package com.dtraas.homestock.data.export

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses a CSV built by [CsvExporter] back into typed Voorraad rows — the "read side" mirroring
 * CsvExporter's "write side". Columns are read *positionally*, in exactly the order
 * [CsvExporter.inventoryToCsv] writes them (name, brand, category, quantity, unit, expiration,
 * minQuantity, favorite, note, barcode), not matched by header text — a translated or hand-edited
 * header row still parses fine, as long as the column order itself wasn't changed. The barcode
 * column is read leniently (a file exported before it existed simply has 9 columns, not 10 — see
 * the `cols.size < 9` check below, not 10) — MoreScreen's own import flow is what decides whether
 * a real (all-digit) barcode is worth an Open Food Facts re-check versus a synthetic
 * "csv-…"/"manual-…"/"ai-…" one from a product that was never actually scanned. This is meant for
 * round-tripping the app's own export, not for importing an arbitrary CSV from elsewhere.
 *
 * [categoryKeyByLabel]/[unitKeyByLabel] are the reverse of CsvExporter's label lookups — localized
 * display text (e.g. "Zuivel", "gram") back to the stored key (e.g. "zuivel", "gram") — built once
 * via `stringResource` in MoreScreen and passed in, same reasoning as CsvExporter. A category that
 * doesn't match any known label (wrong locale, typo, hand-edited file) falls back to "overig"
 * rather than dropping the row — matches [com.dtraas.homestock.data.model.Category.OVERIG]'s
 * storage key, hardcoded here as a literal so this file stays free of a `Category` import, same as
 * CsvExporter. A row missing its name or with an unparseable quantity is skipped outright, since
 * there's no sensible fallback for either.
 */
