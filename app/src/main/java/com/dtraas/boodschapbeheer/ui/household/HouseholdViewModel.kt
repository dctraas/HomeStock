package com.dtraas.boodschapbeheer.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.data.repository.HouseholdNotFoundException
import com.dtraas.boodschapbeheer.data.repository.HouseholdRepository
import com.dtraas.boodschapbeheer.data.repository.HouseholdSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HouseholdMode { CHOOSE, CREATE, JOIN }

data class HouseholdUiState(
    val mode: HouseholdMode = HouseholdMode.CHOOSE,
    val isLoading: Boolean = false,
    /** Set for errors we already have a specific, localized message for (e.g. "code not found"). */
    val errorMessage: String? = null,
    /** Set for anything else (network failure, unexpected exception) — the UI shows a generic string for these. */
    val hasGenericError: Boolean = false,
    val createdCode: String? = null,
    val joinCodeInput: String = "",
)

class HouseholdViewModel(
    private val householdRepository: HouseholdRepository,
    private val householdSession: HouseholdSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HouseholdUiState())
    val uiState: StateFlow<HouseholdUiState> = _uiState

    fun selectCreate() {
        _uiState.update { it.copy(mode = HouseholdMode.CREATE, isLoading = true, errorMessage = null, hasGenericError = false) }
        viewModelScope.launch {
            householdRepository.createHousehold()
                .onSuccess { code -> _uiState.update { it.copy(isLoading = false, createdCode = code) } }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false).withError(error) } }
        }
    }

    fun selectJoin() {
        _uiState.update { it.copy(mode = HouseholdMode.JOIN, errorMessage = null, hasGenericError = false) }
    }

    fun back() {
        _uiState.update { HouseholdUiState() }
    }

    fun onJoinCodeChange(value: String) {
        _uiState.update { it.copy(joinCodeInput = value, errorMessage = null, hasGenericError = false) }
    }

    /** Called once the user has shared/noted a freshly created household's code. */
    fun confirmCreatedHousehold() {
        val code = _uiState.value.createdCode ?: return
        householdSession.setHousehold(code)
    }

    fun joinHousehold() {
        val code = _uiState.value.joinCodeInput
        if (code.length != HouseholdRepository.CODE_LENGTH) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, hasGenericError = false) }
        viewModelScope.launch {
            householdRepository.joinHousehold(code)
                .onSuccess { joinedCode -> householdSession.setHousehold(joinedCode) }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false).withError(error) } }
        }
    }

    /**
     * [HouseholdNotFoundException] already carries a specific, localized message; anything
     * else (network failure, unexpected Firestore/Auth error) doesn't, so the UI falls back
     * to a generic translated string rather than the raw exception text.
     */
    private fun HouseholdUiState.withError(error: Throwable): HouseholdUiState =
        if (error is HouseholdNotFoundException) {
            copy(errorMessage = error.message, hasGenericError = false)
        } else {
            copy(errorMessage = null, hasGenericError = true)
        }
}
