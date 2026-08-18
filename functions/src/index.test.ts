import { describe, expect, it } from "vitest";
import { HttpsError } from "firebase-functions/v2/https";
import {
  browseCacheKey,
  cleanInstructions,
  findNutrientAmount,
  instructionsFromAnalyzed,
  isCacheableRecipeId,
  isValidIsoDate,
  languageNameForLocale,
  parseReceiptPrice,
  requireUid,
  translationDocId,
} from "./index";

// These cover the pure, I/O-free helpers exported from index.ts — the business-logic pieces
// that are actually feasible to unit-test without an Admin SDK / Firestore emulator. The
// onCall handlers themselves (recognizeReceipt, generateRecipe, searchRecipes,
// getRecipeInformation, translateRecipe, findMyHouseholds) call out to Firestore, Anthropic,
// and Spoonacular, and are exercised manually/in the emulator instead — see functions/README.md.

describe("requireUid", () => {
  it("returns the uid when auth is present", () => {
    expect(requireUid({ uid: "abc123" })).toBe("abc123");
  });

  it("throws unauthenticated when auth is missing", () => {
    expect(() => requireUid(undefined)).toThrow(HttpsError);
    try {
      requireUid(undefined);
      expect.unreachable();
    } catch (e) {
      expect((e as HttpsError).code).toBe("unauthenticated");
    }
  });
});

describe("parseReceiptPrice", () => {
  it("parses a plain decimal", () => {
    expect(parseReceiptPrice("3.49")).toBe(3.49);
  });

  it("parses a comma-decimal (Dutch receipts) into a dot-decimal", () => {
    expect(parseReceiptPrice("3,49")).toBe(3.49);
  });

  it("strips a currency symbol and stray whitespace", () => {
    expect(parseReceiptPrice("€ 12,99")).toBe(12.99);
  });

  it("returns null for text with no usable number", () => {
    expect(parseReceiptPrice("SUBTOTAAL")).toBeNull();
  });

  it("returns null for a negative amount", () => {
    expect(parseReceiptPrice("-3.49")).toBeNull();
  });
});

describe("isValidIsoDate", () => {
  it("accepts a well-formed ISO date", () => {
    expect(isValidIsoDate("2026-08-18")).toBe(true);
  });

  it("rejects a non-date string", () => {
    expect(isValidIsoDate("not-a-date")).toBe(false);
  });

  it("rejects a syntactically-plausible but impossible calendar date", () => {
    expect(isValidIsoDate("2026-13-40")).toBe(false);
  });

  it("rejects non-string input", () => {
    expect(isValidIsoDate(20260818)).toBe(false);
    expect(isValidIsoDate(undefined)).toBe(false);
  });
});

describe("cleanInstructions", () => {
  it("returns null for undefined input", () => {
    expect(cleanInstructions(undefined)).toBeNull();
  });

  it("strips HTML tags and collapses list items into newline-separated text", () => {
    const html = "<ol><li>Chop the onion.</li><li>Fry until golden.</li></ol>";
    expect(cleanInstructions(html)).toBe("- Chop the onion.\n- Fry until golden.");
  });

  it("returns null when the cleaned result is empty", () => {
    expect(cleanInstructions("<p></p>")).toBeNull();
  });

  it("decodes the handful of entities Spoonacular's HTML actually uses", () => {
    expect(cleanInstructions("Salt &amp; pepper&nbsp;to taste")).toBe("Salt & pepper to taste");
  });
});

describe("instructionsFromAnalyzed", () => {
  it("returns null for undefined/empty input", () => {
    expect(instructionsFromAnalyzed(undefined)).toBeNull();
    expect(instructionsFromAnalyzed([])).toBeNull();
  });

  it("numbers steps sequentially across multiple named groups", () => {
    // This is the exact bug fixed earlier: Spoonacular's plain-text `instructions` was often
    // empty even though `analyzedInstructions` had real steps — this fallback (and its
    // numbering, which RecipeDetailScreen's cook mode parses) is what fixed it.
    const analyzed = [
      { name: "Sauce", steps: [{ number: 1, step: "Simmer the tomatoes." }] },
      { name: "Assembly", steps: [{ number: 1, step: "Layer the pasta." }, { number: 2, step: "Bake for 20 minutes." }] },
    ];
    expect(instructionsFromAnalyzed(analyzed)).toBe(
      "1. Simmer the tomatoes.\n2. Layer the pasta.\n3. Bake for 20 minutes.",
    );
  });

  it("skips steps with blank text", () => {
    const analyzed = [{ name: null as unknown as string, steps: [{ number: 1, step: "  " }, { number: 2, step: "Serve hot." }] }];
    expect(instructionsFromAnalyzed(analyzed)).toBe("1. Serve hot.");
  });
});

describe("findNutrientAmount", () => {
  const nutrients = [
    { name: "Calories", amount: 412.345, unit: "kcal" },
    { name: "Protein", amount: 18, unit: "g" },
  ];

  it("finds and rounds a known nutrient to 1 decimal", () => {
    expect(findNutrientAmount(nutrients, "Calories")).toBe(412.3);
  });

  it("returns null for a nutrient not present", () => {
    expect(findNutrientAmount(nutrients, "Fat")).toBeNull();
  });

  it("returns null when there's no nutrition data at all", () => {
    expect(findNutrientAmount(undefined, "Calories")).toBeNull();
  });
});

describe("browseCacheKey", () => {
  it("is stable regardless of intolerances order (cache correctness depends on this)", () => {
    const a = browseCacheKey("italian", ["dairy", "egg"], 10, 0);
    const b = browseCacheKey("italian", ["egg", "dairy"], 10, 0);
    expect(a).toBe(b);
  });

  it("differs when any parameter differs", () => {
    const base = browseCacheKey("italian", ["dairy"], 10, 0);
    expect(browseCacheKey("mexican", ["dairy"], 10, 0)).not.toBe(base);
    expect(browseCacheKey("italian", ["dairy"], 10, 20)).not.toBe(base);
    expect(browseCacheKey("italian", [], 10, 0)).not.toBe(base);
  });

  it("uses stable placeholders for missing cuisine/intolerances", () => {
    expect(browseCacheKey(undefined, undefined, 10, 0)).toBe("browse_none_none_10_0");
  });
});

describe("isCacheableRecipeId", () => {
  it("accepts an ordinary Spoonacular numeric id", () => {
    expect(isCacheableRecipeId("12345")).toBe(true);
  });

  it("rejects AI-generated and custom recipe ids — never shared across households", () => {
    expect(isCacheableRecipeId("ai-abc123")).toBe(false);
    expect(isCacheableRecipeId("custom-abc123")).toBe(false);
  });

  it("rejects null/undefined/empty", () => {
    expect(isCacheableRecipeId(null)).toBe(false);
    expect(isCacheableRecipeId(undefined)).toBe(false);
    expect(isCacheableRecipeId("")).toBe(false);
  });
});

describe("translationDocId", () => {
  it("combines the recipe id and locale", () => {
    expect(translationDocId("12345", "nl")).toBe("12345_nl");
  });
});

describe("languageNameForLocale", () => {
  it("falls back to the raw locale code for an unmapped locale", () => {
    expect(languageNameForLocale("xx")).toBe("xx");
  });
});
