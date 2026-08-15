package com.dtraas.homestock.data.export

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses a CSV built by [CsvExporter] back into typed Voorraad rows — the "read side" mirroring
 * CsvExporter's "write side". Columns are read *positionally*, in exactly the order
 * [CsvExporter.inventoryToCsv] writes them (name, brand, category, quantity, unit, expiration,
 * minQuantity, favorite, note), not matched by header text — a translated or hand-edited header
 * row still parses fine, as long as the column order itself wasn't changed. This is meant for
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
        val dataRows = allRows.drop(1) // first row is the header, written by CsvExporter — not data.

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
            )
        }
        return InventoryImportResult(imported, skipped)
    }

    private fun parseDateOrNull(raw: String): Long? {
        if (raw.isEmpty()) return null
        return runCatching { importDateFormat.parse(raw)?.time }.getOrNull()
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
)

data class InventoryImportResult(
    val rows: List<ImportedInventoryRow>,
    val skippedCount: Int,
)