object CsvImporter {
    private const val FALLBACK_CATEGORY_KEY = "overig"
    private val importDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }

    fun parseInventoryCsv(
        csv: String,
        categoryKeyByLabel: Map<String, String>,
        unitKeyByLabel: Map<String, String>,
        yesLabel: String,
    ): InventoryImportResult {
        val allRows = parseCsv(csv)
        if (allRows.isEmpty()) return InventoryImportResult(emptyList(), 0)
        // First row is the header, written by CsvExporter — not data.
        return parseInventoryRows(allRows.drop(1), categoryKeyByLabel, unitKeyByLabel, yesLabel)
    }

    private fun parseInventoryRows(
        dataRows: List<List<String>>,
        categoryKeyByLabel: Map<String, String>,
        unitKeyByLabel: Map<String, String>,
        yesLabel: String,
    ): InventoryImportResult {
        var skipped = 0
        val imported = mutableListOf<ImportedInventoryRow>()
        for (cols in dataRows) {
            if (cols.size < 9) {
                skipped++
                continue
            }
            val name = cols[0].trim()
            val quantity = cols[3].trim().toIntOrNull()
            if (name.isEmpty() || quantity == null) {
                skipped++
                continue
            }
            imported += ImportedInventoryRow(
                name = name,
                brand = cols[1].trim().ifEmpty { null },
                categoryKey = categoryKeyByLabel[cols[2].trim()] ?: FALLBACK_CATEGORY_KEY,
                quantity = quantity,
                unitKey = unitKeyByLabel[cols[4].trim()],
                expirationDate = parseDateOrNull(cols[5].trim()),
                minQuantity = cols[6].trim().toIntOrNull(),
                isFavorite = cols[7].trim().equals(yesLabel, ignoreCase = true),
                note = cols[8].trim().ifEmpty { null },
                barcode = cols.getOrNull(9)?.trim()?.ifEmpty { null },
            )
        }
        return InventoryImportResult(imported, skipped)
    }

    /**
     * Reads back a [CsvExporter.shoppingListToCsv] file, positionally: name, category, store,
     * quantity, unit, note, price, checked, list — same "column order, not header text" contract
     * as [parseInventoryCsv]. A row missing its name or with an unparseable quantity is skipped,
     * same reasoning as an inventory row. The list column is read leniently (a file exported
     * before it existed simply has 8 columns, not 9 — see the `cols.size < 8` check below, not 9)
     * — a missing/blank value there means "the default list", same as [ImportedShoppingListRow.listName]
     * being null does on the way in.
     */
    fun parseShoppingListCsv(
        csv: String,
        categoryKeyByLabel: Map<String, String>,
        unitKeyByLabel: Map<String, String>,
        yesLabel: String,
    ): ShoppingListImportResult {
        val allRows = parseCsv(csv)
        if (allRows.isEmpty()) return ShoppingListImportResult(emptyList(), 0)
        return parseShoppingListRows(allRows.drop(1), categoryKeyByLabel, unitKeyByLabel, yesLabel)
    }

    private fun parseShoppingListRows(
        dataRows: List<List<String>>,
        categoryKeyByLabel: Map<String, String>,
        unitKeyByLabel: Map<String, String>,
        yesLabel: String,
    ): ShoppingListImportResult {
        var skipped = 0
        val imported = mutableListOf<ImportedShoppingListRow>()
        for (cols in dataRows) {
            if (cols.size < 8) {
                skipped++
                continue
            }
            val name = cols[0].trim()
            val quantity = cols[3].trim().toIntOrNull()
            if (name.isEmpty() || quantity == null) {
                skipped++
                continue
            }
            imported += ImportedShoppingListRow(
                name = name,
                categoryKey = categoryKeyByLabel[cols[1].trim()] ?: FALLBACK_CATEGORY_KEY,
                store = cols[2].trim().ifEmpty { null },
                quantity = quantity,
                unitKey = unitKeyByLabel[cols[4].trim()],
                note = cols[5].trim().ifEmpty { null },
                price = cols[6].trim().toDoubleOrNull(),
                isChecked = cols[7].trim().equals(yesLabel, ignoreCase = true),
                listName = cols.getOrNull(8)?.trim()?.ifEmpty { null },
            )
        }
        return ShoppingListImportResult(imported, skipped)
    }

    private fun parseDateOrNull(raw: String): Long? {
        if (raw.isEmpty()) return null
        return runCatching { importDateFormat.parse(raw)?.time }.getOrNull()
    }

    /**
     * Reads back a [CsvExporter.recipesToCsv] file, positionally: id, name, custom (Ja/Nee),
     * favorite (Ja/Nee), category, area, readyInMinutes, servings, ingredients, instructions —
     * same "column order, not header text, is what matters" contract as [parseInventoryCsv]. A
     * row missing its name is skipped, same as a nameless inventory row; every other field is
     * optional the way a recipe's own fields already are.
     */
    fun parseRecipesCsv(csv: String, yesLabel: String): RecipeImportResult {
        val allRows = parseCsv(csv)
        if (allRows.isEmpty()) return RecipeImportResult(emptyList(), 0)
        return parseRecipeRows(allRows.drop(1), yesLabel)
    }

    private fun parseRecipeRows(dataRows: List<List<String>>, yesLabel: String): RecipeImportResult {
        var skipped = 0
        val imported = mutableListOf<ImportedRecipeRow>()
        for (cols in dataRows) {
            if (cols.size < 10) {
                skipped++
                continue
            }
            val name = cols[1].trim()
            if (name.isEmpty()) {
                skipped++
                continue
            }
            val ingredients = cols[8].split(CsvExporter.INGREDIENT_SEPARATOR)
                .mapNotNull { entry ->
                    val parts = entry.split(CsvExporter.INGREDIENT_FIELD_SEPARATOR, limit = 2)
                    val ingredientName = parts.getOrNull(0)?.trim().orEmpty()
                    if (ingredientName.isEmpty()) null else ingredientName to (parts.getOrNull(1)?.trim().orEmpty())
                }
            imported += ImportedRecipeRow(
                id = cols[0].trim(),
                name = name,
                isCustom = cols[2].trim().equals(yesLabel, ignoreCase = true),
                isFavorite = cols[3].trim().equals(yesLabel, ignoreCase = true),
                category = cols[4].trim().ifEmpty { null },
                area = cols[5].trim().ifEmpty { null },
                readyInMinutes = cols[6].trim().toIntOrNull(),
                servings = cols[7].trim().toIntOrNull(),
                ingredients = ingredients,
                instructions = cols[9].trim().ifEmpty { null },
            )
        }
        return RecipeImportResult(imported, skipped)
    }

    /** Reads back a [CsvExporter.storesToCsv] file — name plus its gangvolgorde, blank-name rows
     *  skipped outright rather than counted (a store list has no other required field to make a
     *  blank row worth reporting). The aisleOrder column is read leniently, same reasoning as
     *  every other column CsvExporter added after this file's first release — a file exported
     *  before it existed simply has 1 column, and comes back with an empty gangvolgorde. */
    fun parseStoresCsv(csv: String): List<ImportedStoreRow> {
        val allRows = parseCsv(csv)
        if (allRows.isEmpty()) return emptyList()
        return parseStoreRows(allRows.drop(1))
    }

    private fun parseStoreRows(dataRows: List<List<String>>): List<ImportedStoreRow> =
        dataRows.mapNotNull { cols ->
            val name = cols.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val aisleOrder = cols.getOrNull(1)
                ?.split(CsvExporter.INGREDIENT_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            ImportedStoreRow(name, aisleOrder)
        }

    /**
     * Reads back a [CsvExporter.mealHistoryToCsv] file, positionally: date, slot, name, status —
     * same "column order, not header text" contract as [parseInventoryCsv]. [slotKeyByLabel] is
     * the reverse of the localized [com.dtraas.homestock.data.model.MealSlot] label lookup
     * MoreScreen builds for export, same reasoning as [categoryKeyByLabel] elsewhere in this
     * file. A row is skipped when its name is blank or its date/slot don't parse — there's no
     * sensible "default day" or "default slot" to fall back to for either.
     */
    fun parseMealHistoryCsv(
        csv: String,
        slotKeyByLabel: Map<String, String>,
        eatenLabel: String,
        wastedLabel: String,
    ): MealHistoryImportResult {
        val allRows = parseCsv(csv)
        if (allRows.isEmpty()) return MealHistoryImportResult(emptyList(), 0)
        return parseMealHistoryRows(allRows.drop(1), slotKeyByLabel, eatenLabel, wastedLabel)
    }

    private fun parseMealHistoryRows(
        dataRows: List<List<String>>,
        slotKeyByLabel: Map<String, String>,
        eatenLabel: String,
        wastedLabel: String,
    ): MealHistoryImportResult {
        var skipped = 0
        val imported = mutableListOf<ImportedMealHistoryRow>()
        for (cols in dataRows) {
            if (cols.size < 4) {
                skipped++
                continue
            }
            val date = cols[0].trim()
            val slotKey = slotKeyByLabel[cols[1].trim()]
            val name = cols[2].trim()
            if (date.isEmpty() || slotKey == null || name.isEmpty()) {
                skipped++
                continue
            }
            val statusRaw = cols[3].trim()
            imported += ImportedMealHistoryRow(
                date = date,
                slotKey = slotKey,
                name = name,
                status = when {
                    statusRaw.equals(eatenLabel, ignoreCase = true) -> "eaten"
                    statusRaw.equals(wastedLabel, ignoreCase = true) -> "wasted"
                    else -> null
                },
            )
        }
        return MealHistoryImportResult(imported, skipped)
    }

    /**
     * True when [csv]'s very first row is a lone one-column title cell rather than a real
     * (multi-column) header — i.e. it looks like a [CsvExporter.combinedToCsv] "Alles" export
     * bundling several sections, not a single-scope file. Every single-scope export
     * ([parseInventoryCsv]/[parseShoppingListCsv]/[parseRecipesCsv]/[parseStoresCsv]) always
     * starts with its own real, multi-column header row, so this never misfires on one of those.
     */
    fun isCombinedCsv(csv: String): Boolean = parseCsv(csv).firstOrNull()?.size == 1

    /**
     * Splits a [CsvExporter.combinedToCsv] file back into its per-section row groups — title to
     * that section's own rows, header row included first. Works entirely off [parseCsv]'s
     * already quote-aware row list rather than splitting the raw text on blank lines, so a
     * recipe's own multi-line Bereidingswijze field is never mistaken for a section boundary just
     * because it happens to contain a blank line itself. A lone one-column row anywhere in the
     * file is exactly what [CsvExporter.combinedToCsv] uses to mark a new section's title, so
     * that shape alone — not a fixed list of known section names — is what this looks for; a
     * section this app doesn't know how to import (or one a future version added) simply comes
     * back as an entry [parseCombinedCsv] doesn't recognize and skips.
     */
    fun splitCombinedCsv(csv: String): List<Pair<String, List<List<String>>>> {
        val sections = mutableListOf<Pair<String, MutableList<List<String>>>>()
        for (cols in parseCsv(csv)) {
            if (cols.size == 1) {
                sections += cols[0] to mutableListOf()
            } else {
                sections.lastOrNull()?.second?.add(cols)
            }
        }
        return sections
    }

    /**
     * The "Alles" export's read side: splits [csv] into its sections (see [splitCombinedCsv]) and
     * runs each one through the matching scope's own row parser, matched by comparing that
     * section's title against the *currently displayed* localized section titles the caller
     * passes in — the same strings [CsvExporter.combinedToCsv] was given when this file was
     * written, so a file exported in one language still imports correctly in that same language.
     * A section whose title matches none of them (an older/newer export format's own new section)
     * is left out of the result entirely rather than guessed at.
     */
    fun parseCombinedCsv(
        csv: String,
        inventorySectionTitle: String,
        shoppingListSectionTitle: String,
        recipesSectionTitle: String,
        storesSectionTitle: String,
        mealHistorySectionTitle: String,
        categoryKeyByLabel: Map<String, String>,
        unitKeyByLabel: Map<String, String>,
        slotKeyByLabel: Map<String, String>,
        yesLabel: String,
        eatenLabel: String,
        wastedLabel: String,
    ): CombinedImportResult {
        var inventory: InventoryImportResult? = null
        var shoppingList: ShoppingListImportResult? = null
        var recipes: RecipeImportResult? = null
        var stores: List<ImportedStoreRow>? = null
        var mealHistory: MealHistoryImportResult? = null
        for ((title, rows) in splitCombinedCsv(csv)) {
            if (rows.isEmpty()) continue
            val dataRows = rows.drop(1) // that section's own header row, not data.
            when (title) {
                inventorySectionTitle -> inventory = parseInventoryRows(dataRows, categoryKeyByLabel, unitKeyByLabel, yesLabel)
                shoppingListSectionTitle -> shoppingList = parseShoppingListRows(dataRows, categoryKeyByLabel, unitKeyByLabel, yesLabel)
                recipesSectionTitle -> recipes = parseRecipeRows(dataRows, yesLabel)
                storesSectionTitle -> stores = parseStoreRows(dataRows)
                mealHistorySectionTitle -> mealHistory = parseMealHistoryRows(dataRows, slotKeyByLabel, eatenLabel, wastedLabel)
            }
        }
        return CombinedImportResult(inventory, shoppingList, recipes, stores, mealHistory)
    }

    /**
     * Minimal RFC 4180 parser: quoted fields, `""`-escaped quotes inside them, and both `\r\n`
     * and bare `\n` line endings (a file re-saved by a text editor rather than a spreadsheet app
     * may not keep CsvExporter's own `\r\n`). No CSV library exists in this project's
     * dependencies, so this hand-rolled state machine is the whole parser.
     */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var pendingRow = false
        var i = 0

        fun endField() {
            currentRow.add(field.toString())
            field.clear()
            pendingRow = true
        }

        fun endRow() {
            endField()
            rows.add(currentRow.toList())
            currentRow.clear()
            pendingRow = false
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i += 2
                    }
                    c == '"' -> {
                        inQuotes = false
                        i++
                    }
                    else -> {
                        field.append(c)
                        i++
                    }
                }
                continue
            }
            when (c) {
                '"' -> { inQuotes = true; i++ }
                ',' -> { endField(); i++ }
                '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                    i++
                }
                '\n' -> { endRow(); i++ }
                else -> { field.append(c); i++ }
            }
        }
        // A file that doesn't end in a newline still has one unterminated row/field to flush.
        if (field.isNotEmpty() || currentRow.isNotEmpty() || pendingRow) endRow()

        // Drop stray blank lines (a single empty field and nothing else) rather than treating
        // them as zero-length data rows.
        return rows.filter { row -> row.size > 1 || row.firstOrNull()?.isNotEmpty() == true }
    }
}

