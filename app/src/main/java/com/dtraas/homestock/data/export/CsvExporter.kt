package com.dtraas.homestock.data.export

import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.local.entity.ShoppingListItemEntity
import com.dtraas.homestock.data.local.entity.StoreEntity
import com.dtraas.homestock.data.repository.RecipeDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a CSV export of Voorraad — a plain, Compose-independent function so it's easy to call
 * from a click handler (not a composable context) and to unit test. Column headers and the
 * "Ja"/"Nee" values are passed in already localized (built once via `stringResource` in
 * MoreScreen, where the export is triggered from) rather than hardcoded here, so the exported
 * file matches whatever language the app itself is currently in — same as every other piece of
 * user-facing text in the app. [CsvImporter] reads this exact column layout back in.
 *
 * [categoryLabel]/[unitLabel] resolve a stored `Category`/`MeasurementUnit` storage key (e.g.
 * "zuivel", "gram") to its localized display name — the CSV should read like something a person
 * chose from a dropdown, not the internal key literal.
 */
object CsvExporter {
    private val exportDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    /** Wraps one CSV field in double quotes and escapes any quotes inside it — the minimum needed
     *  for a field to round-trip correctly through Excel/Sheets, since names/notes can freely
     *  contain commas, quotes, or newlines. */
    private fun field(value: String?): String = "\"" + (value ?: "").replace("\"", "\"\"") + "\""

    private fun row(vararg fields: String?): String = fields.joinToString(",") { field(it) }

    /** \r\n per RFC 4180, not just \n — some spreadsheet importers are picky about that. */
    private fun rows(header: String, dataRows: List<String>): String =
        (listOf(header) + dataRows).joinToString("\r\n")

    fun inventoryToCsv(
        items: List<InventoryItemWithProduct>,
        headers: InventoryCsvHeaders,
        categoryLabel: (String) -> String,
        unitLabel: (String?) -> String,
        yesLabel: String,
        noLabel: String,
    ): String {
        val header = row(
            headers.name, headers.brand, headers.category, headers.quantity, headers.unit,
            headers.expiration, headers.minQuantity, headers.favorite, headers.note, headers.barcode,
        )
        val dataRows = items.map { item ->
            row(
                item.name,
                item.brand,
                categoryLabel(item.category),
                item.quantity.toString(),
                unitLabel(item.unit),
                item.expirationDate?.let { exportDateFormat.format(Date(it)) },
                item.minQuantity?.toString(),
                if (item.isFavorite) yesLabel else noLabel,
                item.note,
                // A synthetic barcode ("csv-…"/"manual-…"/"ai-…" — see the barcode column's own
                // read-side doc in CsvImporter) round-trips here just as faithfully as a real
                // scanned one; CsvImporter is the one that tells the two apart on the way back in.
                item.barcode,
            )
        }
        return rows(header, dataRows)
    }

    /**
     * A shopping-list export, same reasoning as [inventoryToCsv] — the "Lijsten" scope of
     * MoreScreen's Data-overzetten sheet — every open and checked line, across every named list,
     * with its store/quantity/unit/note/price as the household set them. [listName] resolves an
     * item's [ShoppingListItemEntity.listId] to that list's own display name (the default,
     * unnamed list's own name for a null id) — a list's Firestore id isn't portable across a
     * re-import the way its name is. [CsvImporter.parseShoppingListCsv] reads this exact shape
     * back in.
     */
    fun shoppingListToCsv(
        items: List<ShoppingListItemEntity>,
        headers: ShoppingListCsvHeaders,
        categoryLabel: (String) -> String,
        unitLabel: (String) -> String,
        listName: (String?) -> String,
        yesLabel: String,
        noLabel: String,
    ): String {
        val header = row(
            headers.name, headers.category, headers.store, headers.quantity, headers.unit,
            headers.note, headers.price, headers.checked, headers.list,
        )
        val dataRows = items.map { item ->
            row(
                item.name,
                categoryLabel(item.category),
                item.store.ifEmpty { null },
                item.quantity.toString(),
                unitLabel(item.unit),
                item.note,
                item.price?.toString(),
                if (item.isChecked) yesLabel else noLabel,
                listName(item.listId),
            )
        }
        return rows(header, dataRows)
    }

    /**
     * Every dataset in one file — one column count for the whole file would force one section's
     * columns onto another's, so each [sections] entry (title to already-built CSV body) keeps
     * its own header, with a blank line and a one-column section title before each one after the
     * first — same convention a household would use hand-combining several exports in a
     * spreadsheet themselves. Used for MoreScreen's "Alles"-scope export.
     */
    fun combinedToCsv(sections: List<Pair<String, String>>): String =
        sections.flatMapIndexed { index, (title, body) ->
            if (index == 0) listOf(row(title), body) else listOf("", row(title), body)
        }.joinToString("\r\n")

