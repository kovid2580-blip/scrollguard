package com.scrollguard.app.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.domain.model.AppUsageInfo
import com.scrollguard.app.domain.model.BlockedAttempt
import com.scrollguard.app.domain.model.DailyReport
import com.scrollguard.app.domain.repository.BlockedAttemptRepository
import com.scrollguard.app.domain.repository.UsageRepository
import com.scrollguard.app.monitoring.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReportsUiState(
    val todayReport: DailyReport? = null,
    val weeklyReports: List<DailyReport> = emptyList(),
    val todayAppUsage: List<AppUsageInfo> = emptyList(),
    val recentBlockedAttempts: List<BlockedAttempt> = emptyList(),
    val usageAccessGranted: Boolean = false,
    val dailyComparisonPercent: Int? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val blockedAttemptRepository: BlockedAttemptRepository,
    private val permissionChecker: PermissionChecker
) : ViewModel() {

    private val today = LocalDate.now().toString()

    val uiState: StateFlow<ReportsUiState> = combine(
        usageRepository.observeDailyReport(today),
        usageRepository.observeRecentReports(7),
        usageRepository.observeUsageForDate(today),
        blockedAttemptRepository.observeRecentAttempts(20),
        permissionChecker.status
    ) { todayReport, recentReports, appUsage, attempts, perms ->
        val sortedWeekly = recentReports.sortedBy { it.date }
        val yesterdayDate = LocalDate.now().minusDays(1).toString()
        val yesterdayReport = sortedWeekly.firstOrNull { it.date == yesterdayDate }
        val comparison = if (todayReport != null && yesterdayReport != null && yesterdayReport.totalScreenTimeMs > 0L) {
            (((todayReport.totalScreenTimeMs - yesterdayReport.totalScreenTimeMs).toDouble() /
                yesterdayReport.totalScreenTimeMs.toDouble()) * 100.0).toInt()
        } else {
            null
        }

        ReportsUiState(
            todayReport = todayReport,
            weeklyReports = sortedWeekly,
            todayAppUsage = appUsage.take(12),
            recentBlockedAttempts = attempts,
            usageAccessGranted = perms.usageAccessGranted,
            dailyComparisonPercent = comparison
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsUiState(usageAccessGranted = permissionChecker.isUsageAccessGranted())
    )

    init {
        refresh()
    }

    fun refresh() {
        permissionChecker.refresh()
        viewModelScope.launch {
            runCatching { usageRepository.syncTodayUsageFromSystem() }
        }
    }
}