data class ImportedInventoryRow(
    val name: String,
    val brand: String?,
    val categoryKey: String,
    val quantity: Int,
    val unitKey: String?,
    val expirationDate: Long?,
    val minQuantity: Int?,
    val isFavorite: Boolean,
    val note: String?,
    // The product's own barcode as CsvExporter last saw it — real (all-digit) if the product was
    // ever actually scanned, or one of the app's own synthetic prefixes ("csv-…"/"manual-…"/
    // "ai-…") otherwise. Null for a file exported before this column existed. See MoreScreen's
    // confirmImport for what a real barcode is used for on the way back in.
    val barcode: String?,
)

data class InventoryImportResult(
    val rows: List<ImportedInventoryRow>,
    val skippedCount: Int,
)

data class ImportedShoppingListRow(
    val name: String,
    val categoryKey: String,
    val store: String?,
    val quantity: Int,
    val unitKey: String?,
    val note: String?,
    val price: Double?,
    val isChecked: Boolean,
    // Null (or the default list's own exported display name — MoreScreen is the one that knows
    // that name and tells the two apart) means the default, unnamed list. Any other name gets
    // looked up (or created, if it's new) by MoreScreen's own commitShoppingListRows.
    val listName: String?,
)

data class ShoppingListImportResult(
    val rows: List<ImportedShoppingListRow>,
    val skippedCount: Int,
)

