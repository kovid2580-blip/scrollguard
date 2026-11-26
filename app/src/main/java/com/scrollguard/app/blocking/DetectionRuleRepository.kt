package com.scrollguard.app.blocking

import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.domain.repository.FocusScheduleRepository
import com.scrollguard.app.domain.repository.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory hot cache for active rules, schedules, strict-mode state, and today's usage
 * so the AccessibilityService can evaluate decisions without main-thread database I/O.
 */
@Singleton
class DetectionRuleRepository @Inject constructor(
    private val blockingRuleRepository: BlockingRuleRepository,
    private val focusScheduleRepository: FocusScheduleRepository,
    private val preferences: ScrollGuardPreferences,
    private val usageRepository: UsageRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var cachedRules: List<BlockingRule> = emptyList()
        private set

    @Volatile
    var cachedSchedules: List<FocusSchedule> = emptyList()
        private set

    @Volatile
    var cachedStrictModeState: StrictModeState = StrictModeState()
        private set

    @Volatile
    var cachedTodayUsageByPackage: Map<String, Long> = emptyMap()
        private set

    @Volatile
    var cachedDiagnosticsEnabled: Boolean = false
        private set

    init {
        scope.launch {
            blockingRuleRepository.observeAllRules().collectLatest {
                cachedRules = it
            }
        }
        scope.launch {
            focusScheduleRepository.observeAllSchedules().collectLatest {
                cachedSchedules = it
            }
        }
        scope.launch {
            preferences.strictModeState.collectLatest {
                cachedStrictModeState = it
            }
        }
        scope.launch {
            preferences.diagnosticsEnabled.collectLatest {
                cachedDiagnosticsEnabled = it
            }
        }
        scope.launch {
            usageRepository.observeUsageForDate(LocalDate.now().toString()).collectLatest { list ->
                cachedTodayUsageByPackage = list.associate { it.packageName to it.foregroundDurationMs }
            }
        }
    }

    suspend fun refreshNow() {
        cachedRules = blockingRuleRepository.getAllRules()
        cachedSchedules = focusScheduleRepository.getAllSchedules()
    }
}
