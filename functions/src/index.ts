import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";

admin.initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

const db = admin.firestore();

// Stored via `firebase functions:secrets:set ANTHROPIC_API_KEY` — never committed, never
// visible client-side. See functions/README.md for the full deploy walkthrough.
const anthropicApiKey = defineSecret("ANTHROPIC_API_KEY");

// Stored via `firebase functions:secrets:set SPOONACULAR_API_KEY` — powers the recipe
// functions below (searchRecipes/getRecipeInformation). See functions/README.md.
const spoonacularApiKey = defineSecret("SPOONACULAR_API_KEY");

const ANTHROPIC_MODEL = "claude-haiku-4-5";
const ANTHROPIC_VERSION = "2023-06-01";
const SPOONACULAR_BASE = "https://api.spoonacular.com";

/** Mirrors data/model/Category.kt's storageKey values — kept in sync manually. */
const CATEGORY_KEYS = [
  "zuivel",
  "groente_fruit",
  "vlees_vis",
  "brood_bakkerij",
  "voorraadkast",
  "diepvries",
  "dranken",
  "snoep_snacks",
  "huishouden",
  "verzorging",
  "overig",
] as const;

const ALLOWED_MIME_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

// Generous but bounded — a phone photo re-encoded as JPEG at reasonable quality lands well
// under this; it exists to cap Anthropic spend per call, not to accommodate legitimate large
// uploads.
const MAX_BASE64_LENGTH = 8_000_000; // ~6 MB decoded

/**
 * Every callable below acts on behalf of one household and is premium-only. This re-derives
 * both from Firestore rather than trusting the client, so a modified APK can't call any of
 * them for free — mirrors HouseholdMembersRepository.observeHouseholdIsPremium() on the
 * client. The membership-doc check first (this uid must itself be a member) also prevents an
 * arbitrary caller from probing an unrelated household's premium status by guessing its id.
 */
async function requirePremiumHousehold(uid: string, householdId: string): Promise<void> {
  const membersSnapshot = await db.collection("households").doc(householdId).collection("members").get();

  const isMember = membersSnapshot.docs.some((doc) => doc.id === uid);
  if (!isMember) {
    throw new HttpsError("permission-denied", "not_a_household_member");
  }

  const isPremium = membersSnapshot.docs.some((doc) => doc.get("isPremium") === true);
  if (!isPremium) {
    throw new HttpsError("permission-denied", "premium_required");
  }
}

function requireUid(auth: { uid: string } | undefined): string {
  const uid = auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign-in required.");
  return uid;
}

// ---------------------------------------------------------------------------
// Shared cross-household caches. Spoonacular recipe content and its translations are
// identical for every household that looks at the same recipe/locale — caching them
// per-household (like the rest of this app's data) would mean every household pays the
// same Spoonacular-point/Claude-token cost for content someone else already fetched. These
// collections live at the top level (not under households/{id}) and are never written by
// the client directly — see firestore.rules — so a household's own Firestore usage never
// grows from this, it's purely a shared, server-managed cache. Read/write failures here are
// swallowed rather than thrown: a cache hiccup should degrade to "fetch it live", never
// break the feature it's speeding up.
// ---------------------------------------------------------------------------

async function getFreshCache<T>(collection: string, docId: string, ttlMs: number): Promise<T | null> {
  try {
    const snapshot = await db.collection(collection).doc(docId).get();
    if (!snapshot.exists) return null;
    const cachedAt = snapshot.get("cachedAt") as number | undefined;
    if (typeof cachedAt !== "number" || Date.now() - cachedAt > ttlMs) return null;
    return (snapshot.get("data") as T | undefined) ?? null;
  } catch (error) {
    logger.error("cache read failed, falling back to a live fetch", { collection, docId, error });
    return null;
  }
}

async function setCache(collection: string, docId: string, data: unknown): Promise<void> {
  try {
    await db.collection(collection).doc(docId).set({ data, cachedAt: Date.now() });
  } catch (error) {
    logger.error("cache write failed (non-fatal)", { collection, docId, error });
  }
}

// ---------------------------------------------------------------------------
// recognizeProduct — photo of a single product -> up to 3 name/category guesses.
// ---------------------------------------------------------------------------

const PRODUCT_RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    candidates: {
      type: "array",
      items: {
        type: "object",
        properties: {
          name: { type: "string" },
          category: { type: "string", enum: [...CATEGORY_KEYS] },
          confidence: { type: "integer" },
        },
        required: ["name", "category", "confidence"],
        additionalProperties: false,
      },
    },
  },
  required: ["candidates"],
  additionalProperties: false,
} as const;

interface RecognizeProductRequest {
  imageBase64: string;
  mimeType: string;
  householdId: string;
  /** BCP-47 language tag, e.g. "nl", "en" — best-effort hint for the product name's language. */
  locale?: string;
}

interface RecognizeCandidate {
  name: string;
  category: (typeof CATEGORY_KEYS)[number];
  confidence: number;
}

function buildProductPrompt(locale: string): string {
  return (
    "You are helping a home grocery inventory app identify a product from a photo a user " +
    "just took. Look at the image and identify the grocery or household product shown.\n\n" +
    "Return up to 3 candidates, most likely first. For each candidate:\n" +
    "- name: the specific product name. If any brand name, product name, or text is legible " +
    `on the packaging, use that (in its original language). If nothing is legible, write a ` +
    `short generic description of the product in this language: ${locale}.\n` +
    "- category: exactly one of the provided category keys — pick the single best fit.\n" +
    "- confidence: your confidence in this candidate, 0-100.\n\n" +
    "If the image does not show a recognizable grocery or household product at all, return an " +
    "empty candidates array. Do not invent a specific brand or product name you cannot " +
    "actually see evidence for in the image — prefer a generic description over a guess."
  );
}

// ---------------------------------------------------------------------------
// recognizeReceipt — photo of a whole receipt -> a list of purchased line items.
// ---------------------------------------------------------------------------

