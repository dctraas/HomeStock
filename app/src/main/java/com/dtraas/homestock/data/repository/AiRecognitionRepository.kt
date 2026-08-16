package com.dtraas.homestock.data.repository

import android.util.Base64
import com.dtraas.homestock.data.model.Category
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.tasks.await

/** One name/category guess for a photographed product, with the model's own confidence. */
data class RecognizedProductCandidate(
    val name: String,
    val category: Category,
    val confidencePercent: Int,
)

sealed interface RecognizeProductResult {
    data class Success(val candidates: List<RecognizedProductCandidate>) : RecognizeProductResult

    /** Server re-checked and this household isn't premium (e.g. subscription lapsed mid-session). */
    data object PremiumRequired : RecognizeProductResult
    data object NoConnection : RecognizeProductResult

    /** Call succeeded but returned nothing usable, or failed for any other reason. */
    data object Failed : RecognizeProductResult
}

sealed interface RecognizeExpirationDateResult {
    /** [epochMillis] is UTC midnight of the recognized date — the same convention
     *  ProductDetailScreen's own date picker/[InventoryRepository] use for expirationDate. */
    data class Success(val epochMillis: Long, val confidencePercent: Int) : RecognizeExpirationDateResult

    /** Call succeeded but the model couldn't find a legible date in the photo. */
    data object NotFound : RecognizeExpirationDateResult

    /** Server re-checked and this household isn't premium (e.g. subscription lapsed mid-session). */
    data object PremiumRequired : RecognizeExpirationDateResult
    data object NoConnection : RecognizeExpirationDateResult

    /** Call failed for any other reason (network hiccup, malformed response, ...). */
    data object Failed : RecognizeExpirationDateResult
}

/**
 * Client for the `recognizeProduct` Cloud Function (see `functions/src/index.ts`), which sends
 * a photo to Claude Haiku 4.5 and returns up to 3 product name/category candidates. This is a
 * premium feature — the Cloud Function itself re-derives premium status from Firestore and
 * rejects the call (surfaced here as [RecognizeProductResult.PremiumRequired]) rather than
 * trusting the client, so gating this only in the UI (see MoreScreen/ScanScreen) wouldn't be
 * enough on its own.
 *
 * The Anthropic API key never reaches this app — it lives only in the Cloud Function's Secret
 * Manager config, which is the whole reason this goes through a backend instead of calling
 * the Claude API directly from the device.
 */
class AiRecognitionRepository(
    private val functions: FirebaseFunctions,
    private val householdSession: HouseholdSession,
) {
    suspend fun recognize(jpegBytes: ByteArray): RecognizeProductResult {
        val householdId = householdSession.householdId.value ?: return RecognizeProductResult.Failed
        val requestData = hashMapOf(
            "imageBase64" to Base64.encodeToString(jpegBytes, Base64.NO_WRAP),
            "mimeType" to "image/jpeg",
            "householdId" to householdId,
            "locale" to Locale.getDefault().language,
        )

        return try {
            val result = functions.getHttpsCallable("recognizeProduct").call(requestData).await()
            // Explicit getData() rather than the result.data property-syntax shorthand — on
            // some Firebase SDK versions Kotlin resolves that shorthand against
            // HttpsCallableResult's private backing field instead of the public getter,
            // failing to compile ("val data: Any? is private").
            val candidates = parseCandidates(result.getData())
            if (candidates.isEmpty()) RecognizeProductResult.Failed else RecognizeProductResult.Success(candidates)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> RecognizeProductResult.PremiumRequired
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                -> RecognizeProductResult.NoConnection
                else -> RecognizeProductResult.Failed
            }
        } catch (e: IOException) {
            RecognizeProductResult.NoConnection
        } catch (e: Exception) {
            RecognizeProductResult.Failed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCandidates(rawResponse: Any?): List<RecognizedProductCandidate> {
        val response = rawResponse as? Map<String, Any?> ?: return emptyList()
        val rawCandidates = response["candidates"] as? List<Any?> ?: return emptyList()
        return rawCandidates.mapNotNull { entry ->
            val candidate = entry as? Map<String, Any?> ?: return@mapNotNull null
            val name = (candidate["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            RecognizedProductCandidate(
                name = name,
                category = Category.fromStorageKey(candidate["category"] as? String),
                confidencePercent = (candidate["confidence"] as? Number)?.toInt()?.coerceIn(0, 100) ?: 0,
            )
        }
    }

    /** Client for the `recognizeExpirationDate` Cloud Function — see this class's doc for why
     *  the Anthropic call itself lives server-side. Used by ProductDetailScreen's "THT-datum
     *  scannen" camera. */
    suspend fun recognizeExpirationDate(jpegBytes: ByteArray): RecognizeExpirationDateResult {
        val householdId = householdSession.householdId.value ?: return RecognizeExpirationDateResult.Failed
        val requestData = hashMapOf(
            "imageBase64" to Base64.encodeToString(jpegBytes, Base64.NO_WRAP),
            "mimeType" to "image/jpeg",
            "householdId" to householdId,
            "locale" to Locale.getDefault().language,
        )

        return try {
            val result = functions.getHttpsCallable("recognizeExpirationDate").call(requestData).await()
            parseExpirationDateResult(result.getData())
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> RecognizeExpirationDateResult.PremiumRequired
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                -> RecognizeExpirationDateResult.NoConnection
                else -> RecognizeExpirationDateResult.Failed
            }
        } catch (e: IOException) {
            RecognizeExpirationDateResult.NoConnection
        } catch (e: Exception) {
            RecognizeExpirationDateResult.Failed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseExpirationDateResult(rawResponse: Any?): RecognizeExpirationDateResult {
        val response = rawResponse as? Map<String, Any?> ?: return RecognizeExpirationDateResult.Failed
        val dateIso = (response["dateIso"] as? String)?.trim()
        if (dateIso.isNullOrEmpty()) return RecognizeExpirationDateResult.NotFound
        val confidencePercent = (response["confidence"] as? Number)?.toInt()?.coerceIn(0, 100) ?: 0
        val epochMillis = try {
            LocalDate.parse(dateIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            return RecognizeExpirationDateResult.NotFound
        }
        return RecognizeExpirationDateResult.Success(epochMillis, confidencePercent)
    }
}
