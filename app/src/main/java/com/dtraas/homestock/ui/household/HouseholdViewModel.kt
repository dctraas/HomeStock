package com.dtraas.homestock.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.repository.DeviceProfile
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.HouseholdNotFoundException
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.data.repository.HouseholdSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HouseholdMode { PROFILE, CHOOSE, CREATE, JOIN }

data class HouseholdUiState(
    val mode: HouseholdMode = HouseholdMode.CHOOSE,
    val isLoading: Boolean = false,
    /** Set for errors we already have a specific, localized message for (e.g. "code not found"). */
    val errorMessage: String? = null,
    /** Set for anything else (network failure, unexpected exception) — the UI shows a generic string for these. */
    val hasGenericError: Boolean = false,
    /** The household exists and the code is valid, but it's already at the free-tier member limit. */
    val householdFull: Boolean = false,
    val createdCode: String? = null,
    val joinCodeInput: String = "",
    val householdNameInput: String = "",
    /** True once "Doorgaan" is tapped on the household-name step — switches CREATE from the name form to loading/result. */
    val hasSubmittedHouseholdName: Boolean = false,
)

class HouseholdViewModel(
    private val householdRepository: HouseholdRepository,
    private val householdSession: HouseholdSession,
    private val householdMembersRepository: HouseholdMembersRepository,
    deviceProfile: DeviceProfile,
) : ViewModel() {

    // The profile step (name + optional photo) only makes sense the very first time someone
    // opens the app — a device that already has a name (e.g. after leaving a household to
    // join another one) skips straight to CHOOSE instead of asking again.
    private val _uiState = MutableStateFlow(
        HouseholdUiState(mode = if (deviceProfile.displayName.value == null) HouseholdMode.PROFILE else HouseholdMode.CHOOSE),
    )
    val uiState: StateFlow<HouseholdUiState> = _uiState

    fun confirmProfile() {
        _uiState.update { it.copy(mode = HouseholdMode.CHOOSE) }
    }

    fun selectCreate() {
        _uiState.update {
            it.copy(
                mode = HouseholdMode.CREATE,
                errorMessage = null,
                hasGenericError = false,
                householdNameInput = "",
                hasSubmittedHouseholdName = false,
                createdCode = null,
            )
        }
    }

    fun onHouseholdNameChange(value: String) {
        if (value.length <= HouseholdRepository.HOUSEHOLD_NAME_MAX_LENGTH) {
            _uiState.update { it.copy(householdNameInput = value) }
        }
    }

    /** Called once a household name has been entered — actually creates the household. */
    fun submitHouseholdName() {
        val name = _uiState.value.householdNameInput.trim()
        if (name.isEmpty()) return
        _uiState.update {
            it.copy(isLoading = true, hasSubmittedHouseholdName = true, errorMessage = null, hasGenericError = false)
        }
        viewModelScope.launch {
            householdRepository.createHousehold(name)
                .onSuccess { code -> _uiState.update { it.copy(isLoading = false, createdCode = code) } }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false).withError(error) } }
        }
    }

    fun selectJoin() {
        _uiState.update { it.copy(mode = HouseholdMode.JOIN, errorMessage = null, hasGenericError = false, householdFull = false) }
    }

    fun back() {
        _uiState.update { HouseholdUiState(mode = HouseholdMode.CHOOSE) }
    }

    fun onJoinCodeChange(value: String) {
        _uiState.update { it.copy(joinCodeInput = value, errorMessage = null, hasGenericError = false, householdFull = false) }
    }

    /** Called once the user has shared/noted a freshly created household's code. */
    fun confirmCreatedHousehold() {
        val code = _uiState.value.createdCode ?: return
        // A freshly created household only ever has this one device so far — no need to
        // check the free-tier limit, just register.
        viewModelScope.launch { householdMembersRepository.registerCurrentDevice(code) }
        householdSession.setHousehold(code)
    }

    fun joinHousehold() {
        val code = _uiState.value.joinCodeInput
        if (code.length != HouseholdRepository.CODE_LENGTH) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, hasGenericError = false, householdFull = false) }
        viewModelScope.launch {
            householdRepository.joinHousehold(code)
                .onSuccess { joinedCode ->
                    if (householdMembersRepository.canJoin(joinedCode)) {
                        householdMembersRepository.registerCurrentDevice(joinedCode)
                        householdSession.setHousehold(joinedCode)
                    } else {
                        _uiState.update { it.copy(isLoading = false, householdFull = true) }
                    }
                }
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