const RECEIPT_RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    items: {
      type: "array",
      items: {
        type: "object",
        properties: {
          name: { type: "string" },
          category: { type: "string", enum: [...CATEGORY_KEYS] },
          quantity: { type: "integer" },
        },
        required: ["name", "category", "quantity"],
        additionalProperties: false,
      },
    },
  },
  required: ["items"],
  additionalProperties: false,
} as const;

interface RecognizeReceiptRequest {
  imageBase64: string;
  mimeType: string;
  householdId: string;
  locale?: string;
}

interface ReceiptLineItem {
  name: string;
  category: (typeof CATEGORY_KEYS)[number];
  quantity: number;
}

function buildReceiptPrompt(locale: string): string {
  return (
    "You are reading a photo of a supermarket/grocery receipt for a home inventory app. " +
    "Extract every purchased product line — ignore store name/address, payment/card " +
    "terminal lines, subtotal/total/VAT/tax breakdown rows, loyalty program text, and any " +
    "other boilerplate that isn't an actual product.\n\n" +
    "For each real product line, return:\n" +
    `- name: the product name, cleaned up into a normal, readable form in this language: ${locale} ` +
    "(receipts often abbreviate — expand obvious abbreviations, fix obvious OCR typos, but " +
    "don't invent a brand you can't actually read).\n" +
    "- category: exactly one of the provided category keys — pick the single best fit.\n" +
    "- quantity: how many units of this product were purchased. Weighed items (e.g. loose " +
    "produce priced per kg) should get quantity 1 unless the receipt clearly shows a whole-" +
    "unit count. Default to 1 when the receipt doesn't make quantity clear.\n\n" +
    "Two-line weighed items (a product name row followed by a separate '1,23 €/kg ...' unit-" +
    "price row) are one product, not two — merge them into a single entry. If the photo " +
    "doesn't show a legible receipt at all, return an empty items array."
  );
}

// ---------------------------------------------------------------------------
// generateRecipe — household ingredients (+ optional wish) -> one AI-authored recipe.
// ---------------------------------------------------------------------------

const RECIPE_RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    title: { type: "string" },
    cuisine: { type: "string" },
    estimatedMinutes: { type: "integer" },
    ingredients: {
      type: "array",
      items: {
        type: "object",
        properties: {
          name: { type: "string" },
          amount: { type: "string" },
        },
        required: ["name", "amount"],
        additionalProperties: false,
      },
    },
    instructions: {
      type: "array",
      items: { type: "string" },
    },
  },
  required: ["title", "cuisine", "estimatedMinutes", "ingredients", "instructions"],
  additionalProperties: false,
} as const;

interface GenerateRecipeRequest {
  householdId: string;
  /** Ingredient names currently in the household's inventory — as free text, any language. */
  availableIngredients: string[];
  /** Optional free-text wish, e.g. "iets met kip en rijst" or "Italiaans, vegetarisch". */
  wish?: string;
  locale?: string;
}

interface GeneratedRecipe {
  title: string;
  cuisine: string;
  estimatedMinutes: number;
  ingredients: Array<{ name: string; amount: string }>;
  instructions: string[];
}

function buildRecipeGenerationPrompt(availableIngredients: string[], wish: string | undefined, locale: string): string {
  const ingredientList = availableIngredients.length > 0
    ? availableIngredients.slice(0, 40).join(", ")
    : "(none listed — suggest something reasonable with common pantry staples)";
  return (
    "You are a home cooking assistant. Invent one realistic, cookable recipe, written in " +
    `this language: ${locale}.\n\n` +
    `Ingredients currently available at home: ${ingredientList}\n` +
    (wish && wish.trim().length > 0 ? `The cook's request: ${wish.trim()}\n` : "") +
    "\nPrefer using what's already available where reasonable, but it's fine to also call " +
    "for a handful of common ingredients that aren't listed — mark those clearly by amount " +
    "as you normally would, there's no need to flag which ones are missing. Keep it to a " +
    "realistic home-cookable dish (not a restaurant tasting menu), with clear step-by-step " +
    "instructions as a numbered list of short, actionable steps.\n\n" +
    "- title: a short, appetizing dish name.\n" +
    "- cuisine: a short cuisine/style label, e.g. \"Italiaans\", \"Aziatisch\", \"Nederlands\".\n" +
    "- estimatedMinutes: realistic total time including prep.\n" +
    "- ingredients: name + amount (e.g. \"300 g\", \"2 stuks\", \"snufje\") per line.\n" +
    "- instructions: one string per step, in order, no numbering prefix (the app adds that)."
  );
}

// ---------------------------------------------------------------------------
// Shared Anthropic Messages API call (structured JSON output).
// ---------------------------------------------------------------------------

interface AnthropicTextBlock {
  type: "text";
  text: string;
}

interface AnthropicMessageResponse {
  content: Array<AnthropicTextBlock | { type: string }>;
}

async function callAnthropicWithImage(
  apiKey: string,
  imageBase64: string,
  mimeType: string,
  prompt: string,
  schema: object,
): Promise<string> {
  return callAnthropic(apiKey, schema, [
    { type: "image", source: { type: "base64", media_type: mimeType, data: imageBase64 } },
    { type: "text", text: prompt },
  ]);
}

async function callAnthropicTextOnly(apiKey: string, prompt: string, schema: object): Promise<string> {
  return callAnthropic(apiKey, schema, [{ type: "text", text: prompt }]);
}

async function callAnthropic(apiKey: string, schema: object, content: unknown[]): Promise<string> {
  const response = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": apiKey,
      "anthropic-version": ANTHROPIC_VERSION,
    },
    body: JSON.stringify({
      model: ANTHROPIC_MODEL,
      max_tokens: 1536,
      messages: [{ role: "user", content }],
      output_config: { format: { type: "json_schema", schema } },
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    logger.error("Anthropic API returned an error", { status: response.status, body });
    throw new HttpsError("unavailable", "recognition_failed");
  }

  const parsed = (await response.json()) as AnthropicMessageResponse;
  const textBlock = parsed.content.find((block): block is AnthropicTextBlock => block.type === "text");
  if (!textBlock) {
    throw new HttpsError("internal", "empty_model_response");
  }
  return textBlock.text;
}

