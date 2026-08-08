package com.dtraas.homestock.ui.airecognize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.AiRecognitionRepository
import com.dtraas.homestock.data.repository.ProductRepository
import com.dtraas.homestock.data.repository.RecognizeProductResult
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AiRecognizeStep {
    /** Camera preview, waiting for a photo. */
    data object Capturing : AiRecognizeStep

    /**
     * Photo taken, waiting on [AiRecognitionRepository.recognize] — a real network round-trip
     * to the Cloud Function (which itself calls Claude), unlike the old on-device ML Kit pass
     * this replaced, so (unlike before) this now needs its own visible loading state.
     */
    data object Analyzing : AiRecognizeStep

    data class Failed(val reason: FailReason) : AiRecognizeStep

    /**
     * One or more candidates came back — [suggestedName] and [category] start out set from the
     * top-confidence one ([candidates].first()) but are fully editable before confirming.
     * Surfacing all of [candidates] (not just the top guess) matters in practice — the single
     * best match is sometimes a worse fit than #2 or #3, and letting the user tap whichever one
     * actually matches beats forcing a single guess into the name field with no alternative.
     */
    data class Recognized(
        /** Label text to confidence percent, sorted by confidence descending. */
        val candidates: List<Pair<String, Int>>,
        val suggestedName: String,
        val category: Category,
        val confidencePercent: Int,
    ) : AiRecognizeStep
}

enum class FailReason {
    /** Camera capture itself failed (device/CameraX issue), before any network call. */
    CAPTURE,
    NO_CONNECTION,

    /** Server re-checked and this household isn't (or is no longer) premium. */
    PREMIUM_REQUIRED,
    UNKNOWN,
}

class AiRecognizeViewModel(
    private val productRepository: ProductRepository,
    private val aiRecognitionRepository: AiRecognitionRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<AiRecognizeStep>(AiRecognizeStep.Capturing)
    val step: StateFlow<AiRecognizeStep> = _step

    /** Emits the synthetic barcode once a suggestion is confirmed and cached — the screen navigates to the existing ScanResultScreen with it. */
    private val _confirmed = MutableSharedFlow<String>()
    val confirmed: SharedFlow<String> = _confirmed

    // The Recognized step's own candidates list is (name, confidence) pairs to keep the chip UI
    // simple; the category that came back alongside each candidate is looked up from here when
    // a chip is tapped (selectCandidate) rather than carried in the step's own candidate list.
    private var categoriesByCandidateName: Map<String, Category> = emptyMap()

    fun onPhotoCaptured(jpegBytes: ByteArray) {
        _step.value = AiRecognizeStep.Analyzing
        viewModelScope.launch {
            when (val result = aiRecognitionRepository.recognize(jpegBytes)) {
                is RecognizeProductResult.Success -> {
                    categoriesByCandidateName = result.candidates.associate { it.name to it.category }
                    val top = result.candidates.first()
                    _step.value = AiRecognizeStep.Recognized(
                        candidates = result.candidates.map { it.name to it.confidencePercent },
                        suggestedName = top.name,
                        category = top.category,
                        confidencePercent = top.confidencePercent,
                    )
                }
                RecognizeProductResult.PremiumRequired -> _step.value = AiRecognizeStep.Failed(FailReason.PREMIUM_REQUIRED)
                RecognizeProductResult.NoConnection -> _step.value = AiRecognizeStep.Failed(FailReason.NO_CONNECTION)
                RecognizeProductResult.Failed -> _step.value = AiRecognizeStep.Failed(FailReason.UNKNOWN)
            }
        }
    }

    fun onCaptureFailed() {
        _step.value = AiRecognizeStep.Failed(FailReason.CAPTURE)
    }

    /** User tapped a different candidate than the top guess — swap name, category and confidence to match it. */
    fun selectCandidate(label: String, confidencePercent: Int) {
        (_step.value as? AiRecognizeStep.Recognized)?.let {
            _step.value = it.copy(
                suggestedName = label,
                category = categoriesByCandidateName[label] ?: it.category,
                confidencePercent = confidencePercent,
            )
        }
    }

    fun retake() {
        _step.value = AiRecognizeStep.Capturing
    }

    fun onNameChange(name: String) {
        (_step.value as? AiRecognizeStep.Recognized)?.let { _step.value = it.copy(suggestedName = name) }
    }

    fun onCategoryChange(category: Category) {
        (_step.value as? AiRecognizeStep.Recognized)?.let { _step.value = it.copy(category = category) }
    }

    /**
     * Caches the confirmed suggestion under a freshly generated synthetic barcode (this
     * product has no real one — it was never scanned) and reports it for navigation.
     * ScanResultScreen's own [ProductRepository.getOrFetchProduct] checks the cache before
     * ever touching the network, so it picks this straight up with no extra plumbing needed
     * there — from that screen on, it's just a normal "add to inventory" confirm flow.
     */
    fun confirm() {
        val recognized = _step.value as? AiRecognizeStep.Recognized ?: return
        val name = recognized.suggestedName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val barcode = "ai-${UUID.randomUUID()}"
            productRepository.saveManualProduct(barcode, name, recognized.category)
            _confirmed.emit(barcode)
        }
    }
}
