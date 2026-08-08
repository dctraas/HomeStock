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

const ANTHROPIC_MODEL = "claude-haiku-4-5";
const ANTHROPIC_VERSION = "2023-06-01";

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

const RESPONSE_SCHEMA = {
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

const ALLOWED_MIME_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

// Generous but bounded — a phone photo re-encoded as JPEG at reasonable quality lands well
// under this; it exists to cap Anthropic spend per call, not to accommodate legitimate large
// uploads.
const MAX_BASE64_LENGTH = 8_000_000; // ~6 MB decoded

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

function buildPrompt(locale: string): string {
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

interface AnthropicTextBlock {
  type: "text";
  text: string;
}

interface AnthropicMessageResponse {
  content: Array<AnthropicTextBlock | { type: string }>;
}

async function callAnthropic(apiKey: string, imageBase64: string, mimeType: string, locale: string): Promise<string> {
  const response = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": apiKey,
      "anthropic-version": ANTHROPIC_VERSION,
    },
    body: JSON.stringify({
      model: ANTHROPIC_MODEL,
      max_tokens: 1024,
      messages: [
        {
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: mimeType, data: imageBase64 } },
            { type: "text", text: buildPrompt(locale) },
          ],
        },
      ],
      output_config: { format: { type: "json_schema", schema: RESPONSE_SCHEMA } },
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    logger.error("recognizeProduct: Anthropic API returned an error", { status: response.status, body });
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
 * from and edit — mirroring the shape the app's on-device ML Kit fallback used to return, so
 * the client-side UI (candidate chips, editable name field) didn't need to change shape.
 *
 * Premium is enforced here, not just in the app UI: this function re-derives it from
 * Firestore rather than trusting a client-supplied flag, so a modified APK can't call it for
 * free. See README.md in this directory for the full deploy + secret setup.
 */
export const recognizeProduct = onCall(
  { secrets: [anthropicApiKey], cors: false, timeoutSeconds: 30 },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }

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

    // Household-wide premium check — same rule as HouseholdMembersRepository
    // .observeHouseholdIsPremium() on the client: any member device with an active
    // subscription unlocks the whole household. Re-derived server-side from Firestore so a
    // patched client can't fake it. The membership-doc check first (this uid must itself be a
    // member) also prevents an arbitrary caller from probing an unrelated household's premium
    // status by guessing its id.
    const firestore = admin.firestore();
    const membersSnapshot = await firestore
      .collection("households")
      .doc(householdId)
      .collection("members")
      .get();

    const isMember = membersSnapshot.docs.some((doc) => doc.id === uid);
    if (!isMember) {
      throw new HttpsError("permission-denied", "not_a_household_member");
    }

    const isPremium = membersSnapshot.docs.some((doc) => doc.get("isPremium") === true);
    if (!isPremium) {
      throw new HttpsError("permission-denied", "premium_required");
    }

    const responseText = await callAnthropic(anthropicApiKey.value(), imageBase64, mimeType, locale);

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
