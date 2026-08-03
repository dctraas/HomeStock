package com.dtraas.boodschapbeheer.data.receipt

/** One piece of OCR'd text with its vertical position on the photographed receipt. */
data class OcrLine(val text: String, val top: Float, val bottom: Float, val left: Float)

/**
 * Reconstructs visual rows from ML Kit's OCR lines using their position, instead of trusting
 * the block-then-line order ML Kit's own `Text.text` property returns. That order breaks down
 * whenever a receipt has two visually separate columns — an item-name column and a
 * right-aligned price column, common on printed receipts with a wide gap between them, or on
 * a digital receipt's own on-screen table — because ML Kit tends to group each column into its
 * own TextBlock, so the flattened text reads "every name, then every price" rather than row by
 * row. [reconstructRows] instead groups OCR lines whose vertical ranges substantially overlap
 * into one row, then orders that row's pieces left to right by their left edge — i.e. it
 * reconstructs rows the way a human eye would scan the photo, regardless of which TextBlock
 * ML Kit happened to put each piece in.
 */
object ReceiptRowReconstructor {

    fun reconstructRows(lines: List<OcrLine>): List<String> {
        val rows = mutableListOf<MutableList<OcrLine>>()

        for (line in lines.sortedBy { it.top }) {
            val row = rows.find { sameRow(it, line) }
            if (row != null) row += line else rows += mutableListOf(line)
        }

        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } }
    }

    // Two pieces belong to the same row when their vertical ranges overlap by more than half
    // the shorter piece's height — enough to tell "same printed line, different column" apart
    // from "next line down", without being so strict that a few pixels of OCR jitter splits a
    // single real row in two.
    private fun sameRow(row: List<OcrLine>, candidate: OcrLine): Boolean {
        val rowTop = row.minOf { it.top }
        val rowBottom = row.maxOf { it.bottom }
        val overlap = minOf(rowBottom, candidate.bottom) - maxOf(rowTop, candidate.top)
        val shorterHeight = minOf(rowBottom - rowTop, candidate.bottom - candidate.top)
        return shorterHeight > 0f && overlap > shorterHeight / 2f
    }
}
