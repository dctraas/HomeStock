import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { google } from "googleapis";

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

export function requireUid(auth: { uid: string } | undefined): string {
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
          // Empty string when no legible price for this line — same empty-string-instead-of-
          // null convention as TRANSLATE_DETAIL_SCHEMA below (keeps the schema flat).
          price: { type: "string" },
        },
        required: ["name", "category", "quantity", "price"],
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

/** Raw shape Claude actually returns — price is an unparsed string, see [ReceiptLineItem]. */
interface RawReceiptLineItem {
  name: string;
  category: (typeof CATEGORY_KEYS)[number];
  quantity: number;
  price: string;
}

interface ReceiptLineItem {
  name: string;
  category: (typeof CATEGORY_KEYS)[number];
  quantity: number;
  /** Total price paid for this line (not per-unit — the client divides by quantity itself), or null when not legible. */
  price: number | null;
}

/** "3,98", "3.98", "€ 3,98" -> 3.98; anything unparseable -> null. */
export function parseReceiptPrice(raw: string): number | null {
  const normalized = raw.replace(/[^0-9,.-]/g, "").replace(",", ".");
  if (normalized.length === 0) return null;
  const value = Number.parseFloat(normalized);
  return Number.isFinite(value) && value >= 0 ? value : null;
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
    "unit count. Default to 1 when the receipt doesn't make quantity clear.\n" +
    "- price: the total price actually paid for this line, exactly as printed (before any " +
    "per-unit division), as a plain decimal number using a period, e.g. \"3.98\". Empty string " +
    "if no legible price for this specific line.\n\n" +
    "Two-line weighed items (a product name row followed by a separate '1,23 €/kg ...' unit-" +
    "price row) are one product, not two — merge them into a single entry, using the total " +
    "price from that pair. If the photo doesn't show a legible receipt at all, return an " +
    "empty items array."
  );
}

// ---------------------------------------------------------------------------
// recognizeExpirationDate — photo of a product's packaging -> its printed best-before date.
// ---------------------------------------------------------------------------

const EXPIRATION_DATE_RESPONSE_SCHEMA = {
  type: "object",
  properties: {
    // Empty string when no legible date is found — same convention as RECEIPT_RESPONSE_SCHEMA's
    // price field, keeping the schema flat instead of using a nullable type.
    dateIso: { type: "string" },
    confidence: { type: "integer" },
  },
  required: ["dateIso", "confidence"],
  additionalProperties: false,
} as const;

interface RecognizeExpirationDateRequest {
  imageBase64: string;
  mimeType: string;
  householdId: string;
  locale?: string;
}

function buildExpirationDatePrompt(locale: string): string {
  return (
    "You are helping a home grocery inventory app read the expiration/best-before date " +
    "printed or stamped on a product's packaging, from a photo a user just took. Look for a " +
    "date near wording like 'THT', 'houdbaar tot', 'tenminste houdbaar tot', 'best before', " +
    `'use by', 'exp', 'EXP', or the local equivalent in this language: ${locale}.\n\n` +
    "- dateIso: the date in ISO 8601 format (YYYY-MM-DD). If the printed date has no day " +
    "of month (e.g. only a month and year), use the first day of that month. If there are " +
    "multiple dates on the packaging (e.g. a production date and a best-before date), use " +
    "the best-before/expiration date, not the production date. If you cannot find a legible " +
    "expiration date in the photo at all, return an empty string.\n" +
    "- confidence: your confidence that dateIso is correct, 0-100 (0 when dateIso is empty).\n\n" +
    "Do not guess a date you cannot actually see evidence for in the image."
  );
}

/** True for a `YYYY-MM-DD` string that also parses as a real calendar date. */
export function isValidIsoDate(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  return !Number.isNaN(new Date(`${value}T00:00:00Z`).getTime());
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
    servings: { type: "integer" },
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
  required: ["title", "cuisine", "estimatedMinutes", "servings", "ingredients", "instructions"],
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
  servings: number;
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
    "- servings: realistic number of people this feeds (typically 2-6) — ingredient amounts " +
    "must be consistent with this count, since the app lets users scale the recipe up or down " +
    "from it.\n" +
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

    let parsed: { items: RawReceiptLineItem[] };
    try {
      parsed = JSON.parse(responseText) as { items: RawReceiptLineItem[] };
    } catch (error) {
      logger.error("recognizeReceipt: could not parse model output as JSON", { responseText, error });
      throw new HttpsError("internal", "invalid_model_response");
    }

    const items: ReceiptLineItem[] = (Array.isArray(parsed.items) ? parsed.items.slice(0, 60) : []).map(
      (item) => ({ ...item, price: parseReceiptPrice(item.price) }),
    );
    return { items };
  },
);

/**
 * Callable Cloud Function backing the "THT-datum scannen" camera on ProductDetailScreen
 * (premium-only — see AiRecognitionRepository.kt on the client). Takes a single photo of a
 * product's packaging and asks Claude Haiku 4.5 to read its printed best-before/expiration
 * date, returning it as an ISO 8601 date string.
 */
