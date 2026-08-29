import { describe, expect, it } from "vitest";
import { HttpsError } from "firebase-functions/v2/https";
import {
  browseCacheKey,
  cleanInstructions,
  extractJsonLdRecipe,
  findNutrientAmount,
  instructionsFromAnalyzed,
  isCacheableRecipeId,
  isDisallowedImportHost,
  isValidIsoDate,
  languageNameForLocale,
  parseImportUrl,
  parseReceiptPrice,
  requireUid,
  resolveTranslatedInstructions,
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
    expect(browseCacheKey(undefined, undefined, 10, 0)).toBe("browse_none_none_10_0_none_none_none");
  });

  it("differs when the filter sheet's ready-time/meal-type/diet params differ", () => {
    const base = browseCacheKey("italian", ["dairy"], 10, 0);
    expect(browseCacheKey("italian", ["dairy"], 10, 0, 30)).not.toBe(base);
    expect(browseCacheKey("italian", ["dairy"], 10, 0, undefined, "main course")).not.toBe(base);
    expect(browseCacheKey("italian", ["dairy"], 10, 0, undefined, undefined, "vegetarian")).not.toBe(base);
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

describe("resolveTranslatedInstructions", () => {
  it("prefers the translated text when Claude actually returned something", () => {
    expect(resolveTranslatedInstructions("Chop the onion.", "Snijd de ui.")).toBe("Snijd de ui.");
  });

  it("is null when the source recipe genuinely has no instructions — the expected, unremarkable case", () => {
    expect(resolveTranslatedInstructions(null, "")).toBeNull();
  });

  it("falls back to the English original rather than losing it, when the model drops a real instructions field", () => {
    // This is the bug this function exists to prevent: a source recipe with real instructions
    // must never end up with none just because one translation call came back empty.
    expect(resolveTranslatedInstructions("Chop the onion.", "")).toBe("Chop the onion.");
  });
});

describe("isDisallowedImportHost", () => {
  it("rejects localhost and its subdomains", () => {
    expect(isDisallowedImportHost("localhost")).toBe(true);
    expect(isDisallowedImportHost("app.localhost")).toBe(true);
  });

  it("rejects loopback and private IPv4 ranges", () => {
    expect(isDisallowedImportHost("127.0.0.1")).toBe(true);
    expect(isDisallowedImportHost("10.1.2.3")).toBe(true);
    expect(isDisallowedImportHost("172.16.0.5")).toBe(true);
    expect(isDisallowedImportHost("172.31.255.255")).toBe(true);
    expect(isDisallowedImportHost("192.168.1.1")).toBe(true);
  });

  it("rejects the cloud metadata IP and link-local range", () => {
    expect(isDisallowedImportHost("169.254.169.254")).toBe(true);
    expect(isDisallowedImportHost("169.254.1.1")).toBe(true);
  });

  it("does not reject a 172.x address outside the private /12 block", () => {
    expect(isDisallowedImportHost("172.32.0.1")).toBe(false);
    expect(isDisallowedImportHost("172.15.0.1")).toBe(false);
  });

  it("allows an ordinary public host", () => {
    expect(isDisallowedImportHost("example.com")).toBe(false);
    expect(isDisallowedImportHost("www.allrecipes.com")).toBe(false);
  });
});

describe("parseImportUrl", () => {
  it("accepts an ordinary https URL", () => {
    expect(parseImportUrl("https://example.com/recipe/123").hostname).toBe("example.com");
  });

  it("rejects a non-http(s) scheme", () => {
    expect(() => parseImportUrl("file:///etc/passwd")).toThrow(HttpsError);
    expect(() => parseImportUrl("ftp://example.com")).toThrow(HttpsError);
  });

  it("rejects an unparseable string", () => {
    expect(() => parseImportUrl("not a url")).toThrow(HttpsError);
  });

  it("rejects a private/loopback host", () => {
    expect(() => parseImportUrl("http://localhost:3000/recipe")).toThrow(HttpsError);
    expect(() => parseImportUrl("http://169.254.169.254/latest/meta-data")).toThrow(HttpsError);
  });
});

describe("extractJsonLdRecipe", () => {
  it("parses a plain schema.org Recipe block", () => {
    const html = `<html><head><script type="application/ld+json">${JSON.stringify({
      "@context": "https://schema.org",
      "@type": "Recipe",
      name: "Tomatensoep",
      recipeCuisine: "Nederlands",
      recipeYield: "4 servings",
      totalTime: "PT45M",
      recipeIngredient: ["500 g tomaten", "1 ui", "zout naar smaak"],
      recipeInstructions: ["Snijd de ui.", "Kook de tomaten."],
    })}</script></head><body></body></html>`;
    const recipe = extractJsonLdRecipe(html);
    expect(recipe).not.toBeNull();
    expect(recipe?.title).toBe("Tomatensoep");
    expect(recipe?.cuisine).toBe("Nederlands");
    expect(recipe?.servings).toBe(4);
    expect(recipe?.estimatedMinutes).toBe(45);
    expect(recipe?.ingredients).toEqual([
      { name: "tomaten", amount: "500 g" },
      { name: "ui", amount: "1" },
      { name: "zout naar smaak", amount: "" },
    ]);
    expect(recipe?.instructions).toEqual(["Snijd de ui.", "Kook de tomaten."]);
  });

  it("finds a Recipe node nested inside an @graph array", () => {
    const html = `<script type="application/ld+json">${JSON.stringify({
      "@context": "https://schema.org",
      "@graph": [
        { "@type": "WebSite", name: "Some Blog" },
        {
          "@type": ["Recipe"],
          name: "Pasta Carbonara",
          recipeIngredient: ["200 g pasta", "2 eieren"],
          recipeInstructions: [{ "@type": "HowToStep", text: "Kook de pasta." }],
        },
      ],
    })}</script>`;
    const recipe = extractJsonLdRecipe(html);
    expect(recipe?.title).toBe("Pasta Carbonara");
    expect(recipe?.instructions).toEqual(["Kook de pasta."]);
  });

  it("flattens HowToSection steps nested under itemListElement", () => {
    const html = `<script type="application/ld+json">${JSON.stringify({
      "@type": "Recipe",
      name: "Lasagne",
      recipeIngredient: ["1 pak lasagnebladen"],
      recipeInstructions: [
        {
          "@type": "HowToSection",
          name: "Saus",
          itemListElement: [
            { "@type": "HowToStep", text: "Maak de saus." },
            { "@type": "HowToStep", text: "Laat sudderen." },
          ],
        },
      ],
    })}</script>`;
    const recipe = extractJsonLdRecipe(html);
    expect(recipe?.instructions).toEqual(["Maak de saus.", "Laat sudderen."]);
  });

  it("returns null when no script block contains a Recipe node", () => {
    const html = `<script type="application/ld+json">${JSON.stringify({ "@type": "WebSite", name: "Some Blog" })}</script>`;
    expect(extractJsonLdRecipe(html)).toBeNull();
  });

  it("returns null when the Recipe node has no name or no ingredients", () => {
    const missingName = `<script type="application/ld+json">${JSON.stringify({
      "@type": "Recipe",
      recipeIngredient: ["1 ui"],
    })}</script>`;
    expect(extractJsonLdRecipe(missingName)).toBeNull();

    const missingIngredients = `<script type="application/ld+json">${JSON.stringify({
      "@type": "Recipe",
      name: "Iets",
      recipeIngredient: [],
    })}</script>`;
    expect(extractJsonLdRecipe(missingIngredients)).toBeNull();
  });

  it("omits cuisine/estimatedMinutes/servings entirely when the page doesn't state them, rather than defaulting to \"\"/0", () => {
    const html = `<script type="application/ld+json">${JSON.stringify({
      "@type": "Recipe",
      name: "Simpel gerecht",
      recipeIngredient: ["1 ui"],
      recipeInstructions: "Snijd de ui.",
    })}</script>`;
    const recipe = extractJsonLdRecipe(html);
    expect(recipe?.title).toBe("Simpel gerecht");
    expect("cuisine" in (recipe ?? {})).toBe(false);
    expect("estimatedMinutes" in (recipe ?? {})).toBe(false);
    expect("servings" in (recipe ?? {})).toBe(false);
  });

  it("tolerates malformed JSON in one block by skipping to the next", () => {
    const html =
      `<script type="application/ld+json">{ not valid json </script>` +
      `<script type="application/ld+json">${JSON.stringify({
        "@type": "Recipe",
        name: "Salade",
        recipeIngredient: ["1 krop sla"],
        recipeInstructions: "Meng alles.",
      })}</script>`;
    const recipe = extractJsonLdRecipe(html);
    expect(recipe?.title).toBe("Salade");
  });
});
