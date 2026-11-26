package com.scrollguard.app.presentation.strictmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.domain.repository.UsageRepository
import com.scrollguard.app.security.PinActionResult
import com.scrollguard.app.security.PinManager
import com.scrollguard.app.security.PinVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StrictModeUiState(
    val strictModeState: StrictModeState = StrictModeState(),
    val focusRemainingSeconds: Long = 0L,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val generatedRecoveryCode: String? = null,
    val showPinDialog: Boolean = false,
    val pinError: String? = null
)

@HiltViewModel
class StrictModeViewModel @Inject constructor(
    private val preferences: ScrollGuardPreferences,
    private val pinManager: PinManager,
    private val usageRepository: UsageRepository
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val generatedRecoveryCode = MutableStateFlow<String?>(null)
    private val showPinDialog = MutableStateFlow(false)
    private val pinError = MutableStateFlow<String?>(null)

    private var pendingProtectedAction: (suspend () -> Unit)? = null
    private var activeSessionStartMs: Long = 0L

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<StrictModeUiState> = combine(
        preferences.strictModeState,
        ticker,
        statusMessage,
        errorMessage,
        generatedRecoveryCode,
        showPinDialog,
        pinError
    ) { values ->
        val strict = values[0] as StrictModeState
        val nowMs = values[1] as Long
        val status = values[2] as String?
        val error = values[3] as String?
        val recovery = values[4] as String?
        val pinVisible = values[5] as Boolean
        val pError = values[6] as String?

        val remainingSec = if (strict.focusSessionActive && strict.focusSessionEndEpochMs > nowMs) {
            (strict.focusSessionEndEpochMs - nowMs) / 1000L
        } else {
            0L
        }

        StrictModeUiState(
            strictModeState = strict,
            focusRemainingSeconds = remainingSec,
            statusMessage = status,
            errorMessage = error,
            generatedRecoveryCode = recovery,
            showPinDialog = pinVisible,
            pinError = pError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StrictModeUiState()
    )

    fun createPin(pin: String, confirmation: String) {
        viewModelScope.launch {
            clearMessages()
            when (val res = pinManager.createPin(pin.toCharArray(), confirmation.toCharArray())) {
                is PinActionResult.Success -> {
                    generatedRecoveryCode.value = res.recoveryCode
                    statusMessage.value = "PIN created securely. Save your recovery key below."
                }
                is PinActionResult.Error -> {
                    errorMessage.value = res.message
                }
            }
        }
    }

    fun toggleStrictMode(enable: Boolean) {
        clearMessages()
        val current = uiState.value.strictModeState
        if (enable && !current.hasPinSet) {
            errorMessage.value = "Create a PIN first before enabling Strict Mode."
            return
        }
        if (!enable && current.hasPinSet) {
            pendingProtectedAction = {
                preferences.setStrictModeEnabled(false)
                statusMessage.value = "Strict Mode disabled. Normal Mode active."
            }
            pinError.value = null
            showPinDialog.value = true
        } else {
            viewModelScope.launch {
                preferences.setStrictModeEnabled(enable)
                statusMessage.value = if (enable) "Strict Mode enabled." else "Strict Mode disabled."
            }
        }
    }

    fun setFocusDurationMinutes(minutes: Int) {
        if (uiState.value.strictModeState.isSettingsLocked()) {
            pendingProtectedAction = {
                preferences.setFocusSessionDurationMinutes(minutes)
            }
            pinError.value = null
            showPinDialog.value = true
        } else {
            viewModelScope.launch {
                preferences.setFocusSessionDurationMinutes(minutes)
            }
        }
    }

    fun startFocusSession() {
        val durationMin = uiState.value.strictModeState.focusSessionDurationMinutes
        viewModelScope.launch {
            clearMessages()
            val nowMs = System.currentTimeMillis()
            activeSessionStartMs = nowMs
            preferences.startFocusSession(
                durationMinutes = durationMin,
                endEpochMs = nowMs + durationMin * 60_000L
            )
            statusMessage.value = "Focus session started ($durationMin min)."
        }
    }

    fun endFocusSession() {
        clearMessages()
        val current = uiState.value.strictModeState
        val action: suspend () -> Unit = {
            val nowMs = System.currentTimeMillis()
            val elapsedMs = if (activeSessionStartMs > 0L) {
                (nowMs - activeSessionStartMs).coerceAtLeast(0L)
            } else {
                val totalMs = current.focusSessionDurationMinutes * 60_000L
                val remainingMs = (current.focusSessionEndEpochMs - nowMs).coerceAtLeast(0L)
                (totalMs - remainingMs).coerceAtLeast(0L)
            }
            if (elapsedMs > 0L) {
                usageRepository.addFocusSessionDuration(LocalDate.now().toString(), elapsedMs)
            }
            activeSessionStartMs = 0L
            preferences.endFocusSession()
            statusMessage.value = "Focus session ended."
        }

        if (current.isSettingsLocked()) {
            pendingProtectedAction = action
            pinError.value = null
            showPinDialog.value = true
        } else {
            viewModelScope.launch { action() }
        }
    }

    fun setDailyTempUnlockLimit(limit: Int) {
        val action: suspend () -> Unit = {
            preferences.setDailyTempUnlockLimit(limit)
        }
        if (uiState.value.strictModeState.isSettingsLocked()) {
            pendingProtectedAction = action
            pinError.value = null
            showPinDialog.value = true
        } else {
            viewModelScope.launch { action() }
        }
    }

    fun recoverWithKey(recoveryKey: String) {
        viewModelScope.launch {
            clearMessages()
            when (val res = pinManager.disablePinAndStrictModeWithRecovery(recoveryKey.toCharArray())) {
                is PinActionResult.Success -> {
                    preferences.endFocusSession()
                    statusMessage.value = "Emergency recovery succeeded. Strict Mode and PIN cleared."
                }
                is PinActionResult.Error -> {
                    errorMessage.value = res.message
                }
            }
        }
    }

    fun verifyPinForPendingAction(pin: String) {
        viewModelScope.launch {
            when (val res = pinManager.verifyPin(pin.toCharArray())) {
                PinVerificationResult.Verified, PinVerificationResult.NotSet -> {
                    showPinDialog.value = false
                    pinError.value = null
                    pendingProtectedAction?.invoke()
                    pendingProtectedAction = null
                }
                is PinVerificationResult.Invalid -> {
                    pinError.value = "Incorrect PIN. ${res.attemptsRemainingBeforeDelay} attempt(s) before delay."
                }
                is PinVerificationResult.LockedOut -> {
                    pinError.value = "Too many attempts. Wait ${res.remainingSeconds}s."
                }
            }
        }
    }

    fun dismissPinDialog() {
        showPinDialog.value = false
        pinError.value = null
        pendingProtectedAction = null
    }

    fun clearRecoveryBanner() {
        generatedRecoveryCode.value = null
    }

    private fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }
}