export const recognizeExpirationDate = onCall(
  { secrets: [anthropicApiKey], cors: false, timeoutSeconds: 30, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<RecognizeExpirationDateRequest> | undefined;
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
      buildExpirationDatePrompt(locale),
      EXPIRATION_DATE_RESPONSE_SCHEMA,
    );

    let parsed: { dateIso: string; confidence: number };
    try {
      parsed = JSON.parse(responseText) as { dateIso: string; confidence: number };
    } catch (error) {
      logger.error("recognizeExpirationDate: could not parse model output as JSON", { responseText, error });
      throw new HttpsError("internal", "invalid_model_response");
    }

    const dateIso = isValidIsoDate(parsed.dateIso) ? parsed.dateIso : null;
    return { dateIso, confidence: dateIso ? Math.max(0, Math.min(100, Math.round(parsed.confidence))) : 0 };
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
// importRecipeFromUrl — parses a recipe out of an arbitrary web page a household pastes in.
// Tries schema.org "Recipe" JSON-LD first (most recipe sites already embed this for Google's
// own recipe rich-results, so this is usually free and exact); falls back to asking Claude to
// extract one from the page's own text when that's missing or incomplete. Either way this
// returns the same {recipe} shape as generateRecipe above, so the client reuses the same
// mapGeneratedRecipeToDetail mapper — the result is never saved automatically, it only
// pre-fills the "eigen recept" editor (see CustomRecipeEditScreen) for the household to review
// and fix before keeping it, since a scraped/AI-extracted result can be wrong in ways a
// generated-from-inventory recipe never is (mis-parsed step order, an ingredient the page
// buried in a sidebar, a JS-only page with no server-rendered recipe at all).
// ---------------------------------------------------------------------------

const MAX_IMPORT_HTML_LENGTH = 1_500_000; // ~1.5 MB of markup — generous for a recipe page, bounded against a pathological response.
const IMPORT_FETCH_TIMEOUT_MS = 15_000;
const MAX_IMPORT_PAGE_TEXT_LENGTH = 12_000; // Bounds the Claude-fallback prompt's token cost.

interface ImportRecipeFromUrlRequest {
  householdId: string;
  url: string;
  locale?: string;
}

/**
 * Best-effort SSRF guard: rejects loopback/private/link-local hostnames and the cloud
 * metadata IP by hostname text alone. Not a substitute for network-level egress control (a
 * DNS-rebinding attack could still slip a private address past a text check like this), but it
 * stops the obvious case — someone pasting "http://localhost/..." or
 * "http://169.254.169.254/..." — for a feature that otherwise has this Cloud Function fetch
 * whatever URL a household types in.
 */
export function isDisallowedImportHost(hostname: string): boolean {
  const host = hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".localhost") || host === "::1") return true;
  if (host === "169.254.169.254") return true; // cloud metadata service
  const ipv4 = host.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4) {
    const a = Number(ipv4[1]);
    const b = Number(ipv4[2]);
    if (a === 127) return true; // loopback
    if (a === 10) return true; // 10.0.0.0/8
    if (a === 172 && b >= 16 && b <= 31) return true; // 172.16.0.0/12
    if (a === 192 && b === 168) return true; // 192.168.0.0/16
    if (a === 169 && b === 254) return true; // link-local
    if (a === 0) return true;
  }
  return false;
}

/** Validates and parses a household-supplied recipe URL — http(s) only, no private/loopback host. */
export function parseImportUrl(raw: string): URL {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new HttpsError("invalid-argument", "invalid_url");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new HttpsError("invalid-argument", "invalid_url");
  }
  if (isDisallowedImportHost(url.hostname)) {
    throw new HttpsError("invalid-argument", "invalid_url");
  }
  return url;
}

async function fetchImportPage(url: URL): Promise<string> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), IMPORT_FETCH_TIMEOUT_MS);
  try {
    const response = await fetch(url.toString(), {
      redirect: "follow",
      signal: controller.signal,
      headers: {
        // A real, current desktop-Chrome UA — the previous self-identifying one
        // ("HomeStockRecipeImport/1.0") was outright rejected (or served a near-empty
        // interstitial instead of the real page) by several major recipe sites' anti-bot
        // layers, which is why every import attempt failed the same way regardless of the URL.
        // The rest of this header set mimics what a real browser actually sends alongside that
        // UA — several of the same anti-bot layers check for those too, not just the UA string.
        "user-agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
          "Chrome/131.0.0.0 Safari/537.36",
        accept: "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "accept-language": "nl-NL,nl;q=0.9,en-US;q=0.8,en;q=0.7",
        "upgrade-insecure-requests": "1",
      },
    });
    if (!response.ok) {
      logger.warn("importRecipeFromUrl: fetch not ok", { url: url.toString(), status: response.status });
      throw new HttpsError("unavailable", "import_fetch_failed");
    }
    const text = await response.text();
    logger.info("importRecipeFromUrl: fetched", { url: url.toString(), status: response.status, htmlLength: text.length });
    return text.slice(0, MAX_IMPORT_HTML_LENGTH);
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    logger.warn("importRecipeFromUrl: fetch failed", { url: url.toString(), error });
    throw new HttpsError("unavailable", "import_fetch_failed");
  } finally {
    clearTimeout(timeout);
  }
}