/**
 * Callable Cloud Function backing the in-app AI product recognition camera (premium-only —
 * see AiRecognitionRepository.kt on the client). Takes a single photo, asks Claude Haiku 4.5
 * to identify the product, and returns up to 3 name/category candidates for the user to pick
 * from and edit.
 */
export const recognizeProduct = onCall(
  // `invoker: "public"` allows the Cloud Run service underneath this function to be reached
  // at all — new GCP projects default to blocking unauthenticated (from IAM's point of view)
  // invocations since late 2024. This is safe here: it only affects the network-layer IAM
  // check, not application access — the actual sign-in + premium check below still runs on
  // every call via Firebase's own callable-function auth (`request.auth`), which is separate
  // from and unaffected by this setting. Every other callable in this file needs the same
  // setting for the same reason.
  { secrets: [anthropicApiKey], cors: false, timeoutSeconds: 30, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<RecognizeProductRequest> | undefined;
    const householdId = data?.householdId;
    const imageBase64 = data?.imageBase64;
    const mimeType = data?.mimeType;
    const locale = typeof data?.locale === "string" && data.locale.trim().length > 0 ? data.locale : "nl";

    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    if (!imageBase64 || typeof imageBase64 !== "string") {
      throw new HttpsError("invalid-argument", "imageBase64 is required.");
    }
    if (imageBase64.length > MAX_BASE64_LENGTH) {
      throw new HttpsError("invalid-argument", "Image is too large.");
    }
    if (!mimeType || !ALLOWED_MIME_TYPES.has(mimeType)) {
      throw new HttpsError("invalid-argument", "Unsupported or missing mimeType.");
    }

    await requirePremiumHousehold(uid, householdId);

    const responseText = await callAnthropicWithImage(
      anthropicApiKey.value(),
      imageBase64,
      mimeType,
      buildProductPrompt(locale),
      PRODUCT_RESPONSE_SCHEMA,
    );

    let parsed: { candidates: RecognizeCandidate[] };
    try {
      parsed = JSON.parse(responseText) as { candidates: RecognizeCandidate[] };
    } catch (error) {
      logger.error("recognizeProduct: could not parse model output as JSON", { responseText, error });
      throw new HttpsError("internal", "invalid_model_response");
    }

    const candidates = Array.isArray(parsed.candidates) ? parsed.candidates.slice(0, 3) : [];
    return { candidates };
  },
);

/**
 * Callable Cloud Function backing the Bonnetje-scanner camera (premium-only — see
 * ReceiptRecognitionRepository.kt on the client). Takes a single photo of a whole receipt and
 * asks Claude Haiku 4.5 to extract the purchased product lines directly — this replaced an
 * earlier on-device ML Kit OCR + hand-written parser approach, which was fragile against the
 * wide variety of real-world receipt layouts (see git history of data/receipt/ for that
 * version). Sending the photo straight to Claude is far more robust to crumpled receipts,
 * unusual layouts, and two-line weighed-item entries, at the cost of a real network round trip
 * and a small per-scan cost.
 */
export const recognizeReceipt = onCall(
  { secrets: [anthropicApiKey], cors: false, timeoutSeconds: 45, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<RecognizeReceiptRequest> | undefined;
    const householdId = data?.householdId;
    const imageBase64 = data?.imageBase64;
    const mimeType = data?.mimeType;
    const locale = typeof data?.locale === "string" && data.locale.trim().length > 0 ? data.locale : "nl";

    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    if (!imageBase64 || typeof imageBase64 !== "string") {
      throw new HttpsError("invalid-argument", "imageBase64 is required.");
    }
    if (imageBase64.length > MAX_BASE64_LENGTH) {
      throw new HttpsError("invalid-argument", "Image is too large.");
    }
    if (!mimeType || !ALLOWED_MIME_TYPES.has(mimeType)) {
      throw new HttpsError("invalid-argument", "Unsupported or missing mimeType.");
    }

    await requirePremiumHousehold(uid, householdId);

    const responseText = await callAnthropicWithImage(
      anthropicApiKey.value(),
      imageBase64,
      mimeType,
      buildReceiptPrompt(locale),
      RECEIPT_RESPONSE_SCHEMA,
    );

    let parsed: { items: ReceiptLineItem[] };
    try {
      parsed = JSON.parse(responseText) as { items: ReceiptLineItem[] };
    } catch (error) {
      logger.error("recognizeReceipt: could not parse model output as JSON", { responseText, error });
      throw new HttpsError("internal", "invalid_model_response");
    }

    const items = Array.isArray(parsed.items) ? parsed.items.slice(0, 60) : [];
    return { items };
  },
);

/**
 * Callable Cloud Function generating one AI-authored recipe from the household's current
 * inventory (premium-only). Complements the Spoonacular-backed search below rather than
 * replacing it — this is for "verras me" / "ik heb zin in iets met X" moments where a fixed
 * database might not have a good match, not a substitute for real, tested recipes.
 */
export const generateRecipe = onCall(
  { secrets: [anthropicApiKey], cors: false, timeoutSeconds: 30, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<GenerateRecipeRequest> | undefined;
    const householdId = data?.householdId;
    const locale = typeof data?.locale === "string" && data.locale.trim().length > 0 ? data.locale : "nl";
    const availableIngredients = Array.isArray(data?.availableIngredients)
      ? data!.availableIngredients.filter((x): x is string => typeof x === "string").slice(0, 60)
      : [];
    const wish = typeof data?.wish === "string" ? data.wish.slice(0, 300) : undefined;

    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }

    await requirePremiumHousehold(uid, householdId);

    const responseText = await callAnthropicTextOnly(
      anthropicApiKey.value(),
      buildRecipeGenerationPrompt(availableIngredients, wish, locale),
      RECIPE_RESPONSE_SCHEMA,
    );

    let recipe: GeneratedRecipe;
    try {
      recipe = JSON.parse(responseText) as GeneratedRecipe;
    } catch (error) {
      logger.error("generateRecipe: could not parse model output as JSON", { responseText, error });
      throw new HttpsError("internal", "invalid_model_response");
    }

    return { recipe };
  },
);

