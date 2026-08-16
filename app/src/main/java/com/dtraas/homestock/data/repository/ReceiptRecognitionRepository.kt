package com.dtraas.homestock.data.repository

import android.util.Base64
import com.dtraas.homestock.data.model.Category
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.tasks.await

/** One purchased line item read off a scanned receipt. */
data class RecognizedReceiptItem(
    val name: String,
    val category: Category,
    val quantity: Int,
    // Total price for this line as printed on the receipt (not yet divided by quantity) — null
    // when the model couldn't read a price for this line. See ReceiptScanViewModel for where
    // this becomes a per-unit "last paid" price on the product.
    val price: Double? = null,
)

sealed interface RecognizeReceiptResult {
    data class Success(val items: List<RecognizedReceiptItem>) : RecognizeReceiptResult

    /** Server re-checked and this household isn't premium (e.g. subscription lapsed mid-session). */
    data object PremiumRequired : RecognizeReceiptResult
    data object NoConnection : RecognizeReceiptResult

    /** Call succeeded but returned nothing usable, or failed for any other reason. */
    data object Failed : RecognizeReceiptResult
}

/**
 * Client for the `recognizeReceipt` Cloud Function (see `functions/src/index.ts`), which sends
 * a photo of a whole receipt to Claude Haiku 4.5 and returns every product line it can read off
 * it — replacing an earlier on-device ML Kit OCR + hand-rolled row/price parser (see git history
 * of `data/receipt/` for that version), which was fragile against the wide variety of real
 * receipt layouts. This is a premium feature — the Cloud Function itself re-derives premium
 * status from Firestore and rejects the call (surfaced here as [RecognizeReceiptResult.PremiumRequired])
 * rather than trusting the client.
 *
 * Mirrors [AiRecognitionRepository]'s shape closely on purpose — same kind of call, same
 * failure modes.
 */
class ReceiptRecognitionRepository(
    private val functions: FirebaseFunctions,
    private val householdSession: HouseholdSession,
) {
    suspend fun recognize(jpegBytes: ByteArray): RecognizeReceiptResult {
        val householdId = householdSession.householdId.value ?: return RecognizeReceiptResult.Failed
        val requestData = hashMapOf(
            "imageBase64" to Base64.encodeToString(jpegBytes, Base64.NO_WRAP),
            "mimeType" to "image/jpeg",
            "householdId" to householdId,
            "locale" to Locale.getDefault().language,
        )

        return try {
            val result = functions.getHttpsCallable("recognizeReceipt").call(requestData).await()
            val items = parseItems(result.getData())
            if (items.isEmpty()) RecognizeReceiptResult.Failed else RecognizeReceiptResult.Success(items)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> RecognizeReceiptResult.PremiumRequired
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                -> RecognizeReceiptResult.NoConnection
                else -> RecognizeReceiptResult.Failed
            }
        } catch (e: IOException) {
            RecognizeReceiptResult.NoConnection
        } catch (e: Exception) {
            RecognizeReceiptResult.Failed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseItems(rawResponse: Any?): List<RecognizedReceiptItem> {
        val response = rawResponse as? Map<String, Any?> ?: return emptyList()
        val rawItems = response["items"] as? List<Any?> ?: return emptyList()
        return rawItems.mapNotNull { entry ->
            val item = entry as? Map<String, Any?> ?: return@mapNotNull null
            val name = (item["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            RecognizedReceiptItem(
                name = name,
                category = Category.fromStorageKey(item["category"] as? String),
                quantity = (item["quantity"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
                price = (item["price"] as? Number)?.toDouble(),
            )
        }
    }
}