/** Strips a whole HTML page down to plain, line-broken text for the Claude-fallback prompt —
 *  unlike [cleanInstructions] (which only ever sees an already-isolated instructions fragment
 *  from Spoonacular), this also drops <script>/<style> blocks, since a raw page includes both. */
function stripHtml(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<li[^>]*>/gi, "\n- ")
    .replace(/<\/?(p|ol|ul|br|div|h[1-6])[^>]*>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&nbsp;/g, " ")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/[ \t]+/g, " ")
    .replace(/\n{2,}/g, "\n")
    .trim();
}

/** Walks a parsed JSON-LD value looking for a node whose "@type" (a string or string[]) includes
 *  "Recipe" — schema.org allows a lone top-level object, an array of them, or an object with an
 *  "@graph" wrapping several (the common WordPress-recipe-plugin shape). */
function findRecipeNode(value: unknown): Record<string, unknown> | null {
  if (Array.isArray(value)) {
    for (const entry of value) {
      const found = findRecipeNode(entry);
      if (found) return found;
    }
    return null;
  }
  if (value && typeof value === "object") {
    const obj = value as Record<string, unknown>;
    const type = obj["@type"];
    const types = Array.isArray(type) ? type : typeof type === "string" ? [type] : [];
    if (types.some((t) => typeof t === "string" && t.toLowerCase() === "recipe")) return obj;
    if (obj["@graph"]) {
      const found = findRecipeNode(obj["@graph"]);
      if (found) return found;
    }
  }
  return null;
}

function jsonLdText(value: unknown): string | undefined {
  if (typeof value === "string") return value.trim() || undefined;
  if (value && typeof value === "object" && "text" in (value as Record<string, unknown>)) {
    return jsonLdText((value as Record<string, unknown>).text);
  }
  return undefined;
}

/** schema.org's `recipeInstructions` is wildly inconsistent across sites: a single (sometimes
 *  HTML) string, an array of strings, or an array of HowToStep/HowToSection objects (a
 *  HowToSection nests further steps under `itemListElement`). Flattens every shape into a
 *  plain ordered list of step strings. */
function jsonLdInstructionSteps(value: unknown): string[] {
  if (typeof value === "string") {
    return stripHtml(value)
      .split(/\n+/)
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
  }
  if (Array.isArray(value)) {
    return value.flatMap((entry) => jsonLdInstructionSteps(entry));
  }
  if (value && typeof value === "object") {
    const obj = value as Record<string, unknown>;
    if (obj.itemListElement) return jsonLdInstructionSteps(obj.itemListElement);
    const text = jsonLdText(obj);
    return text ? [text] : [];
  }
  return [];
}

/** ISO 8601 duration ("PT30M", "PT1H15M") -> whole minutes, undefined if unparseable/zero. */
function parseIsoDurationMinutes(value: unknown): number | undefined {
  if (typeof value !== "string") return undefined;
  const match = value.match(/^PT(?:(\d+)H)?(?:(\d+)M)?/);
  if (!match) return undefined;
  const hours = Number(match[1] ?? 0);
  const minutes = Number(match[2] ?? 0);
  const total = hours * 60 + minutes;
  return total > 0 ? total : undefined;
}

function jsonLdYieldServings(value: unknown): number | undefined {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw === "number") return Math.round(raw);
  if (typeof raw === "string") {
    const match = raw.match(/\d+/);
    return match ? Number(match[0]) : undefined;
  }
  return undefined;
}

/** schema.org's `recipeIngredient` is one free-text line per ingredient ("300 g bloem", "2
 *  eieren") rather than separate name/amount fields — splits off a leading quantity so this
 *  still lands in the same {name, amount} shape [buildRecipeGenerationPrompt]'s AI output
 *  already uses, instead of dumping the whole line into `name`. Best-effort only: a line with
 *  no leading quantity ("zout naar smaak") just gets an empty `amount`. */
function splitIngredientLine(line: string): { name: string; amount: string } {
  const match = line.match(
    /^([\d½¼¾⅓⅔.,/\s]+(?:g|gram|kg|ml|l|el|tl|stuks?|stuk|cup|cups|oz|lb|teaspoons?|tablespoons?)?\.?)\s+(.+)$/i,
  );
  if (match && match[2].trim().length > 0) {
    return { name: match[2].trim(), amount: match[1].trim() };
  }
  return { name: line, amount: "" };
}

/**
 * [GeneratedRecipe] minus the three fields JSON-LD often doesn't carry (cuisine, total time,
 * serving count) — those become optional here rather than defaulted to ""/0, so a JSON-LD site
 * that simply never stated a serving count doesn't seed CustomRecipeEditScreen's review form
 * with a bogus "0" the household then has to notice and clear. `mapGeneratedRecipeToDetail` on
 * the client already reads a missing key as "no value" (not 0/""), so omitting the key entirely
 * — rather than sending an empty/zero placeholder — is what actually gets that behavior.
 */
type ExtractedRecipe = Pick<GeneratedRecipe, "title" | "ingredients" | "instructions"> &
  Partial<Pick<GeneratedRecipe, "cuisine" | "estimatedMinutes" | "servings">>;

