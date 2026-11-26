package com.scrollguard.app.presentation.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.blocking.DetectionRuleRepository
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.domain.model.InstalledApp
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.domain.repository.FocusScheduleRepository
import com.scrollguard.app.domain.repository.InstalledAppsRepository
import com.scrollguard.app.scheduling.ScheduleAlarmScheduler
import com.scrollguard.app.scheduling.ScheduleEvaluator
import com.scrollguard.app.security.PinManager
import com.scrollguard.app.security.PinVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class ScheduleItemUi(
    val schedule: FocusSchedule,
    val isCurrentlyActive: Boolean
)

data class SchedulesUiState(
    val schedules: List<ScheduleItemUi> = emptyList(),
    val availableApps: List<InstalledApp> = emptyList(),
    val strictLocked: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinError: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: FocusScheduleRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val scheduleAlarmScheduler: ScheduleAlarmScheduler,
    private val detectionRuleRepository: DetectionRuleRepository,
    private val preferences: ScrollGuardPreferences,
    private val pinManager: PinManager
) : ViewModel() {

    private val appsFlow = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val showPinDialog = MutableStateFlow(false)
    private val pinError = MutableStateFlow<String?>(null)
    private val sessionUnlocked = MutableStateFlow(false)
    private var pendingAction: (suspend () -> Unit)? = null

    val uiState: StateFlow<SchedulesUiState> = combine(
        scheduleRepository.observeAllSchedules(),
        appsFlow,
        preferences.strictModeState,
        showPinDialog,
        pinError,
        sessionUnlocked
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val schedules = values[0] as List<FocusSchedule>
        @Suppress("UNCHECKED_CAST")
        val apps = values[1] as List<InstalledApp>
        val strict = values[2] as StrictModeState
        val pinVisible = values[3] as Boolean
        val error = values[4] as String?
        val unlocked = values[5] as Boolean

        SchedulesUiState(
            schedules = schedules.map {
                ScheduleItemUi(
                    schedule = it,
                    isCurrentlyActive = scheduleEvaluator.isScheduleActive(it)
                )
            },
            availableApps = apps,
            strictLocked = strict.isSettingsLocked() && !unlocked,
            showPinDialog = pinVisible,
            pinError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SchedulesUiState()
    )

    init {
        viewModelScope.launch {
            appsFlow.value = installedAppsRepository.getLaunchableApps()
        }
    }

    fun toggleSchedule(schedule: FocusSchedule, enabled: Boolean) {
        runProtectedAction {
            scheduleRepository.setScheduleEnabled(schedule.id, enabled)
            detectionRuleRepository.refreshNow()
            scheduleAlarmScheduler.rescheduleNextBoundary()
        }
    }

    fun saveSchedule(
        existingId: Long,
        name: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        weekdays: Set<DayOfWeek>,
        affectedPackages: Set<String>,
        appliesToFullApp: Boolean,
        appliesToSelectiveContent: Boolean
    ) {
        runProtectedAction {
            scheduleRepository.upsertSchedule(
                FocusSchedule(
                    id = existingId,
                    name = name.trim().ifBlank { "Focus Schedule" },
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    weekdays = weekdays,
                    enabled = true,
                    affectedPackages = affectedPackages,
                    appliesToFullApp = appliesToFullApp,
                    appliesToSelectiveContent = appliesToSelectiveContent
                )
            )
            detectionRuleRepository.refreshNow()
            scheduleAlarmScheduler.rescheduleNextBoundary()
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        runProtectedAction {
            scheduleRepository.deleteSchedule(scheduleId)
            detectionRuleRepository.refreshNow()
            scheduleAlarmScheduler.rescheduleNextBoundary()
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            when (val res = pinManager.verifyPin(pin.toCharArray())) {
                PinVerificationResult.Verified, PinVerificationResult.NotSet -> {
                    sessionUnlocked.value = true
                    showPinDialog.value = false
                    pinError.value = null
                    pendingAction?.invoke()
                    pendingAction = null
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
        pendingAction = null
    }

    private fun runProtectedAction(action: suspend () -> Unit) {
        if (uiState.value.strictLocked) {
            pendingAction = action
            pinError.value = null
            showPinDialog.value = true
        } else {
            viewModelScope.launch { action() }
        }
    }
}