// ---------------------------------------------------------------------------
// searchRecipes / getRecipeInformation — Spoonacular-backed recipe database.
// ---------------------------------------------------------------------------

interface SpoonacularIngredient {
  id: number;
  name: string;
  amount: number;
  unit: string;
}

interface SpoonacularNutrient {
  name: string;
  amount: number;
  unit: string;
}

interface SpoonacularNutrition {
  nutrients?: SpoonacularNutrient[];
}

interface SpoonacularInfoResult {
  id: number;
  title: string;
  image?: string;
  cuisines?: string[];
  dishTypes?: string[];
  instructions?: string;
  extendedIngredients?: SpoonacularIngredient[];
  readyInMinutes?: number;
  nutrition?: SpoonacularNutrition;
}

interface SpoonacularComplexSearchResponse {
  results: SpoonacularInfoResult[];
  totalResults?: number;
}

interface SpoonacularFindByIngredientsResult {
  id: number;
  title: string;
  image?: string;
  usedIngredientCount: number;
  missedIngredientCount: number;
}

/** Strips Spoonacular's (usually HTML) instructions field down to plain, line-broken text. */
function cleanInstructions(html: string | undefined): string | null {
  if (!html) return null;
  const text = html
    .replace(/<li[^>]*>/gi, "\n- ")
    .replace(/<\/?(p|ol|ul|br)[^>]*>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&nbsp;/g, " ")
    .replace(/\n{2,}/g, "\n")
    .trim();
  return text.length > 0 ? text : null;
}

/** Looks up one named nutrient (e.g. "Calories", "Protein") from Spoonacular's per-serving nutrition breakdown, rounded to 1 decimal — null if that recipe has no nutrition data (older cache entries from before this field existed, or Spoonacular simply not having it for that recipe) or doesn't list this particular nutrient. */
function findNutrientAmount(nutrients: SpoonacularNutrient[] | undefined, name: string): number | null {
  const nutrient = nutrients?.find((n) => n.name === name);
  return nutrient ? Math.round(nutrient.amount * 10) / 10 : null;
}

function toRecipeDetail(result: SpoonacularInfoResult) {
  const nutrients = result.nutrition?.nutrients;
  return {
    id: String(result.id),
    name: result.title,
    thumbnailUrl: result.image ?? null,
    category: result.dishTypes?.[0] ?? null,
    area: result.cuisines?.[0] ?? null,
    instructions: cleanInstructions(result.instructions),
    ingredients: (result.extendedIngredients ?? []).slice(0, 20).map((ingredient) => ({
      name: ingredient.name,
      measure: [ingredient.amount, ingredient.unit].filter((part) => part !== undefined && part !== "").join(" "),
    })),
    readyInMinutes: result.readyInMinutes ?? null,
    // Per serving, not per 100g (unlike a product's NutritionInfo) — Spoonacular reports a
    // whole recipe's nutrition divided by its own serving count, there's no per-100g figure.
    calories: findNutrientAmount(nutrients, "Calories"),
    protein: findNutrientAmount(nutrients, "Protein"),
    fat: findNutrientAmount(nutrients, "Fat"),
    carbohydrates: findNutrientAmount(nutrients, "Carbohydrates"),
  };
}

// Spoonacular recipe content is effectively static once published (a title/ingredient list
// doesn't change day to day), so a long TTL is safe — this mainly guards against permanently
// caching a mistake rather than against real staleness.
const RECIPE_DETAIL_CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

// "Popular recipes" ranking (the filterless browse call, by far the most common one — every
// household's Recepten screen hits this on open) can shift day to day, so this stays much
// shorter than the detail cache above. 24h rather than the old 12h now that a cache miss here
// means re-fetching the whole ~900-recipe catalog (see warmBrowseCache), not just one page —
// popularity ranking doesn't move fast enough to justify paying that cost twice a day.
const RECIPE_BROWSE_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

type RecipeDetailPayload = ReturnType<typeof toRecipeDetail>;

/** What gets cached for a "browse" page — [totalResults] (Spoonacular's own count for this exact filter combo) is what lets a *cached* page still answer "is there a next page" without an extra live call. */
interface RecipeSearchCachePayload {
  details: RecipeDetailPayload[];
  totalResults: number;
}

/** Deterministic key for a "browse" mode call's exact param combination — order-independent on intolerances so ["Gluten","Dairy"] and ["Dairy","Gluten"] share a cache entry. Includes [offset] so each page of a paginated browse gets its own cache entry rather than colliding on page 1's. */
function browseCacheKey(cuisine: string | undefined, intolerances: string[] | undefined, number: number, offset: number): string {
  const intolerancesKey = intolerances && intolerances.length > 0 ? [...intolerances].sort().join(",") : "none";
  return `browse_${cuisine ?? "none"}_${intolerancesKey}_${number}_${offset}`;
}

