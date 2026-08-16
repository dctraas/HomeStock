package com.dtraas.homestock.data.repository

import android.content.Context
import com.dtraas.homestock.work.ReceiptQueueWorker
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One receipt photo waiting to be processed — see [ReceiptQueueRepository]. */
data class QueuedReceipt(val id: String, val capturedAtMillis: Long)

/** Outcome of one [ReceiptQueueRepository.processQueue] run, for [ReceiptQueueWorker]'s notification. */
data class ReceiptQueueProcessResult(val processedReceipts: Int, val addedItems: Int)

/**
 * Local, on-device queue for receipt photos captured while offline — "offline-first bonnetje
 * scannen": [com.dtraas.homestock.ui.receiptscan.ReceiptScanViewModel] enqueues a photo here
 * instead of showing a dead-end "Geen verbinding" error whenever `recognizeReceipt` fails with
 * no connection, and [ReceiptQueueWorker] (a network-constrained WorkManager job, scheduled by
 * [enqueue]) drains the queue automatically once connectivity returns.
 *
 * Each photo is written to [queueDir] as a plain JPEG file; the pending list itself (id +
 * capture time) lives in a small hand-rolled JSON array in SharedPreferences. This app has no
 * local database — Firestore is the only persistence layer everywhere else — so that's the
 * lightest option that still survives an app restart or process death, rather than pulling in
 * Room back in just for this one queue.
 */
class ReceiptQueueRepository(
    context: Context,
    private val receiptRecognitionRepository: ReceiptRecognitionRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val queueDir: File by lazy { File(appContext.filesDir, "receipt_queue").apply { mkdirs() } }

    private val _pendingCount = MutableStateFlow(readEntries().size)
    val pendingCount: StateFlow<Int> = _pendingCount

    /** Saves [jpegBytes] to the queue and arms [ReceiptQueueWorker] to process it once the
     *  device is back online (see that worker's `schedule`, which WorkManager itself keeps
     *  waiting on regardless of app process lifetime). */
    fun enqueue(jpegBytes: ByteArray) {
        val id = UUID.randomUUID().toString()
        File(queueDir, fileNameFor(id)).writeBytes(jpegBytes)
        val entries = readEntries() + QueuedReceipt(id, System.currentTimeMillis())
        writeEntries(entries)
        _pendingCount.value = entries.size
        ReceiptQueueWorker.schedule(appContext)
    }

    /**
     * Processes every currently queued receipt: recognizes it, auto-saves every readable line
     * straight to inventory (mirrors [com.dtraas.homestock.ui.receiptscan.ReceiptScanViewModel.confirmAndSave]'s
     * save step — there's no one present to review/edit the read-off names and quantities the
     * way the interactive scan flow offers, so this takes them at face value), then removes it
     * from the queue.
     *
     * A receipt that still can't reach the network is kept queued for the next run. One that
     * fails for any other reason (premium lapsed, an unreadable photo, ...) is dropped instead —
     * retrying the exact same photo forever wouldn't help, and letting it block every later
     * queued receipt behind it would be worse than losing that one entry.
     */
    suspend fun processQueue(): ReceiptQueueProcessResult {
        val entries = readEntries()
        if (entries.isEmpty()) return ReceiptQueueProcessResult(0, 0)

        var processedReceipts = 0
        var addedItems = 0
        val remaining = mutableListOf<QueuedReceipt>()

        for (entry in entries) {
            val file = File(queueDir, fileNameFor(entry.id))
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes == null) continue // the photo itself is gone — nothing left to retry

            when (val result = receiptRecognitionRepository.recognize(bytes)) {
                is RecognizeReceiptResult.Success -> {
                    addedItems += saveItems(result.items)
                    processedReceipts++
                    file.delete()
                }
                RecognizeReceiptResult.NoConnection -> remaining += entry
                RecognizeReceiptResult.PremiumRequired, RecognizeReceiptResult.Failed -> file.delete()
            }
        }

        writeEntries(remaining)
        _pendingCount.value = remaining.size
        return ReceiptQueueProcessResult(processedReceipts, addedItems)
    }

    /** Same barcode-matching/synthesizing and per-unit price conventions as
     *  ReceiptScanViewModel.confirmAndSave() — see that function's doc. */
    private suspend fun saveItems(items: List<RecognizedReceiptItem>): Int {
        var saved = 0
        for (item in items) {
            if (item.name.isBlank()) continue
            val matchedBarcode = productRepository.searchByName(item.name).getOrNull()?.firstOrNull()?.barcode
            val barcode: String
            if (matchedBarcode != null) {
                productRepository.updateCategory(matchedBarcode, item.category)
                inventoryRepository.recordScan(matchedBarcode, item.quantity, item.category)
                barcode = matchedBarcode
            } else {
                val syntheticBarcode = "receipt-${UUID.randomUUID()}"
                productRepository.saveManualProduct(syntheticBarcode, item.name, item.category)
                inventoryRepository.recordScan(syntheticBarcode, item.quantity, item.category)
                barcode = syntheticBarcode
            }
            item.price?.let { totalPrice ->
                productRepository.updateLastPrice(barcode, totalPrice / item.quantity.coerceAtLeast(1))
            }
            saved++
        }
        return saved
    }

    private fun fileNameFor(id: String) = "$id.jpg"

    private fun readEntries(): List<QueuedReceipt> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                QueuedReceipt(id, obj.optLong("capturedAtMillis"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeEntries(entries: List<QueuedReceipt>) {
        val array = JSONArray()
        entries.forEach { entry -> array.put(JSONObject().put("id", entry.id).put("capturedAtMillis", entry.capturedAtMillis)) }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "receipt_queue"
        const val KEY_ENTRIES = "entries"
    }
}
