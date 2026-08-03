package com.dtraas.boodschapbeheer.data.receipt

/** A single candidate product line pulled off a scanned receipt. */
data class ReceiptLineItem(val name: String, val price: String?)

/**
 * Best-effort line-by-line parser for Dutch supermarket receipts, turning raw
 * OCR text into candidate product names. There's no structured format to rely
 * on — every chain prints differently — so this leans on one strong structural
 * signal (product lines end in a price; store/legal/payment boilerplate almost
 * never does) plus a keyword denylist for the boilerplate that *does* happen to
 * end in something price-shaped (VAT breakdown rows, card terminal totals).
 * It's deliberately conservative and will miss some real product lines (e.g.
 * ones OCR mangled the price on) in favor of not pulling in noise — the confirm
 * screen exists specifically so a human checks its output before anything is
 * saved. This is a Beta feature (see MoreScreen).
 */
object ReceiptParser {

    private val noiseKeywords = listOf(
        // Totals & VAT
        "totaal", "subtotaal", "eindtotaal", "te betalen", "btw", "excl", "incl",
        // Payment / terminal
        "statiegeld", "kassabon", "kassa", "kassier", "transactie", "pinnen", "pin ",
        "contant", "retour", "terminal", "creditcard", "maestro", "mastercard", "visa",
        "chip", "kaartnummer", "kaart nr", "autorisatie", "referentie", "goedgekeurd",
        "akkoord", "wisselgeld", "gepast", "betaalwijze",
        // Loyalty / discounts
        "korting", "bonus", "bonuskaart", "spaarzegel", "spaarpunt", "voordeel", "airmiles",
        // Store/legal boilerplate
        "artikelen", "bedankt", "tot ziens", "welkom", "fijne dag", "prettige dag",
        "kvk", "iban", "bon nr", "bonnummer", "filiaal", "vestiging", "openingstijden",
        "klantenservice", "handelsregister", "btw-nummer", "www.", "@", "tel:", "telefoon",
        // Date/time labels (the values themselves are caught by the numeric-line filter below)
        "datum", "tijd",
    )

    // Product lines end in a price; a bare percentage (VAT-rate rows like "9% BTW") does
    // too often enough to need its own check even without a "btw" keyword on the line.
    private val percentOnlyRegex = Regex("""^\s*\d{1,2}\s*%""")

    // Dutch postal code ("1234 AB"), a phone number, or a URL/email — none of these are
    // product names, and they can slip past the keyword list on a store's letterhead line.
    private val postalCodeRegex = Regex("""\b\d{4}\s?[A-Z]{2}\b""")
    private val phoneNumberRegex = Regex("""\b(0|\+31)[\s-]?\d{2,3}[\s-]?\d{6,7}\b""")
    private val urlOrEmailRegex = Regex("""[\w.-]+@[\w.-]+|https?://|www\.[\w.-]+""")

    private val trailingPriceRegex = Regex("""(-?\d{1,4}[.,]\d{2})\s*$""")

    fun parse(rawText: String): List<ReceiptLineItem> =
        rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot(::looksLikeNoise)
            .mapNotNull(::toLineItem)

    private fun looksLikeNoise(line: String): Boolean {
        val lower = line.lowercase()
        return noiseKeywords.any { keyword -> lower.contains(keyword) } ||
            percentOnlyRegex.containsMatchIn(line) ||
            postalCodeRegex.containsMatchIn(line) ||
            phoneNumberRegex.containsMatchIn(line) ||
            urlOrEmailRegex.containsMatchIn(lower)
    }

    private fun toLineItem(line: String): ReceiptLineItem? {
        // Real product lines print "name .... €X,XX" — requiring that trailing price is the
        // single biggest filter against store/address/legal lines, which almost never end
        // in one. This trades away a few genuine lines OCR mangled the price on, in favor of
        // not pulling in noise (see the class doc).
        val priceMatch = trailingPriceRegex.find(line) ?: return null
        val name = line.substring(0, priceMatch.range.first).trim().trimEnd('-', '*', 'x', 'X', ' ').trim()

        // A "name" that's only digits/symbols (a barcode, a quantity, a date) isn't a product.
        if (name.length < 2 || name.none { it.isLetter() }) return null
        // A discount/statiegeld-terug row prints as a negative amount even once past the
        // keyword filters above (e.g. an unrecognized loyalty scheme's own wording).
        if (priceMatch.value.trim().startsWith("-")) return null

        return ReceiptLineItem(name = name, price = priceMatch.value.trim())
    }
}
