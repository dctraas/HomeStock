import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";

admin.initializeApp();
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

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
  const membersSnapshot = await admin.firestore().collection("households").doc(householdId).collection("members").get();

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

interface SpoonacularInfoResult {
  id: number;
  title: string;
  image?: string;
  cuisines?: string[];
  dishTypes?: string[];
  instructions?: string;
  extendedIngredients?: SpoonacularIngredient[];
  readyInMinutes?: number;
}

interface SpoonacularComplexSearchResponse {
  results: SpoonacularInfoResult[];
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

function toRecipeDetail(result: SpoonacularInfoResult) {
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
  };
}

async function spoonacularGet<T>(path: string, params: Record<string, string>, apiKey: string): Promise<T> {
  const url = new URL(`${SPOONACULAR_BASE}${path}`);
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
  url.searchParams.set("apiKey", apiKey);

  const response = await fetch(url.toString());
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    logger.error("Spoonacular API returned an error", { status: response.status, body, path });
    throw new HttpsError("unavailable", "recipe_search_failed");
  }
  return (await response.json()) as T;
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
  { secrets: [spoonacularApiKey], cors: false, timeoutSeconds: 20, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<SearchRecipesRequest> | undefined;
    const householdId = data?.householdId;
    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    await requirePremiumHousehold(uid, householdId);

    const number = Math.min(Math.max(data?.number ?? 20, 1), 30);
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

    const params: Record<string, string> = {
      number: String(number),
      addRecipeInformation: "true",
      fillIngredients: "true",
      sort: "popularity",
    };
    if (data?.mode === "query" && data.query) params.query = data.query;
    if (data?.cuisine) params.cuisine = data.cuisine;
    if (data?.intolerances && data.intolerances.length > 0) params.intolerances = data.intolerances.join(",");

    const response = await spoonacularGet<SpoonacularComplexSearchResponse>("/recipes/complexSearch", params, apiKey);
    return { details: response.results.map(toRecipeDetail) };
  },
);

interface GetRecipeInformationRequest {
  householdId: string;
  id: string;
}

/** Fetches one recipe's full detail by id — used when opening a recipe that wasn't already returned with full detail (see [searchRecipes]'s "ingredients" mode). */
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

    const result = await spoonacularGet<SpoonacularInfoResult>(
      `/recipes/${encodeURIComponent(id)}/information`,
      {},
      spoonacularApiKey.value(),
    );
    return { detail: toRecipeDetail(result) };
  },
);
