package com.dtraas.boodschapbeheer.data.receipt

/** A single candidate product line pulled off a scanned receipt. */
data class ReceiptLineItem(val name: String, val price: String?)

/**
 * Best-effort line-by-line parser for Dutch supermarket receipts, turning raw
 * OCR text into candidate product names. There's no structured format to rely
 * on — every chain prints differently — so this only does two things: drop
 * lines that are obviously not a product (totals, VAT, dates, store
 * boilerplate), and strip a trailing price off what's left. It's deliberately
 * conservative and will miss or mis-split lines on real receipts; the confirm
 * screen exists specifically so a human checks its output before anything is
 * saved. This is a Beta feature (see MoreScreen).
 */
object ReceiptParser {

    private val noiseKeywords = listOf(
        "totaal", "subtotaal", "btw", "statiegeld", "korting", "bonus", "kassabon",
        "datum", "tijd", "kassa", "kassier", "transactie", "pinnen", "pin ", "contant",
        "retour", "artikelen", "terminal", "bedankt", "tot ziens", "kvk", "iban",
        "bon nr", "www.", "openingstijden",
    )

    private val trailingPriceRegex = Regex("""(\d{1,4}[.,]\d{2})\s*$""")

    fun parse(rawText: String): List<ReceiptLineItem> =
        rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { line -> noiseKeywords.any { keyword -> line.lowercase().contains(keyword) } }
            .mapNotNull(::toLineItem)

    private fun toLineItem(line: String): ReceiptLineItem? {
        val priceMatch = trailingPriceRegex.find(line)
        val namePart = if (priceMatch != null) line.substring(0, priceMatch.range.first) else line
        val name = namePart.trim().trimEnd('-', '*', 'x', 'X', ' ').trim()

        // A "name" that's only digits/symbols (a barcode, a lone price, a date) isn't a product.
        if (name.length < 2 || name.none { it.isLetter() }) return null

        return ReceiptLineItem(name = name, price = priceMatch?.value?.trim())
    }
}
