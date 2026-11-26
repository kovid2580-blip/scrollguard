package com.scrollguard.app.domain.repository

import com.scrollguard.app.domain.model.AppUsageInfo
import com.scrollguard.app.domain.model.BlockedAttempt
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.DailyReport
import com.scrollguard.app.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun observeUsageForDate(date: String): Flow<List<AppUsageInfo>>
    fun observeDailyReport(date: String): Flow<DailyReport?>
    fun observeRecentReports(limit: Int = 7): Flow<List<DailyReport>>
    suspend fun getTodayPackageUsageMs(packageName: String, date: String): Long
    suspend fun syncTodayUsageFromSystem(): DailyReport
    suspend fun addFocusSessionDuration(date: String, durationMs: Long)
}

interface BlockedAttemptRepository {
    fun observeRecentAttempts(limit: Int = 50): Flow<List<BlockedAttempt>>
    fun observeTodayCount(): Flow<Int>
    suspend fun getTodayCount(): Int
    suspend fun recordAttempt(
        packageName: String,
        contentType: ContentCategory,
        reason: String,
        confidence: Float
    )
}

interface InstalledAppsRepository {
    suspend fun getLaunchableApps(): List<InstalledApp>
    suspend fun resolveAppName(packageName: String): String
}