async function spoonacularGet<T>(path: string, params: Record<string, string>, apiKey: string): Promise<T> {
  const url = new URL(`${SPOONACULAR_BASE}${path}`);
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
  url.searchParams.set("apiKey", apiKey);

  const response = await fetch(url.toString());
  const bodyText = await response.text();

  // 402 = daily point quota used up, 429 = too many requests per minute — both mean "try again
  // later", not "something's broken". Spoonacular doesn't consistently signal this as an HTTP
  // error status either: some responses carry it as a 402/429 status, others come back as a
  // plain 200 OK with a {"status":"failure","code":402,...} JSON body instead — checking the
  // body's own code alongside the HTTP status catches both, so this can't silently fall through
  // as a generic failure just because the transport-level status looked fine.
  let parsedBody: { status?: string; code?: number } | undefined;
  try {
    parsedBody = bodyText ? (JSON.parse(bodyText) as { status?: string; code?: number }) : undefined;
  } catch {
    parsedBody = undefined;
  }
  const quotaCodes = [402, 429];
  const isQuotaExceeded =
    quotaCodes.includes(response.status) ||
    (parsedBody?.status === "failure" && parsedBody.code !== undefined && quotaCodes.includes(parsedBody.code));

  if (isQuotaExceeded) {
    logger.warn("Spoonacular quota/rate limit hit", { status: response.status, body: bodyText, path });
    // Surfacing a distinct code lets the client show a message that actually says that,
    // instead of the generic "no connection" one it'd otherwise fall back to for every kind
    // of failure here.
    throw new HttpsError("resource-exhausted", "recipe_quota_exceeded");
  }
  if (!response.ok) {
    logger.error("Spoonacular API returned an error", { status: response.status, body: bodyText, path });
    throw new HttpsError("unavailable", "recipe_search_failed");
  }
  return JSON.parse(bodyText) as T;
}

// Spoonacular hard-caps offset at 900 for any single filter/query combination — the whole
// addressable "browse, popularity-sorted" catalog through this API is nine 100-item pages
// (offsets 0, 100, ..., 800). 100 is also Spoonacular's own per-request max for `number`, and
// divides evenly into every page size the app itself requests (currently 20), so a client page
// never straddles a chunk boundary — one chunk always fully covers one app-side page.
const RECIPE_BROWSE_CHUNK_SIZE = 100;
const RECIPE_BROWSE_CHUNK_OFFSETS = [0, 100, 200, 300, 400, 500, 600, 700, 800];

/** Fetches and caches one 100-recipe "browse" chunk (no query, popularity-sorted) for a given
 *  intolerances combo — a cache hit if [RECIPE_BROWSE_CACHE_TTL_MS] hasn't elapsed, a live
 *  Spoonacular call otherwise. Decoupled from whatever page size a client actually requested —
 *  see [RECIPE_BROWSE_CHUNK_SIZE]'s doc — so this is the one place chunk fetching happens,
 *  shared by both [warmBrowseCache]'s eager pre-fetch and a plain cache-miss fallback. */
async function fetchAndCacheBrowseChunk(
  intolerances: string[] | undefined,
  chunkOffset: number,
  apiKey: string,
): Promise<RecipeSearchCachePayload> {
  const cacheKey = browseCacheKey(undefined, intolerances, RECIPE_BROWSE_CHUNK_SIZE, chunkOffset);
  const cached = await getFreshCache<RecipeSearchCachePayload>("recipeSearchCache", cacheKey, RECIPE_BROWSE_CACHE_TTL_MS);
  if (cached) return cached;

  const params: Record<string, string> = {
    number: String(RECIPE_BROWSE_CHUNK_SIZE),
    offset: String(chunkOffset),
    addRecipeInformation: "true",
    addRecipeNutrition: "true",
    fillIngredients: "true",
    sort: "popularity",
  };
  if (intolerances && intolerances.length > 0) params.intolerances = intolerances.join(",");

  const response = await spoonacularGet<SpoonacularComplexSearchResponse>("/recipes/complexSearch", params, apiKey);
  const details = response.results.map(toRecipeDetail);
  const totalResults = response.totalResults ?? chunkOffset + details.length;
  const payload: RecipeSearchCachePayload = { details, totalResults };

  // Awaited (not fire-and-forget) so these are guaranteed to land before the function's
  // container can be frozen post-response, but run in parallel so this isn't slowed down by
  // writing each recipe's detail cache entry one at a time.
  await Promise.all([
    setCache("recipeSearchCache", cacheKey, payload),
    ...details.map((detail) => setCache("recipeDetailCache", detail.id, detail)),
  ]);
  return payload;
}

/**
 * Eagerly fetches every "browse" chunk that isn't already fresh in cache, sequentially — not
 * in parallel, to stay gentle on Spoonacular's rate limit; this genuinely only runs once per
 * [RECIPE_BROWSE_CACHE_TTL_MS] window per intolerances combo (see [searchRecipes]'s "browse"
 * branch, the only caller), not on every request. The by-far-common trigger is a household's
 * first "Recepten" open since the last TTL expiry asking for offset 0, which is also the
 * slowest single request this function ever serves (up to nine sequential Spoonacular calls)
 * — the trade-off the household is asking for in exchange for every further page in this
 * window coming back instantly from cache instead.
 */
async function warmBrowseCache(intolerances: string[] | undefined, apiKey: string): Promise<void> {
  for (const chunkOffset of RECIPE_BROWSE_CHUNK_OFFSETS) {
    await fetchAndCacheBrowseChunk(intolerances, chunkOffset, apiKey);
  }
}

interface SearchRecipesRequest {
  householdId: string;
  /** "browse" (no filters, popular first), "query" (free-text name search), or "ingredients" (what-can-I-cook). */
  mode: "browse" | "query" | "ingredients";
  query?: string;
  /** CSV of English ingredient terms — only used for mode "ingredients". */
  ingredients?: string;
  /** Spoonacular cuisine name, e.g. "Italian" — only used for modes "browse"/"query". */
  cuisine?: string;
  /** Spoonacular intolerance names, e.g. ["Gluten", "Dairy"] — only used for modes "browse"/"query". */
  intolerances?: string[];
  number?: number;
  /** How many results to skip, for paginating "browse"/"query" — Spoonacular caps this at 900 regardless of filters, so that's the deepest any single query can page. */
  offset?: number;
}

/**
 * Recipe search/browse, proxied through Spoonacular (premium-only, same reasoning as the
 * other functions in this file — the API key stays server-side). "browse" and "query" ask
 * Spoonacular for full recipe detail in the same request (ingredients + instructions), so
 * opening one of these results doesn't need a second call; "ingredients" mode (the
 * maaltijdplanner's "wat kan ik koken" picker) only gets summaries with a used/missed
 * ingredient count — [getRecipeInformation] fetches the rest on demand if one gets opened.
 */