data class ImportedRecipeRow(
    val id: String,
    val name: String,
    val isCustom: Boolean,
    val isFavorite: Boolean,
    val category: String?,
    val area: String?,
    val readyInMinutes: Int?,
    val servings: Int?,
    val ingredients: List<Pair<String, String>>,
    val instructions: String?,
)

data class RecipeImportResult(
    val rows: List<ImportedRecipeRow>,
    val skippedCount: Int,
)

data class ImportedStoreRow(
    val name: String,
    // Each element is one aisle path, same encoding as [com.dtraas.homestock.data.local.entity.StoreEntity.aisleOrder]
    // itself — empty means "no custom gangvolgorde set" (or a file exported before this column
    // existed), not "clear whatever the household already had".
    val aisleOrder: List<String>,
)

data class ImportedMealHistoryRow(
    // Kept as the raw "yyyy-MM-dd" text CsvExporter wrote rather than parsed here, so this file
    // stays free of a java.time import — MoreScreen's own commitMealHistoryRows parses it with
    // the same LocalDate.toString()/parse() round-trip CsvExporter's own date column already
    // relies on implicitly.
    val date: String,
    val slotKey: String,
    val name: String,
    // "eaten"/"wasted"/null — a com.dtraas.homestock.data.local.entity.MealCompletionStatus
    // storage key, kept as a raw string for the same "no extra import" reason as [date].
    val status: String?,
)

data class MealHistoryImportResult(
    val rows: List<ImportedMealHistoryRow>,
    val skippedCount: Int,
)

/**
 * [CsvImporter.parseCombinedCsv]'s result — one nullable field per importable scope, null for any
 * section the file simply didn't have (e.g. a household that had no custom stores yet). Also used
 * directly by MoreScreen as the shape of a *single*-scope import's own staged preview (every
 * field but that one scope's left at its default null) — see MoreScreen's own pendingImportPreview
 * doc for why every scope now stages through this same one type instead of committing straight
 * away.
 */
data class CombinedImportResult(
    val inventory: InventoryImportResult? = null,
    val shoppingList: ShoppingListImportResult? = null,
    val recipes: RecipeImportResult? = null,
    val stores: List<ImportedStoreRow>? = null,
    val mealHistory: MealHistoryImportResult? = null,
)
