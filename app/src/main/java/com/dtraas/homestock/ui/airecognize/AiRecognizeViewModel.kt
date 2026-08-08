package com.dtraas.homestock.ui.airecognize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.ProductRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AiRecognizeStep {
    /**
     * Camera preview, waiting for a photo. Capturing and running ML Kit's on-device labeler
     * on it both happen fast enough (sub-second, no network round-trip) that they're handled
     * as a local loading spinner on the capture button itself rather than a separate screen
     * state — unlike e.g. Bonnetje scannen's OCR-then-parse step, there's no slower stage
     * afterwards that would need its own "Processing" screen here.
     */
    data object Capturing : AiRecognizeStep

    /** Nothing recognizable in the photo, or the capture/labeling itself failed — same recovery either way (retake). */
    data object Failed : AiRecognizeStep

    /**
     * One or more labels came back — [suggestedName] and [category] start out set from the
     * top-confidence one ([candidates].first()) but are fully editable before confirming,
     * since ML Kit's on-device labels are generic ("Food", "Produce", "Bread") rather than a
     * specific product name. Surfacing all of [candidates] (not just the top guess) matters
     * in practice — the single best match is sometimes a worse fit than #2 or #3, and letting
     * the user tap whichever one actually matches beats forcing a single guess into the name
     * field with no alternative.
     */
    data class Recognized(
        /** Label text to confidence percent, sorted by confidence descending. */
        val candidates: List<Pair<String, Int>>,
        val suggestedName: String,
        val category: Category,
        val confidencePercent: Int,
    ) : AiRecognizeStep
}

class AiRecognizeViewModel(
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<AiRecognizeStep>(AiRecognizeStep.Capturing)
    val step: StateFlow<AiRecognizeStep> = _step

    /** Emits the synthetic barcode once a suggestion is confirmed and cached — the screen navigates to the existing ScanResultScreen with it. */
    private val _confirmed = MutableSharedFlow<String>()
    val confirmed: SharedFlow<String> = _confirmed

    /** [candidates] pre-sorted by confidence descending. */
    fun onLabelsRecognized(candidates: List<Pair<String, Int>>) {
        val (topLabel, topConfidence) = candidates.first()
        _step.value = AiRecognizeStep.Recognized(
            candidates = candidates,
            suggestedName = topLabel,
            category = suggestCategoryForLabel(topLabel),
            confidencePercent = topConfidence,
        )
    }

    /** User tapped a different candidate than the top guess — swap name, category and confidence to match it. */
    fun selectCandidate(label: String, confidencePercent: Int) {
        (_step.value as? AiRecognizeStep.Recognized)?.let {
            _step.value = it.copy(
                suggestedName = label,
                category = suggestCategoryForLabel(label),
                confidencePercent = confidencePercent,
            )
        }
    }

    fun onCaptureFailed() {
        _step.value = AiRecognizeStep.Failed
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