export const searchRecipes = onCall(
  // 60s (not the usual 20) — a cache-cold plain browse request below can chain up to nine
  // sequential Spoonacular calls (see warmBrowseCache) before it can respond at all.
  { secrets: [spoonacularApiKey], cors: false, timeoutSeconds: 60, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<SearchRecipesRequest> | undefined;
    const householdId = data?.householdId;
    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    await requirePremiumHousehold(uid, householdId);

    const number = Math.min(Math.max(data?.number ?? 20, 1), 30);
    // Spoonacular hard-caps offset at 900 regardless of filters — this is the deepest any single
    // query/filter combination can ever page (see functions/README.md's Caching section and the
    // conversation that led here: a true "fetch the whole ~365k-recipe catalog" isn't possible
    // through this API at all, offset-paginating up to this cap is the realistic ceiling).
    const offset = Math.min(Math.max(data?.offset ?? 0, 0), 900);
    const apiKey = spoonacularApiKey.value();

    if (data?.mode === "ingredients") {
      const ingredients = data.ingredients?.trim();
      if (!ingredients) return { summaries: [] };
      const results = await spoonacularGet<SpoonacularFindByIngredientsResult[]>(
        "/recipes/findByIngredients",
        { ingredients, ranking: "1", number: String(number) },
        apiKey,
      );
      return {
        summaries: results.map((r) => ({
          id: String(r.id),
          name: r.title,
          thumbnailUrl: r.image ?? null,
          usedIngredientCount: r.usedIngredientCount,
        })),
      };
    }

    // The plain browse (no cuisine boost, no query) is by far the most common call — every
    // household's Recepten screen hits this on open — and popularity ranking barely moves
    // page to page, so instead of caching/fetching exactly one page at a time, this eagerly
    // fetches and caches the whole ~900-recipe catalog the first time any of it is missing
    // (see warmBrowseCache), then serves every further "load more" out of cache for the rest
    // of RECIPE_BROWSE_CACHE_TTL_MS. The much smaller cuisine-boosted call (only 8 recipes, only
    // page 1) falls through to the old per-page cache-or-fetch logic below instead, same as
    // "query" — neither is worth pre-warming a whole catalog for.
    if (data?.mode === "browse" && !data.cuisine) {
      const chunkOffset = Math.min(Math.floor(offset / RECIPE_BROWSE_CHUNK_SIZE) * RECIPE_BROWSE_CHUNK_SIZE, 800);
      const chunkCacheKey = browseCacheKey(undefined, data?.intolerances, RECIPE_BROWSE_CHUNK_SIZE, chunkOffset);
      let chunk = await getFreshCache<RecipeSearchCachePayload>("recipeSearchCache", chunkCacheKey, RECIPE_BROWSE_CACHE_TTL_MS);
      if (!chunk) {
        await warmBrowseCache(data?.intolerances, apiKey);
        chunk = await fetchAndCacheBrowseChunk(data?.intolerances, chunkOffset, apiKey);
      }
      const startInChunk = offset - chunkOffset;
      const details = chunk.details.slice(startInChunk, startInChunk + number);
      const hasMore = offset + details.length < chunk.totalResults;
      return { details, hasMore };
    }

    // Only the cuisine-boosted "browse" call is cached as a whole page here — "query"/
    // "ingredients" results are shaped by what this particular household typed or has in
    // stock, too personalized to expect much reuse from caching the result set itself.
    // Individual recipes surfaced by ANY mode still get backfilled into recipeDetailCache below,
    // since a given recipe's own content is shared regardless of how it was found.
    const cacheKey = data?.mode === "browse" ? browseCacheKey(data?.cuisine, data?.intolerances, number, offset) : null;
    if (cacheKey) {
      const cached = await getFreshCache<RecipeSearchCachePayload>("recipeSearchCache", cacheKey, RECIPE_BROWSE_CACHE_TTL_MS);
      if (cached) return { details: cached.details, hasMore: offset + cached.details.length < cached.totalResults };
    }

    const params: Record<string, string> = {
      number: String(number),
      offset: String(offset),
      addRecipeInformation: "true",
      addRecipeNutrition: "true",
      fillIngredients: "true",
      sort: "popularity",
    };
    if (data?.mode === "query" && data.query) params.query = data.query;
    if (data?.cuisine) params.cuisine = data.cuisine;
    if (data?.intolerances && data.intolerances.length > 0) params.intolerances = data.intolerances.join(",");

    const response = await spoonacularGet<SpoonacularComplexSearchResponse>("/recipes/complexSearch", params, apiKey);
    const details = response.results.map(toRecipeDetail);
    const totalResults = response.totalResults ?? offset + details.length;
    const hasMore = offset + details.length < totalResults;

    // Awaited (not fire-and-forget) so these are guaranteed to land before the function's
    // container can be frozen post-response, but run in parallel so a cache-miss response isn't
    // slowed down by writing each entry one at a time.
    await Promise.all([
      ...(cacheKey ? [setCache("recipeSearchCache", cacheKey, { details, totalResults })] : []),
      ...details.map((detail) => setCache("recipeDetailCache", detail.id, detail)),
    ]);

    return { details, hasMore };
  },
);

interface GetRecipeInformationRequest {
  householdId: string;
  id: string;
}

/**
 * Fetches one recipe's full detail by id — used when opening a recipe that wasn't already
 * returned with full detail (see [searchRecipes]'s "ingredients" mode). Always returns
 * Spoonacular's original English content: the client keeps this untouched for ingredient
 * matching against the household's inventory, and calls [translateRecipe] separately (into
 * parallel fields) when the app's language isn't English. Don't merge translation in here —
 * overwriting `detail.ingredients` in place would break that matching.
 */
