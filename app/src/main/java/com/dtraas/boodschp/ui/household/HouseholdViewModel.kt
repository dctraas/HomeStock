package com.dtraas.boodschp.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.repository.HouseholdRepository
import com.dtraas.boodschp.data.repository.HouseholdSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HouseholdMode { CHOOSE, CREATE, JOIN }

data class HouseholdUiState(
    val mode: HouseholdMode = HouseholdMode.CHOOSE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
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
        _uiState.update { it.copy(mode = HouseholdMode.CREATE, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            householdRepository.createHousehold()
                .onSuccess { code -> _uiState.update { it.copy(isLoading = false, createdCode = code) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Er ging iets mis")
                    }
                }
        }
    }

    fun selectJoin() {
        _uiState.update { it.copy(mode = HouseholdMode.JOIN, errorMessage = null) }
    }

    fun back() {
        _uiState.update { HouseholdUiState() }
    }

    fun onJoinCodeChange(value: String) {
        _uiState.update { it.copy(joinCodeInput = value, errorMessage = null) }
    }

    /** Called once the user has shared/noted a freshly created household's code. */
    fun confirmCreatedHousehold() {
        val code = _uiState.value.createdCode ?: return
        householdSession.setHousehold(code)
    }

    fun joinHousehold() {
        val code = _uiState.value.joinCodeInput
        if (code.isBlank()) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            householdRepository.joinHousehold(code)
                .onSuccess { joinedCode -> householdSession.setHousehold(joinedCode) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Er ging iets mis")
                    }
                }
        }
    }
}
