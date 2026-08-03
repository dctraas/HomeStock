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
 *
 * Weighed items (fruit, vegetables) commonly print across *two* lines — the
 * product name with no price, then a separate "1,39 €/kg  €1,58" unit-price
 * breakdown line that does end in a price. [parse] tracks the most recent
 * name-only line as a pending name and, when a price line's own text doesn't
 * look like a real product name (see [looksLikeRealName] — this is symbol-
 * tolerant on purpose, since OCR frequently mangles "€" and "/" on exactly
 * these lines), pairs that price with the pending name instead.
 */
object ReceiptParser {

    private val noiseKeywords = listOf(
        // Totals & VAT
        "totaal", "subtotaal", "eindtotaal", "te betalen", "btw", "excl", "incl",
        // Payment / terminal
        "statiegeld", "kassabon", "kassa", "kassier", "transactie", "pinnen", "pin ",
        "contant", "retour", "terminal", "creditcard", "maestro", "mastercard", "visa",
        "leesmethode", "kaartnummer", "kaart nr", "autorisatie", "referentie", "goedgekeurd",
        "akkoord", "wisselgeld", "gepast", "betaalwijze",
        // Loyalty / discounts
        "korting", "bonus", "bonuskaart", "spaarzegel", "spaarpunt", "voordeel", "airmiles",
        // Store/legal boilerplate
        "artikelen", "bedankt", "tot ziens", "welkom", "fijne dag", "prettige dag",
        "kvk", "iban", "bon nr", "bonnummer", "filiaal", "vestiging", "openingstijden",
        "klantenservice", "handelsregister", "btw-nummer", "www.", "@", "tel:", "telefoon",
        // Date/time labels (the values themselves are caught by the numeric-line filter below)
        "datum", "tijd",
        // Opening-hours day names — these lines carry a clock time like "8:00 tot 20:00"
        // that OCR can misread as a decimal price (colon read as comma), so the day name
        // is the more reliable signal to filter on.
        "maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag",
    )

    // Product lines end in a price; a bare percentage (VAT-rate rows like "9% BTW") does
    // too often enough to need its own check even without a "btw" keyword on the line.
    private val percentOnlyRegex = Regex("""^\s*\d{1,2}\s*%""")

    // Dutch postal code ("1234 AB"), a phone number, or a URL/email — none of these are
    // product names, and they can slip past the keyword list on a store's letterhead line.
    private val postalCodeRegex = Regex("""\b\d{4}\s?[A-Z]{2}\b""")
    private val phoneNumberRegex = Regex("""\b(0|\+31)[\s-]?\d{2,3}[\s-]?\d{6,7}\b""")
    private val urlOrEmailRegex = Regex("""[\w.-]+@[\w.-]+|https?://|www\.[\w.-]+""")
    // A clock time ("8:00", "18:05") — receipts print these in timestamps and opening
    // hours, never in a product name, and it survives even when a price elsewhere on the
    // same line got its own colon misread as a comma.
    private val timeOfDayRegex = Regex("""\b\d{1,2}:\d{2}\b""")

    private val trailingPriceRegex = Regex("""(-?\d{1,4}[.,]\d{2})\s*$""")

    // A run of 3+ letters is what separates an actual word ("Bananen", "kiloprijs") from
    // number/unit noise ("1,39", "kg", "x"): unit abbreviations are all 1-2 letters. Used
    // instead of matching specific symbols like "€/kg" directly, since OCR is unreliable
    // about preserving exactly those symbols on the line that most needs them recognized.
    private val wordRegex = Regex("""\p{L}{3,}""")

    // Explicit phrases that read as a real word (3+ letters) but are still pricing text,
    // not a product name — kept as a belt-and-braces on top of [wordRegex].
    private val unitBreakdownRegex = Regex("""(?i)\b(kiloprijs|stuksprijs)\b""")

    fun parse(rawText: String): List<ReceiptLineItem> {
        val candidateLines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot(::looksLikeNoise)

        val items = mutableListOf<ReceiptLineItem>()
        var pendingName: String? = null

        for (line in candidateLines) {
            val priceMatch = trailingPriceRegex.find(line)
            if (priceMatch == null) {
                // No price on this line — remember it in case the *next* line is a
                // unit-price breakdown that belongs to it (see class doc).
                cleanName(line)?.let { pendingName = it }
                continue
            }

            val price = priceMatch.value.trim()
            if (price.startsWith("-")) {
                // A discount/statiegeld-terug row prints as a negative amount even once past
                // the keyword filters above; it never pairs with a pending name.
                pendingName = null
                continue
            }

            val localName = cleanName(line.substring(0, priceMatch.range.first))
            val name = when {
                localName != null && looksLikeRealName(localName) -> localName
                pendingName != null -> pendingName
                else -> null
            }
            pendingName = null

            if (name != null) items += ReceiptLineItem(name = name, price = price)
        }

        return items
    }

    private fun looksLikeNoise(line: String): Boolean {
        val lower = line.lowercase()
        return noiseKeywords.any { keyword -> lower.contains(keyword) } ||
            percentOnlyRegex.containsMatchIn(line) ||
            postalCodeRegex.containsMatchIn(line) ||
            phoneNumberRegex.containsMatchIn(line) ||
            urlOrEmailRegex.containsMatchIn(lower) ||
            timeOfDayRegex.containsMatchIn(line)
    }

    // A "name" that's only digits/symbols (a barcode, a quantity, a date) isn't a product.
    private fun cleanName(raw: String): String? {
        val trimmed = raw.trim().trimEnd('-', '*', 'x', 'X', ' ').trim()
        if (trimmed.length < 2 || trimmed.none { it.isLetter() }) return null
        return trimmed
    }

    private fun looksLikeRealName(name: String) =
        wordRegex.containsMatchIn(name) && !unitBreakdownRegex.containsMatchIn(name)
}