    /**
     * "Mijn recepten" + "Favoriete recepten" together (the "Recepten" scope of MoreScreen's
     * Data-overzetten sheet) — [recipes] is expected pre-merged and de-duplicated by id (a
     * recipe favorited *and* hand-entered appears once, with both [headers]' custom/favorite
     * columns "Ja"); [customIds]/[favoriteIds] are what actually drive those two columns. Full
     * detail, not just a list row — every ingredient and the whole bereidingswijze — since
     * that's the entire point of exporting a recipe versus just its name. Ingredients are one
     * field, [INGREDIENT_SEPARATOR]-joined name/measure pairs (each pair itself
     * [INGREDIENT_FIELD_SEPARATOR]-joined) — CSV's own comma/quote escaping already protects
     * commas *inside* an ingredient name, so a second delimiter level is needed to keep a whole
     * ingredient list inside one field rather than exploding the column count per recipe.
     * [CsvImporter.parseRecipesCsv] reads this exact shape back in.
     */
    fun recipesToCsv(
        recipes: List<RecipeDetail>,
        customIds: Set<String>,
        favoriteIds: Set<String>,
        headers: RecipeCsvHeaders,
        yesLabel: String,
        noLabel: String,
    ): String {
        val header = row(
            headers.id, headers.name, headers.custom, headers.favorite, headers.category,
            headers.area, headers.readyInMinutes, headers.servings, headers.ingredients, headers.instructions,
        )
        val dataRows = recipes.map { recipe ->
            row(
                recipe.id,
                recipe.name,
                if (recipe.id in customIds) yesLabel else noLabel,
                if (recipe.id in favoriteIds) yesLabel else noLabel,
                recipe.category,
                recipe.area,
                recipe.readyInMinutes?.toString(),
                recipe.servings?.toString(),
                recipe.ingredients.joinToString(INGREDIENT_SEPARATOR) { (name, measure) -> "$name$INGREDIENT_FIELD_SEPARATOR$measure" },
                recipe.instructions,
            )
        }
        return rows(header, dataRows)
    }

    /** The household's custom store list (the "Winkels" scope) — name plus its own gangvolgorde
     *  (see [StoreEntity.aisleOrder]'s doc), in the household's own sortOrder. Each store's
     *  aisleOrder is itself a list of paths; [INGREDIENT_SEPARATOR]-joined into one field the same
     *  way [recipesToCsv] joins its ingredients, since a store with no custom gangvolgorde at all
     *  still needs to round-trip as "no paths", not as one blank path.
     *  [CsvImporter.parseStoresCsv] reads this back in. */
    fun storesToCsv(stores: List<StoreEntity>, headers: StoreCsvHeaders): String {
        val header = row(headers.name, headers.aisleOrder)
        val dataRows = stores.map { store -> row(store.name, store.aisleOrder.joinToString(INGREDIENT_SEPARATOR)) }
        return rows(header, dataRows)
    }

    /**
     * A read-out of the maaltijdplanner's past entries (the "Maaltijden historie" scope).
     * [entries] are expected pre-formatted (date/slot/status already localized) by the caller,
     * same convention every other export function here uses. [CsvImporter.parseMealHistoryCsv]
     * reads this back in as brand-new, hand-typed-style planned meals — a re-import has no way to
     * tell whether a given entry is already back on the calendar, so re-importing the same file
     * twice does mean the same day doubles up; MoreScreen's preview step is what gives a household
     * the chance to notice that before it happens, same reasoning Voorraad's own preview has
     * always had.
     */
    fun mealHistoryToCsv(entries: List<MealHistoryCsvRow>, headers: MealHistoryCsvHeaders): String {
        val header = row(headers.date, headers.slot, headers.name, headers.status)
        val dataRows = entries.map { row(it.date, it.slot, it.name, it.status) }
        return rows(header, dataRows)
    }

    // Between-ingredient / within-ingredient-pair separators for the [recipesToCsv] ingredients
    // field — [CsvImporter.parseRecipesCsv] must split on the exact same two literals. Also reused
    // by [storesToCsv]'s aisleOrder column for the same "several values, one field" reason.
    const val INGREDIENT_SEPARATOR = ";"
    const val INGREDIENT_FIELD_SEPARATOR = "|"
}

data class InventoryCsvHeaders(
    val name: String,
    val brand: String,
    val category: String,
    val quantity: String,
    val unit: String,
    val expiration: String,
    val minQuantity: String,
    val favorite: String,
    val note: String,
    val barcode: String,
)

data class ShoppingListCsvHeaders(
    val name: String,
    val category: String,
    val store: String,
    val quantity: String,
    val unit: String,
    val note: String,
    val price: String,
    val checked: String,
    val list: String,
)

data class RecipeCsvHeaders(
    val id: String,
    val name: String,
    val custom: String,
    val favorite: String,
    val category: String,
    val area: String,
    val readyInMinutes: String,
    val servings: String,
    val ingredients: String,
    val instructions: String,
)

data class StoreCsvHeaders(val name: String, val aisleOrder: String)

data class MealHistoryCsvHeaders(
    val date: String,
    val slot: String,
    val name: String,
    val status: String,
)

/** One already-localized row for [CsvExporter.mealHistoryToCsv] — see that function's doc for why this is export-only. */
data class MealHistoryCsvRow(
    val date: String,
    val slot: String,
    val name: String,
    val status: String?,
)
