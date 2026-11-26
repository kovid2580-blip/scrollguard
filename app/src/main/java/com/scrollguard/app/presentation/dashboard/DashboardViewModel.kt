package com.scrollguard.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.PermissionStatus
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.domain.repository.BlockedAttemptRepository
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.domain.repository.FocusScheduleRepository
import com.scrollguard.app.domain.repository.UsageRepository
import com.scrollguard.app.monitoring.PermissionChecker
import com.scrollguard.app.scheduling.ScheduleEvaluator
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
import java.time.ZonedDateTime
import javax.inject.Inject

data class DashboardUiState(
    val todayScreenTimeMs: Long = 0L,
    val protectedAppsCount: Int = 0,
    val selectiveRulesActiveCount: Int = 0,
    val blockedAttemptsToday: Int = 0,
    val activeSchedulesCount: Int = 0,
    val strictModeState: StrictModeState = StrictModeState(),
    val permissionStatus: PermissionStatus = PermissionStatus(false, false, false, true),
    val focusRemainingSeconds: Long = 0L,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val blockingRuleRepository: BlockingRuleRepository,
    private val blockedAttemptRepository: BlockedAttemptRepository,
    private val scheduleRepository: FocusScheduleRepository,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val preferences: ScrollGuardPreferences,
    private val permissionChecker: PermissionChecker
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        usageRepository.observeDailyReport(LocalDate.now().toString()),
        blockingRuleRepository.observeAllRules(),
        blockedAttemptRepository.observeTodayCount(),
        scheduleRepository.observeAllSchedules(),
        preferences.strictModeState,
        permissionChecker.status,
        ticker,
        refreshing
    ) { values ->
        val report = values[0] as? com.scrollguard.app.domain.model.DailyReport
        @Suppress("UNCHECKED_CAST")
        val rules = values[1] as List<com.scrollguard.app.domain.model.BlockingRule>
        val blockedToday = values[2] as Int
        @Suppress("UNCHECKED_CAST")
        val schedules = values[3] as List<com.scrollguard.app.domain.model.FocusSchedule>
        val strict = values[4] as StrictModeState
        val perms = values[5] as PermissionStatus
        val nowMs = values[6] as Long
        val isRefreshing = values[7] as Boolean

        val enabledRules = rules.filter { it.enabled }
        val protectedPackages = enabledRules.map { it.packageName }.distinct().size
        val selectiveActive = enabledRules.count { it.blockType == BlockType.SELECTIVE_CONTENT }
        val nowZdt = ZonedDateTime.now()
        val activeSchedules = schedules.count { scheduleEvaluator.isScheduleActive(it, nowZdt) }
        val focusRemainingSec = if (strict.focusSessionActive && strict.focusSessionEndEpochMs > nowMs) {
            (strict.focusSessionEndEpochMs - nowMs) / 1000L
        } else {
            0L
        }

        DashboardUiState(
            todayScreenTimeMs = report?.totalScreenTimeMs ?: 0L,
            protectedAppsCount = protectedPackages,
            selectiveRulesActiveCount = selectiveActive,
            blockedAttemptsToday = blockedToday,
            activeSchedulesCount = activeSchedules,
            strictModeState = strict,
            permissionStatus = perms,
            focusRemainingSeconds = focusRemainingSec,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(permissionStatus = permissionChecker.readCurrentStatus())
    )

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        permissionChecker.refresh()
        viewModelScope.launch {
            refreshing.value = true
            runCatching { usageRepository.syncTodayUsageFromSystem() }
            refreshing.value = false
        }
    }

    fun toggleNormalModePause() {
        val current = uiState.value.strictModeState
        if (current.isSettingsLocked()) return
        viewModelScope.launch {
            preferences.setBlockingPaused(!current.isBlockingPaused)
        }
    }

    fun startQuickFocusSession() {
        val durationMin = uiState.value.strictModeState.focusSessionDurationMinutes
        viewModelScope.launch {
            preferences.startFocusSession(
                durationMinutes = durationMin,
                endEpochMs = System.currentTimeMillis() + durationMin * 60_000L
            )
        }
    }
}
