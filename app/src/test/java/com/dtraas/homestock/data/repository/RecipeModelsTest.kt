package com.dtraas.homestock.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [RecipeDetail]'s `displayX` getters — in particular [RecipeDetail.displayInstructions],
 * whose missing English fallback (before it was fixed) silently blanked out a recipe's entire
 * bereidingswijze whenever a translation response came back without that one field. See
 * [RecipeDetail]'s class doc for the full context.
 */
class RecipeModelsTest {

    private fun baseDetail(
        instructions: String? = "Chop the onion. Fry until golden.",
        translatedForLocale: String? = null,
        translatedInstructions: String? = null,
    ) = RecipeDetail(
        id = "12345",
        name = "Tomato Soup",
        thumbnailUrl = null,
        category = "Soup",
        area = "Italian",
        instructions = instructions,
        ingredients = listOf("tomato" to "500g", "onion" to "1"),
        translatedForLocale = translatedForLocale,
        translatedInstructions = translatedInstructions,
    )

    @Test
    fun `no translation attempted yet falls back to the English instructions`() {
        val detail = baseDetail(translatedForLocale = null, translatedInstructions = null)
        assertEquals("Chop the onion. Fry until golden.", detail.displayInstructions)
    }

    @Test
    fun `a successful translation is preferred over the English original`() {
        val detail = baseDetail(
            translatedForLocale = "nl",
            translatedInstructions = "Snijd de ui. Bak tot goudbruin.",
        )
        assertEquals("Snijd de ui. Bak tot goudbruin.", detail.displayInstructions)
    }

    @Test
    fun `a translation attempt with a missing instructions field falls back to English, not blank`() {
        // The regression this test guards against: translatedForLocale gets set (a translation
        // attempt did happen for this locale) but translatedInstructions itself came back
        // null/blank — displayInstructions must still show the intact English original rather
        // than silently going blank.
        val detail = baseDetail(translatedForLocale = "nl", translatedInstructions = null)
        assertEquals("Chop the onion. Fry until golden.", detail.displayInstructions)
    }

    @Test
    fun `displayName and displayIngredients follow the same translated-or-fallback rule`() {
        val translated = baseDetail(translatedForLocale = "nl", translatedInstructions = "Snijd de ui.").copy(
            translatedName = "Tomatensoep",
            translatedIngredients = listOf("tomaat" to "500g", "ui" to "1"),
        )
        assertEquals("Tomatensoep", translated.displayName)
        assertEquals(listOf("tomaat" to "500g", "ui" to "1"), translated.displayIngredients)

        val partial = baseDetail(translatedForLocale = "nl", translatedInstructions = "Snijd de ui.")
        // translatedName/translatedIngredients weren't set on this one — same fallback rule
        // applies per-field, so the English originals show through instead of blanking out.
        assertEquals("Tomato Soup", partial.displayName)
        assertEquals(listOf("tomato" to "500g", "onion" to "1"), partial.displayIngredients)
    }
}