export const getRecipeInformation = onCall(
  { secrets: [spoonacularApiKey], cors: false, timeoutSeconds: 20, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<GetRecipeInformationRequest> | undefined;
    const householdId = data?.householdId;
    const id = data?.id;
    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    if (!id || typeof id !== "string") {
      throw new HttpsError("invalid-argument", "id is required.");
    }
    await requirePremiumHousehold(uid, householdId);

    const cached = await getFreshCache<RecipeDetailPayload>("recipeDetailCache", id, RECIPE_DETAIL_CACHE_TTL_MS);
    if (cached) return { detail: cached };

    const result = await spoonacularGet<SpoonacularInfoResult>(
      `/recipes/${encodeURIComponent(id)}/information`,
      // Same nutrition data as complexSearch's addRecipeNutrition, just a differently-named
      // param on this endpoint — Spoonacular isn't consistent about that between the two.
      { includeNutrition: "true" },
      spoonacularApiKey.value(),
    );
    const detail = toRecipeDetail(result);
    await setCache("recipeDetailCache", id, detail);
    return { detail };
  },
);

// ---------------------------------------------------------------------------
// translateRecipe — AI translation for the recipe list/detail screens when the
// household's app language isn't English (Spoonacular's own content always is).
// ---------------------------------------------------------------------------

// English is the only locale Spoonacular itself speaks; every other app language routes
// through here. Keeping the language name in English in the prompt (rather than the locale's
// own endonym) reads more reliably for the model regardless of target language.
const LOCALE_LANGUAGE_NAMES: Record<string, string> = {
  nl: "Dutch",
  de: "German",
  es: "Spanish",
  fr: "French",
};

function languageNameForLocale(locale: string): string {
  return LOCALE_LANGUAGE_NAMES[locale] ?? locale;
}

interface TranslatableIngredient {
  name: string;
  measure: string;
}

interface TranslatableDetailFields {
  name: string;
  category: string | null;
  area: string | null;
  instructions: string | null;
  ingredients: TranslatableIngredient[];
}

/** Raw shape Claude actually returns for [TRANSLATE_DETAIL_SCHEMA] — empty string, not null, for the optional fields (see the schema's own comment). */
interface RawTranslatedDetailFields {
  name: string;
  category: string;
  area: string;
  instructions: string;
  ingredients: TranslatableIngredient[];
}

const TRANSLATE_DETAIL_SCHEMA = {
  type: "object",
  properties: {
    name: { type: "string" },
    // Empty string rather than null for the optional fields — keeps the schema flat (no
    // nullable-type unions to worry about across json_schema implementations), converted back
    // to null on the way out (see the two call sites below).
    category: { type: "string" },
    area: { type: "string" },
    instructions: { type: "string" },
    ingredients: {
      type: "array",
      items: {
        type: "object",
        properties: { name: { type: "string" }, measure: { type: "string" } },
        required: ["name", "measure"],
        additionalProperties: false,
      },
    },
  },
  required: ["name", "category", "area", "instructions", "ingredients"],
  additionalProperties: false,
} as const;

async function translateDetailFields(
  apiKey: string,
  locale: string,
  fields: TranslatableDetailFields,
): Promise<TranslatableDetailFields> {
  const language = languageNameForLocale(locale);
  const input = {
    name: fields.name,
    category: fields.category ?? "",
    area: fields.area ?? "",
    instructions: fields.instructions ?? "",
    ingredients: fields.ingredients,
  };
  const prompt =
    `Translate this recipe into ${language}, the way a native speaker would naturally write it — not a ` +
    "stiff word-for-word translation. Keep every ingredient's numeric amount and unit exactly as given, " +
    "only translate the words. Keep the instructions as clear, natural step-by-step cooking instructions. " +
    "Leave category/area/instructions as an empty string if the input for that field is already empty.\n\n" +
    `Recipe (JSON): ${JSON.stringify(input)}`;
  const responseText = await callAnthropicTextOnly(apiKey, prompt, TRANSLATE_DETAIL_SCHEMA);
  const parsed = JSON.parse(responseText) as RawTranslatedDetailFields;
  return {
    name: parsed.name || fields.name,
    category: parsed.category || null,
    area: parsed.area || null,
    instructions: parsed.instructions || null,
    ingredients: parsed.ingredients?.length ? parsed.ingredients : fields.ingredients,
  };
}

const TRANSLATE_TITLES_SCHEMA = {
  type: "object",
  properties: {
    items: {
      type: "array",
      items: {
        type: "object",
        properties: { id: { type: "string" }, name: { type: "string" } },
        required: ["id", "name"],
        additionalProperties: false,
      },
    },
  },
  required: ["items"],
  additionalProperties: false,
} as const;

async function translateTitles(
  apiKey: string,
  locale: string,
  items: Array<{ id: string; name: string }>,
): Promise<Array<{ id: string; name: string }>> {
  const language = languageNameForLocale(locale);
  const prompt =
    `Translate these recipe titles into ${language}, the way a native speaker would naturally title each ` +
    "dish — not a literal word-for-word translation. Return every item with the same id it came in with, " +
    `so the caller can match translated names back to the right recipe.\n\nTitles (JSON): ${JSON.stringify(items)}`;
  const responseText = await callAnthropicTextOnly(apiKey, prompt, TRANSLATE_TITLES_SCHEMA);
  const parsed = JSON.parse(responseText) as { items: Array<{ id: string; name: string }> };
  return Array.isArray(parsed.items) ? parsed.items : [];
}

interface TranslateRecipeRequest {
  householdId: string;
  locale: string;
  mode: "titles" | "detail";
  items?: Array<{ id: string; name: string }>;
  /** Spoonacular recipe id for mode "detail" — enables [getCachedTranslation]/[setCachedTranslation] below. Omitted (or an "ai-"/"custom-"-prefixed id) for AI-generated/hand-entered recipes, which are never cached here — see [isCacheableRecipeId]. */
  id?: string;
  name?: string;
  category?: string | null;
  area?: string | null;
  instructions?: string | null;
  ingredients?: TranslatableIngredient[];
}

