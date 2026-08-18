package com.dtraas.homestock.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.repository.DeviceProfile
import com.dtraas.homestock.data.repository.HouseholdJoinResult
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
    /** The household exists and the code is valid, but it's already at the free-tier member
     *  limit — Premium households have no cap at all, so this can only happen to a
     *  non-Premium one. */
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
    /** From an invite link (see HouseholdScreen's doc) — skips straight to a pre-filled join step. */
    private val prefillJoinCode: String? = null,
) : ViewModel() {

    // The profile step (name + optional photo) only makes sense the very first time someone
    // opens the app — a device that already has a name (e.g. after leaving a household to
    // join another one) skips straight to CHOOSE instead of asking again. An invite link skips
    // CHOOSE too and lands straight on JOIN, pre-filled — someone who followed a link already
    // expressed intent to join a specific household, no need to make them pick "join" again.
    private val _uiState = MutableStateFlow(
        HouseholdUiState(
            mode = when {
                deviceProfile.displayName.value == null -> HouseholdMode.PROFILE
                prefillJoinCode != null -> HouseholdMode.JOIN
                else -> HouseholdMode.CHOOSE
            },
            joinCodeInput = if (deviceProfile.displayName.value != null) prefillJoinCode.orEmpty() else "",
        ),
    )
    val uiState: StateFlow<HouseholdUiState> = _uiState

    fun confirmProfile() {
        _uiState.update {
            if (prefillJoinCode != null) {
                it.copy(mode = HouseholdMode.JOIN, joinCodeInput = prefillJoinCode)
            } else {
                it.copy(mode = HouseholdMode.CHOOSE)
            }
        }
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
        _uiState.update {
            it.copy(mode = HouseholdMode.JOIN, errorMessage = null, hasGenericError = false, householdFull = false)
        }
    }

    fun back() {
        _uiState.update { HouseholdUiState(mode = HouseholdMode.CHOOSE) }
    }

    fun onJoinCodeChange(value: String) {
        _uiState.update {
            it.copy(joinCodeInput = value, errorMessage = null, hasGenericError = false, householdFull = false)
        }
    }

    /** Called once the user has shared/noted a freshly created household's code. */
    fun confirmCreatedHousehold() {
        val code = _uiState.value.createdCode ?: return
        // A freshly created household only ever has this one device so far — no need to
        // check the free-tier limit, just register.
        viewModelScope.launch { householdMembersRepository.registerCurrentDevice(code) }
        // Name is already in hand from the step just completed — no need to wait for a
        // round-trip read of the household document to cache it for the switcher.
        householdSession.rememberHousehold(code, _uiState.value.householdNameInput.trim())
        householdSession.setHousehold(code)
    }

    fun joinHousehold() {
        val code = _uiState.value.joinCodeInput
        if (code.length != HouseholdRepository.CODE_LENGTH) return
        _uiState.update {
            it.copy(isLoading = true, errorMessage = null, hasGenericError = false, householdFull = false)
        }
        viewModelScope.launch {
            householdRepository.joinHousehold(code)
                .onSuccess { joinedCode ->
                    when (householdMembersRepository.canJoin(joinedCode)) {
                        HouseholdJoinResult.ALLOWED -> {
                            householdMembersRepository.registerCurrentDevice(joinedCode)
                            // Name isn't known yet here (joining is by code alone) —
                            // HouseholdSettingsScreen fills it in once the household document's
                            // name has actually been read.
                            householdSession.rememberHousehold(joinedCode, name = null)
                            householdSession.setHousehold(joinedCode)
                        }
                        HouseholdJoinResult.BLOCKED_FREE_LIMIT ->
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
