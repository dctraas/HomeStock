package com.dtraas.homestock.data.repository

import android.util.Base64
import com.dtraas.homestock.data.model.Category
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
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
            val candidates = parseCandidates(result.data)
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
}