/**
 * Parses schema.org Recipe JSON-LD out of a page's `<script type="application/ld+json">`
 * blocks. Null when the page has none, or the one found lacks the minimum a usable recipe
 * needs (a name plus at least one ingredient) — [importRecipeFromUrl] falls back to the
 * Claude-extraction path in that case rather than returning a half-empty result.
 */
export function extractJsonLdRecipe(html: string): ExtractedRecipe | null {
  const blocks = html.matchAll(/<script[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi);
  for (const block of blocks) {
    let parsed: unknown;
    try {
      parsed = JSON.parse(block[1].trim());
    } catch {
      continue;
    }
    const node = findRecipeNode(parsed);
    if (!node) continue;

    const title = (typeof node.name === "string" ? node.name : "").trim();
    const rawIngredients = Array.isArray(node.recipeIngredient) ? node.recipeIngredient : [];
    const ingredients = rawIngredients
      .filter((entry): entry is string => typeof entry === "string" && entry.trim().length > 0)
      .map((entry) => splitIngredientLine(entry.trim()));
    const instructions = jsonLdInstructionSteps(node.recipeInstructions);
    if (!title || ingredients.length === 0) continue;

    const cuisine = typeof node.recipeCuisine === "string" ? node.recipeCuisine.trim() : "";
    const estimatedMinutes =
      parseIsoDurationMinutes(node.totalTime) ??
      parseIsoDurationMinutes(node.cookTime) ??
      parseIsoDurationMinutes(node.prepTime);
    const servings = jsonLdYieldServings(node.recipeYield);

    return {
      title,
      ...(cuisine ? { cuisine } : {}),
      ...(estimatedMinutes !== undefined ? { estimatedMinutes } : {}),
      ...(servings !== undefined ? { servings } : {}),
      ingredients,
      instructions,
    };
  }
  return null;
}

function buildRecipeImportPrompt(pageText: string, sourceUrl: string, locale: string): string {
  return (
    "You are extracting a recipe from a webpage's text content for a home cooking app. The " +
    `page was fetched from ${sourceUrl}. Below is that page's visible text — navigation, ads, ` +
    "comments, and other clutter may still be mixed in; ignore anything that isn't part of " +
    "the recipe itself (title, ingredient list, step-by-step instructions).\n\n" +
    `Write your answer in this language: ${locale}, translating the recipe if the source page ` +
    "is in a different language.\n\n" +
    "- title: the recipe's name as given on the page.\n" +
    "- cuisine: a short cuisine/style label if inferable, otherwise a reasonable guess from the dish itself.\n" +
    "- estimatedMinutes: the page's stated total time if given, otherwise a realistic estimate.\n" +
    "- servings: the page's stated serving count if given, otherwise a realistic estimate (typically 2-6).\n" +
    "- ingredients: name + amount (e.g. \"300 g\", \"2 stuks\") per line, exactly as listed on the page.\n" +
    "- instructions: one string per step, in order, no numbering prefix (the app adds that) — " +
    "reproduce the page's own steps rather than inventing new ones.\n\n" +
    "If the page text below doesn't actually contain a recognizable recipe, still return your " +
    "best-effort guess rather than refusing — the app will let the user review and fix it " +
    "before saving.\n\n" +
    `Page text:\n${pageText}`
  );
}

/**
 * Callable Cloud Function that imports one recipe from a household-supplied URL (premium-only
 * — see MoreScreen/RecipesScreen). See this section's top comment for the JSON-LD-first,
 * Claude-fallback strategy.
 */
export const importRecipeFromUrl = onCall(
  // 45s: a cache-cold Claude-fallback path chains a page fetch and an Anthropic call, neither
  // of which is as fast as the other functions' single-purpose calls above.
  { secrets: [anthropicApiKey], cors: false, timeoutSeconds: 45, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<ImportRecipeFromUrlRequest> | undefined;
    const householdId = data?.householdId;
    const rawUrl = typeof data?.url === "string" ? data.url.trim() : "";
    const locale = typeof data?.locale === "string" && data.locale.trim().length > 0 ? data.locale : "nl";

    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    if (!rawUrl) {
      throw new HttpsError("invalid-argument", "url is required.");
    }
    const url = parseImportUrl(rawUrl);

    await requirePremiumHousehold(uid, householdId);

    const html = await fetchImportPage(url);

    const jsonLdRecipe = extractJsonLdRecipe(html);
    if (jsonLdRecipe) {
      return { recipe: jsonLdRecipe };
    }

    const pageText = stripHtml(html).slice(0, MAX_IMPORT_PAGE_TEXT_LENGTH);
    if (pageText.length < 200) {
      // Too little text to plausibly contain a recipe — fail clearly instead of spending an
      // Anthropic call on a near-empty page (a JS-only site with no server-rendered content, a
      // login wall, an anti-bot interstitial that returned 200 with a "please enable
      // JavaScript"/challenge page instead of the real one, etc.). Logged with a text snippet
      // so a repeat of this failure is actually diagnosable next time, rather than needing
      // another guess-and-check round.
      logger.warn("importRecipeFromUrl: page text too short for a recipe", {
        url: url.toString(),
        textLength: pageText.length,
        textSnippet: pageText.slice(0, 300),
      });
      throw new HttpsError("not-found", "no_recipe_found");
    }

    const responseText = await callAnthropicTextOnly(
      anthropicApiKey.value(),
      buildRecipeImportPrompt(pageText, url.toString(), locale),
      RECIPE_RESPONSE_SCHEMA,
    );

    let recipe: GeneratedRecipe;
    try {
      recipe = JSON.parse(responseText) as GeneratedRecipe;
    } catch (error) {
      logger.error("importRecipeFromUrl: could not parse model output as JSON", { responseText, error });
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

interface SpoonacularAnalyzedInstructionStep {
  number: number;
  step: string;
}

interface SpoonacularAnalyzedInstruction {
  name?: string;
  steps: SpoonacularAnalyzedInstructionStep[];
}

interface SpoonacularInfoResult {
  id: number;
  title: string;
  image?: string;
  cuisines?: string[];
  dishTypes?: string[];
  instructions?: string;
  // Spoonacular's plain-text `instructions` above is frequently empty even when the recipe
  // does have real steps — this structured field is the reliable source in that case, always
  // included in both /information and complexSearch's addRecipeInformation results, no extra
  // request param needed. See instructionsFromAnalyzed's doc for why both exist here.
  analyzedInstructions?: SpoonacularAnalyzedInstruction[];
  extendedIngredients?: SpoonacularIngredient[];
  readyInMinutes?: number;
  servings?: number;
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
  missedIngredients?: { name: string }[];
}

/** Strips Spoonacular's (usually HTML) instructions field down to plain, line-broken text. */
export function cleanInstructions(html: string | undefined): string | null {
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

/** Fallback for when [cleanInstructions] comes back empty — built from `analyzedInstructions`
 *  instead, which Spoonacular keeps populated far more reliably than the plain-text field.
 *  Numbered "1. ...", one step per line, same shape [generateRecipe]'s Claude-authored recipes
 *  already produce — RecipeDetailScreen's cook mode (see the Kotlin-side splitIntoSteps) parses
 *  that exact "leading number + separator" pattern to break instructions into individual steps,
 *  so this has to match it rather than use its own formatting. Multiple named groups (e.g.
 *  "Sauce" / "Assembly") are flattened into one continuous numbering — the client has no concept
 *  of named sub-sections, only a flat step list. */
export function instructionsFromAnalyzed(analyzed: SpoonacularAnalyzedInstruction[] | undefined): string | null {
  if (!analyzed || analyzed.length === 0) return null;
  const lines: string[] = [];
  for (const group of analyzed) {
    for (const step of group.steps ?? []) {
      const text = step.step?.trim();
      if (text) lines.push(`${lines.length + 1}. ${text}`);
    }
  }
  return lines.length > 0 ? lines.join("\n") : null;
}

/** Looks up one named nutrient (e.g. "Calories", "Protein") from Spoonacular's per-serving nutrition breakdown, rounded to 1 decimal — null if that recipe has no nutrition data (older cache entries from before this field existed, or Spoonacular simply not having it for that recipe) or doesn't list this particular nutrient. */
export function findNutrientAmount(nutrients: SpoonacularNutrient[] | undefined, name: string): number | null {
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
    instructions: cleanInstructions(result.instructions) ?? instructionsFromAnalyzed(result.analyzedInstructions),
    ingredients: (result.extendedIngredients ?? []).slice(0, 20).map((ingredient) => ({
      name: ingredient.name,
      measure: [ingredient.amount, ingredient.unit].filter((part) => part !== undefined && part !== "").join(" "),
    })),
    readyInMinutes: result.readyInMinutes ?? null,
    // How many people extendedIngredients' amounts feed — lets the client offer portion
    // scaling (see RecipeDetailScreen.scaleMeasure on the Kotlin side).
    servings: result.servings ?? null,
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
// shorter than the detail cache above — popularity ranking doesn't move fast enough to justify
// re-fetching more than once a day.
const RECIPE_BROWSE_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

type RecipeDetailPayload = ReturnType<typeof toRecipeDetail>;

/** What gets cached for a "browse" page — [totalResults] (Spoonacular's own count for this exact filter combo) is what lets a *cached* page still answer "is there a next page" without an extra live call. */
interface RecipeSearchCachePayload {
  details: RecipeDetailPayload[];
  totalResults: number;
}

/** Deterministic key for a "browse" mode call's exact param combination — order-independent on intolerances so ["Gluten","Dairy"] and ["Dairy","Gluten"] share a cache entry. Includes [offset] so each page of a paginated browse gets its own cache entry rather than colliding on page 1's. */
export function browseCacheKey(cuisine: string | undefined, intolerances: string[] | undefined, number: number, offset: number): string {
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

/** Fetches and caches one 100-recipe "browse" chunk (no query, popularity-sorted) for a given
 *  intolerances combo — a cache hit if [RECIPE_BROWSE_CACHE_TTL_MS] hasn't elapsed, a live
 *  Spoonacular call otherwise. Decoupled from whatever page size a client actually requested —
 *  see [RECIPE_BROWSE_CHUNK_SIZE]'s doc.
 *
 *  Deliberately lazy — only the one chunk a client actually asked for is ever fetched here, not
 *  the whole ~900-recipe catalog up front. An earlier version eagerly pre-warmed all nine chunks
 *  the moment any of them went stale (see git history's `warmBrowseCache`), reasoning that
 *  popularity ranking barely moves so paying once per [RECIPE_BROWSE_CACHE_TTL_MS] window beat
 *  paying per page. That reasoning assumed a ~150-point/day Spoonacular budget; on the 50-point/
 *  day tier this app has actually been issued, one such eager warm-up (9 chunks ×
 *  `addRecipeInformation`+`addRecipeNutrition` surcharge on 100 results apiece ≈ 63 points) blew
 *  through an entire day's quota in a single household's first "Recepten" open — see
 *  functions/README.md's Caching section for the full point-cost math. Paging deep enough to
 *  actually touch multiple chunks in one day is rare, so the occasional extra live call beats
 *  guaranteeing the worst case every time. */
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
          missedIngredientCount: r.missedIngredientCount,
          // Full list, not capped — RecipesScreen's "Op lijst" button adds every one of these
          // to the shopping list, so trimming here would silently drop real ingredients from
          // that add; the chip that *displays* them is what truncates, client-side.
          missedIngredients: (r.missedIngredients ?? []).map((m) => m.name),
        })),
      };
    }

    // The plain browse (no cuisine boost, no query) is by far the most common call — every
    // household's Recepten screen hits this on open. Requests land on one of nine 100-recipe
    // chunks (see RECIPE_BROWSE_CHUNK_SIZE); fetchAndCacheBrowseChunk itself checks the cache
    // first, so this is just "get me the one chunk this offset falls in" — see that function's
    // doc for why this stays lazy (one chunk at a time) rather than eagerly warming all nine.
    // The much smaller cuisine-boosted call (only 8 recipes, only page 1) falls through to the
    // per-page cache-or-fetch logic below instead, same as "query".
    if (data?.mode === "browse" && !data.cuisine) {
      const chunkOffset = Math.min(Math.floor(offset / RECIPE_BROWSE_CHUNK_SIZE) * RECIPE_BROWSE_CHUNK_SIZE, 800);
      const chunk = await fetchAndCacheBrowseChunk(data?.intolerances, chunkOffset, apiKey);
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
    // A cached entry with no instructions at all is treated as a miss rather than trusted as
    // "this recipe genuinely has none" — instructionsFromAnalyzed above didn't exist until this
    // fix, so any entry written before it (up to RECIPE_DETAIL_CACHE_TTL_MS old) can be stuck
    // with a null instructions that a live re-fetch would now actually fill in. Once that
    // window has fully rolled over this branch never triggers in practice (a fresh cache miss
    // and a "really has no instructions" hit look the same, both null), so it's safe to leave
    // in permanently rather than needing to remember to revert it.
    if (cached && cached.instructions) return { detail: cached };

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

export function languageNameForLocale(locale: string): string {
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

/**
 * Picks what [translateDetailFields] actually stores as the translated instructions —
 * [translated] (Claude's output) when it has something, [original] (the pre-translation
 * English text) otherwise. Never null when [original] has real content: a genuinely
 * instructions-less source recipe is the normal case the translation prompt's own "leave it
 * empty" instruction covers, but a model hiccup that drops a real instructions field must never
 * blank out an otherwise-working recipe — see [translateDetailFields]'s call site for why this
 * also protects the 90-day cross-household translation cache.
 */
export function resolveTranslatedInstructions(original: string | null, translated: string): string | null {
  return translated || original || null;
}

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
    instructions: resolveTranslatedInstructions(fields.instructions, parsed.instructions),
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
export function isCacheableRecipeId(id: string | null | undefined): id is string {
  return typeof id === "string" && id.length > 0 && !id.startsWith("ai-") && !id.startsWith("custom-");
}

export function translationDocId(id: string, locale: string): string {
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

// ---------------------------------------------------------------------------
// findMyHouseholds — account-recovery lookup for AccountLinkRepository.
// ---------------------------------------------------------------------------

/**
 * Lets a signed-in device find every household its *own* Firebase uid already belongs to —
 * the piece the client itself structurally can't do, since firestore.rules denies `list` on
 * `households` (so a client can never enumerate/search it) and there is no client-readable
 * index from uid to household. Only ever queries for `request.auth`'s own uid, taken from the
 * verified ID token rather than any client-supplied value, so this can't be used to probe
 * which household some *other* uid belongs to.
 *
 * Exists for the account-linking collision case (see AccountLinkRepository.switchToExistingGoogleAccount):
 * a Google account already linked to an old, locally-forgotten anonymous uid (e.g. after a
 * reinstall) has no way for that device to rediscover which household it was in. Once the
 * client switches its session to that old uid (signInWithCredential), calling this tells it
 * where to go.
 *
 * Member docs have always lived at `households/{id}/members/{uid}` (see
 * HouseholdMembersRepository.kt) — the document ID *is* the uid, and always has been, so
 * matching on document ID finds every member doc ever written, old or new, with zero
 * migration/backfill needed. The tradeoff: Firestore has no way to filter a collectionGroup
 * query by "last path segment equals X" alone, so this reads the *entire* `members` collection
 * group on every call and filters in code. Fine at this app's current scale (a household-code
 * app has no reason to have huge member counts); if that ever stops being true, add an
 * explicit `uid` field to each member doc (written going forward, backfilled for old docs by a
 * one-off admin script) and switch this to `.where("uid", "==", uid)` with a collection-group
 * index on that field instead.
 */
export const findMyHouseholds = onCall(
  { cors: false, timeoutSeconds: 30, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);

    const snapshot = await db.collectionGroup("members").get();
    const ownDocs = snapshot.docs.filter((doc) => doc.id === uid);

    const households = await Promise.all(
      ownDocs.map(async (doc) => {
        const householdId = doc.ref.parent.parent?.id;
        if (!householdId) return null;
        const householdDoc = await db.collection("households").doc(householdId).get();
        if (!householdDoc.exists) return null;
        const name = (householdDoc.data()?.name as string | undefined) ?? householdId;
        return { id: householdId, name };
      }),
    );

    return { households: households.filter((h): h is { id: string; name: string } => h !== null) };
  },
);

// ---------------------------------------------------------------------------
// verifyPurchase — server-side confirmation of a Play Billing purchase, via the Play
// Developer API, so `isPremium` stops being a value the client alone gets to assert.
// ---------------------------------------------------------------------------

// Must match applicationId in app/build.gradle.kts.
const ANDROID_PACKAGE_NAME = "com.dtraas.homestock";

// Mirrors BillingRepository.PremiumPlan's product ids — kept as an explicit allowlist so a
// malformed/probing request can't ask this function to look up an arbitrary product id via the
// Play Developer API using this project's credentials.
const VERIFIABLE_SUBSCRIPTION_IDS = new Set(["premium_monthly", "premium_yearly"]);

let cachedAndroidPublisher: ReturnType<typeof google.androidpublisher> | null = null;

/**
 * Lazily builds (and caches for the life of this function instance) an Android Publisher API
 * client authenticated as this Cloud Function's own runtime service account — Application
 * Default Credentials, no key file/secret to manage. That service account must be added as a
 * user in Play Console (Play Console → Users and permissions → Invite new users → the service
 * account's email, e.g. `<project-id>@appspot.gserviceaccount.com`) with the "View financial
 * data, orders, and cancellation survey responses" permission — without that grant, every call
 * below fails with a 403 from Google's side, not this code's.
 */
async function getAndroidPublisher() {
  if (cachedAndroidPublisher) return cachedAndroidPublisher;
  const auth = new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });
  cachedAndroidPublisher = google.androidpublisher({ version: "v3", auth });
  return cachedAndroidPublisher;
}

interface VerifyPurchaseRequest {
  householdId: string;
  productId: string;
  purchaseToken: string;
}

/**
 * Re-derives whether [request]'s caller genuinely owns an active `premium_monthly`/
 * `premium_yearly` subscription by asking Google directly (`purchases.subscriptions.get`),
 * rather than trusting `BillingRepository.isPremium` — a value the client computes from Play
 * Billing Library responses that a modified APK could simply fabricate. On success, writes the
 * verified result straight to this uid's member doc via the Admin SDK, which bypasses
 * firestore.rules — see that file's `members/{uid}` rule for why the client itself is no
 * longer allowed to write `isPremium` directly once this exists.
 *
 * Called by BillingRepository right after Play Billing acknowledges a new purchase (see its
 * `verifiedPurchases` flow) — not on every app start, since the Play Developer API has its own
 * quota and a purchase's active/expired state only actually changes at renewal/cancellation
 * time, which Play's own webhook-driven flow could refresh later without the client's help.
 * That real-time-notification path (Play Console → Monetization setup → Real-time developer
 * notifications, delivered to a Pub/Sub topic this project would subscribe another function to)
 * is the natural next step once this manual client-triggered path is in place — it's what
 * catches a cancellation/expiry between app opens, which this function alone can't.
 */
export const verifyPurchase = onCall(
  { cors: false, timeoutSeconds: 20, invoker: "public" },
  async (request) => {
    const uid = requireUid(request.auth);
    const data = request.data as Partial<VerifyPurchaseRequest> | undefined;
    const householdId = data?.householdId;
    const productId = data?.productId;
    const purchaseToken = data?.purchaseToken;

    if (!householdId || typeof householdId !== "string") {
      throw new HttpsError("invalid-argument", "householdId is required.");
    }
    if (!productId || !VERIFIABLE_SUBSCRIPTION_IDS.has(productId)) {
      throw new HttpsError("invalid-argument", "productId is not a recognized subscription.");
    }
    if (!purchaseToken || typeof purchaseToken !== "string") {
      throw new HttpsError("invalid-argument", "purchaseToken is required.");
    }

    const memberRef = db.collection("households").doc(householdId).collection("members").doc(uid);
    const memberSnapshot = await memberRef.get();
    if (!memberSnapshot.exists) {
      throw new HttpsError("permission-denied", "not_a_household_member");
    }

    // Active states per Google's own subscriptionState enum — grace period still has access
    // (Play is retrying a failed renewal payment), everything else (canceled/expired/on hold/
    // paused/pending) doesn't. See https://developer.android.com/google/play/billing/lifecycle/subscriptions.
    const ACTIVE_STATES = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"]);

    let isActive: boolean;
    try {
      const publisher = await getAndroidPublisher();
      const response = await publisher.purchases.subscriptionsv2.get({
        packageName: ANDROID_PACKAGE_NAME,
        token: purchaseToken,
      });
      const purchase = response.data;
      // The token alone identifies the purchase; cross-checking that it actually contains the
      // product id the client claims stops a valid-but-unrelated purchase token being replayed
      // to claim a different product.
      const matchesProduct = purchase.lineItems?.some((item) => item.productId === productId) === true;
      isActive = matchesProduct && ACTIVE_STATES.has(purchase.subscriptionState ?? "");
    } catch (error) {
      logger.error("verifyPurchase: Play Developer API call failed", { error, productId, householdId });
      throw new HttpsError("internal", "play_verification_failed");
    }

    await memberRef.set({ isPremium: isActive, isPremiumVerifiedAt: Date.now() }, { merge: true });

    return { verified: true, isPremium: isActive };
  },
);

// ---------------------------------------------------------------------------
// Real-time cross-device push (huisgenoot-activiteit, huishouden-wijziging) — the only
// Firestore-triggered (as opposed to onCall) exports in this file. See
// HomeStockMessagingService.kt on the client for how these are received and displayed, and
// HouseholdMembersRepository.updateFcmToken for how each member doc's `fcmToken` gets there.
// ---------------------------------------------------------------------------

/**
 * Sends [data] to every member of [householdId] except [excludeUid] (the acting/joining/leaving
 * device itself, so nobody gets pinged about their own action) who has a stored `fcmToken`.
 * Data-only (no `notification` key) — see HomeStockMessagingService's doc for why that matters.
 * Best-effort: a send failure is logged, never thrown, since this runs inside a background
 * trigger with nothing waiting on its result; a token FCM reports as no-longer-registered is
 * cleared from that member's doc so future triggers stop retrying it.
 */
async function pushToOtherMembers(
  householdId: string,
  excludeUid: string | undefined,
  data: Record<string, string>,
): Promise<void> {
  const membersSnapshot = await db.collection("households").doc(householdId).collection("members").get();
  const recipients = membersSnapshot.docs.filter(
    (doc) => doc.id !== excludeUid && typeof doc.get("fcmToken") === "string",
  );
  if (recipients.length === 0) return;

  const tokens = recipients.map((doc) => doc.get("fcmToken") as string);
  try {
    const response = await admin.messaging().sendEachForMulticast({ tokens, data });
    await Promise.all(
      response.responses.map((result, index) => {
        if (result.success) return Promise.resolve();
        const code = result.error?.code;
        if (code !== "messaging/registration-token-not-registered" && code !== "messaging/invalid-registration-token") {
          return Promise.resolve();
        }
        return recipients[index].ref.set({ fcmToken: admin.firestore.FieldValue.delete() }, { merge: true });
      }),
    );
  } catch (error) {
    logger.error("pushToOtherMembers: sendEachForMulticast failed", { error, householdId });
  }
}

/**
 * Pushes "huisgenoot-activiteit" to the rest of the household whenever an inventory/shopping-
 * list change is logged. The client builds the actual displayed title/body from `actorName`
 * (see HomeStockMessagingService) rather than this function sending pre-rendered text, so the
 * notification always reads in the *recipient's* language, not the actor's.
 */
export const notifyHouseholdActivity = onDocumentCreated(
  "households/{householdId}/activityLog/{entryId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const activity = snapshot.data();
    const actorUid = activity.actorUid as string | undefined;
    const actorName = (activity.actorName as string | undefined) ?? "";
    await pushToOtherMembers(event.params.householdId, actorUid, { type: "activity", actorName });
  },
);

/** Pushes "huishouden-wijziging" (iemand is toegetreden) to the rest of the household. */
export const notifyHouseholdMemberJoined = onDocumentCreated(
  "households/{householdId}/members/{uid}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const displayName = (snapshot.data().displayName as string | undefined) ?? "";
    await pushToOtherMembers(event.params.householdId, event.params.uid, {
      type: "member_joined",
      actorName: displayName,
    });
  },
);

/**
 * Pushes "huishouden-wijziging" (iemand heeft het huishouden verlaten) to the rest of the
 * household. Reads the just-deleted doc's last-known `displayName` off [event.data] — Firestore
 * still hands a delete trigger the document's content as it was right before deletion, even
 * though `get()`ing that same path now would return nothing.
 */
export const notifyHouseholdMemberLeft = onDocumentDeleted(
  "households/{householdId}/members/{uid}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const displayName = (snapshot.data()?.displayName as string | undefined) ?? "";
    await pushToOtherMembers(event.params.householdId, event.params.uid, {
      type: "member_left",
      actorName: displayName,
    });
  },
);
