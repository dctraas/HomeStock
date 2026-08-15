package com.dtraas.homestock.data.export

import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
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
            headers.expiration, headers.minQuantity, headers.favorite, headers.note,
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
            )
        }
        return rows(header, dataRows)
    }
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
)
