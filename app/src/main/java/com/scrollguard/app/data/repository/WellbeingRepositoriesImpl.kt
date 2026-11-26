package com.scrollguard.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.scrollguard.app.data.dao.BlockedAttemptDao
import com.scrollguard.app.data.dao.DailyReportDao
import com.scrollguard.app.data.dao.UsageRecordDao
import com.scrollguard.app.data.entity.BlockedAttemptEntity
import com.scrollguard.app.data.entity.DailyReportEntity
import com.scrollguard.app.data.entity.UsageRecordEntity
import com.scrollguard.app.domain.model.AppUsageInfo
import com.scrollguard.app.domain.model.BlockedAttempt
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.DailyReport
import com.scrollguard.app.domain.model.InstalledApp
import com.scrollguard.app.domain.repository.BlockedAttemptRepository
import com.scrollguard.app.domain.repository.InstalledAppsRepository
import com.scrollguard.app.domain.repository.UsageRepository
import com.scrollguard.app.monitoring.UsageStatsMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : InstalledAppsRepository {

    private val selectivePackages = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.zhiliaoapp.musically.go",
        "com.snapchat.android"
    )

    override suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        resolved
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val pkg = activityInfo.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val appInfo = activityInfo.applicationInfo
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                InstalledApp(
                    packageName = pkg,
                    appName = label,
                    isSystemApp = isSystem,
                    supportsSelectiveBlocking = pkg in selectivePackages
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    override suspend fun resolveAppName(packageName: String): String = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }
}

@Singleton
class BlockedAttemptRepositoryImpl @Inject constructor(
    private val dao: BlockedAttemptDao
) : BlockedAttemptRepository {

    override fun observeRecentAttempts(limit: Int): Flow<List<BlockedAttempt>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeTodayCount(): Flow<Int> {
        val (start, end) = todayBounds()
        return dao.observeCountBetween(start, end)
    }

    override suspend fun getTodayCount(): Int {
        val (start, end) = todayBounds()
        return dao.countBetween(start, end)
    }

    override suspend fun recordAttempt(
        packageName: String,
        contentType: ContentCategory,
        reason: String,
        confidence: Float
    ) {
        dao.insert(
            BlockedAttemptEntity(
                packageName = packageName,
                contentType = contentType.name,
                timestamp = System.currentTimeMillis(),
                reason = reason,
                confidence = confidence
            )
        )
    }

    private fun todayBounds(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun BlockedAttemptEntity.toDomain(): BlockedAttempt {
        val category = runCatching { ContentCategory.valueOf(contentType) }
            .getOrDefault(ContentCategory.CUSTOM_SHORT_FORM)
        return BlockedAttempt(
            id = id,
            packageName = packageName,
            contentType = category,
            timestamp = timestamp,
            reason = reason,
            confidence = confidence
        )
    }
}

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val usageRecordDao: UsageRecordDao,
    private val dailyReportDao: DailyReportDao,
    private val blockedAttemptRepository: BlockedAttemptRepository,
    private val usageStatsMonitor: UsageStatsMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val json: Json
) : UsageRepository {

    override fun observeUsageForDate(date: String): Flow<List<AppUsageInfo>> =
        usageRecordDao.observeByDate(date).map { list ->
            list.map {
                AppUsageInfo(
                    packageName = it.packageName,
                    appName = it.appName,
                    foregroundDurationMs = it.foregroundDuration,
                    date = it.date
                )
            }
        }

    override fun observeDailyReport(date: String): Flow<DailyReport?> =
        dailyReportDao.observeByDate(date).map { it?.toDomain() }

    override fun observeRecentReports(limit: Int): Flow<List<DailyReport>> =
        dailyReportDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getTodayPackageUsageMs(packageName: String, date: String): Long =
        usageRecordDao.getForPackageAndDate(packageName, date)?.foregroundDuration ?: 0L

    override suspend fun syncTodayUsageFromSystem(): DailyReport = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val usageMap = usageStatsMonitor.queryTodayForegroundDurations()
        val existingReport = dailyReportDao.getByDate(today)
        val blockedCount = blockedAttemptRepository.getTodayCount()

        val records = usageMap.entries.map { (pkg, durationMs) ->
            UsageRecordEntity(
                packageName = pkg,
                appName = installedAppsRepository.resolveAppName(pkg),
                date = today,
                foregroundDuration = durationMs
            )
        }
        if (records.isNotEmpty()) {
            usageRecordDao.upsertAll(records)
        }

        val totalScreenTime = if (usageMap.isNotEmpty()) {
            usageMap.values.sum()
        } else {
            existingReport?.totalScreenTime ?: 0L
        }

        val summaryMap = if (usageMap.isNotEmpty()) {
            usageMap
        } else {
            existingReport?.let {
                runCatching { json.decodeFromString<Map<String, Long>>(it.applicationUsageSummary) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
        }

        val updated = DailyReportEntity(
            date = today,
            totalScreenTime = totalScreenTime,
            blockedAttempts = blockedCount,
            focusDuration = existingReport?.focusDuration ?: 0L,
            applicationUsageSummary = json.encodeToString(summaryMap)
        )
        dailyReportDao.upsert(updated)
        updated.toDomain()
    }

    override suspend fun addFocusSessionDuration(date: String, durationMs: Long) {
        val existing = dailyReportDao.getByDate(date)
        if (existing == null) {
            dailyReportDao.upsert(
                DailyReportEntity(
                    date = date,
                    totalScreenTime = 0L,
                    blockedAttempts = blockedAttemptRepository.getTodayCount(),
                    focusDuration = durationMs,
                    applicationUsageSummary = "{}"
                )
            )
        } else {
            dailyReportDao.addFocusDuration(date, durationMs)
        }
    }

    private fun DailyReportEntity.toDomain(): DailyReport {
        val map = runCatching {
            json.decodeFromString<Map<String, Long>>(applicationUsageSummary)
        }.getOrDefault(emptyMap())
        return DailyReport(
            date = date,
            totalScreenTimeMs = totalScreenTime,
            blockedAttempts = blockedAttempts,
            focusDurationMs = focusDuration,
            applicationUsageSummary = map
        )
    }
}