// A recipe's translation into a given locale is the same for every household that asks for
// it — cached at the top level (not per household) so the *second* household ever to open
// "Spaghetti Bolognese" in Dutch pays nothing, regardless of which household went first. Long
// TTL: a translation only really goes stale if this function's own prompt/schema changes, not
// because the underlying recipe changed.
const RECIPE_TRANSLATION_CACHE_TTL_MS = 90 * 24 * 60 * 60 * 1000; // 90 days

interface CachedRecipeTranslation {
  name?: string;
  category?: string | null;
  area?: string | null;
  instructions?: string | null;
  ingredients?: TranslatableIngredient[];
  /** Only set once a "detail" translation has actually run — a "titles"-only cache entry (just `name`) must not be mistaken for a full detail translation. */
  hasDetail?: boolean;
  cachedAt?: number;
}

/** AI-generated and hand-entered recipes are private, household-specific content — never worth (or safe) sharing in a cross-household cache, unlike real Spoonacular recipes. */
function isCacheableRecipeId(id: string | null | undefined): id is string {
  return typeof id === "string" && id.length > 0 && !id.startsWith("ai-") && !id.startsWith("custom-");
}

function translationDocId(id: string, locale: string): string {
  return `${id}_${locale}`;
}

async function getCachedTranslation(id: string, locale: string): Promise<CachedRecipeTranslation | null> {
  try {
    const snapshot = await db.collection("recipeTranslations").doc(translationDocId(id, locale)).get();
    if (!snapshot.exists) return null;
    const cachedAt = snapshot.get("cachedAt") as number | undefined;
    if (typeof cachedAt !== "number" || Date.now() - cachedAt > RECIPE_TRANSLATION_CACHE_TTL_MS) return null;
    return snapshot.data() as CachedRecipeTranslation;
  } catch (error) {
    logger.error("recipeTranslations read failed, falling back to a live translation", { id, locale, error });
    return null;
  }
}

/** Merges rather than overwrites — a "titles"-only write must not erase an already-cached detail translation (or vice versa) for the same recipe/locale. */
async function setCachedTranslation(id: string, locale: string, fields: Partial<CachedRecipeTranslation>): Promise<void> {
  try {
    await db.collection("recipeTranslations").doc(translationDocId(id, locale)).set(
      { ...fields, cachedAt: Date.now() },
      { merge: true },
    );
  } catch (error) {
    logger.error("recipeTranslations write failed (non-fatal)", { id, locale, error });
  }
}

/**
 * Translates recipe list titles ("titles" mode) or a full recipe's name/category/area/
 * instructions/ingredients ("detail" mode) into the household's app language. Two cases:
 * - Recipe list rows, which only need a translated title, not the whole recipe — most never
 *   get opened, so translating everything upfront in [searchRecipes] would waste spend on
 *   recipes nobody looks at further.
 * - A recipe's full detail (from [getRecipeInformation] or a search result that already
 *   included it), translated into parallel fields client-side — the original English fields
 *   are left untouched so ingredient matching against the household's inventory keeps working.
 *
 * Both modes check [getCachedTranslation] first for any real Spoonacular recipe id (see
 * [isCacheableRecipeId]) — this is the single biggest lever this app has on Claude spend, since
 * unlike a photo scan or a freeform AI recipe, "translate recipe #12345 into Dutch" produces the
 * exact same output for every household that ever asks.
 */
export const translateRecipe = onCall(
  { secrets: [anthropicApiKey], cors: false, timeoutSeconds: 30, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<TranslateRecipeRequest> | undefined;
    const householdId = data?.householdId;
    const locale = data?.locale;
    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    if (!locale || typeof locale !== "string") {
      throw new HttpsError("invalid-argument", "locale is required.");
    }
    await requirePremiumHousehold(uid, householdId);

    const apiKey = anthropicApiKey.value();

    if (data?.mode === "titles") {
      const items = Array.isArray(data.items) ? data.items.slice(0, 40) : [];
      if (items.length === 0) return { items: [] };

      const cachedNameById = new Map<string, string>();
      await Promise.all(
        items.map(async (item) => {
          if (!isCacheableRecipeId(item.id)) return;
          const cached = await getCachedTranslation(item.id, locale);
          if (cached?.name) cachedNameById.set(item.id, cached.name);
        }),
      );

      const uncached = items.filter((item) => !cachedNameById.has(item.id));
      let freshlyTranslated: Array<{ id: string; name: string }> = [];
      if (uncached.length > 0) {
        freshlyTranslated = await translateTitles(apiKey, locale, uncached);
        await Promise.all(
          freshlyTranslated
            .filter((t) => isCacheableRecipeId(t.id))
            .map((t) => setCachedTranslation(t.id, locale, { name: t.name })),
        );
      }
      const freshNameById = new Map(freshlyTranslated.map((t) => [t.id, t.name]));
      const merged = items.map((item) => ({
        id: item.id,
        name: cachedNameById.get(item.id) ?? freshNameById.get(item.id) ?? item.name,
      }));
      return { items: merged };
    }

    if (!data?.name) {
      throw new HttpsError("invalid-argument", "name is required for mode \"detail\".");
    }

    const cacheId = isCacheableRecipeId(data.id) ? data.id : null;
    if (cacheId) {
      const cached = await getCachedTranslation(cacheId, locale);
      if (cached?.hasDetail) {
        return {
          detail: {
            name: cached.name || data.name,
            category: cached.category ?? null,
            area: cached.area ?? null,
            instructions: cached.instructions ?? null,
            ingredients: cached.ingredients?.length ? cached.ingredients : (data.ingredients ?? []),
          },
        };
      }
    }

    const translated = await translateDetailFields(apiKey, locale, {
      name: data.name,
      category: data.category ?? null,
      area: data.area ?? null,
      instructions: data.instructions ?? null,
      ingredients: Array.isArray(data.ingredients) ? data.ingredients : [],
    });

    if (cacheId) {
      await setCachedTranslation(cacheId, locale, { ...translated, hasDetail: true });
    }

    return { detail: translated };
  },
);
