package com.scrollguard.app.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker
) {
    /**
     * Queries measured foreground durations for the current local calendar day.
     * Uses UsageEvents to compute accurate local-day foreground sessions and falls back
     * to UsageStats daily aggregation only if event details are unavailable.
     * Returns an empty map when PACKAGE_USAGE_STATS is not granted (never fabricates data).
     */
    fun queryTodayForegroundDurations(nowMs: Long = System.currentTimeMillis()): Map<String, Long> {
        if (!permissionChecker.isUsageAccessGranted()) return emptyMap()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val zone = ZoneId.systemDefault()
        val startOfDayMs = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        if (nowMs <= startOfDayMs) return emptyMap()

        val durationsByPackage = mutableMapOf<String, Long>()
        val activeStartByPackage = mutableMapOf<String, Long>()

        val events = runCatching { usm.queryEvents(startOfDayMs, nowMs) }.getOrNull()
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (pkg == context.packageName) continue

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        activeStartByPackage[pkg] = event.timeStamp
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = activeStartByPackage.remove(pkg)
                        if (start != null && event.timeStamp > start) {
                            durationsByPackage[pkg] =
                                (durationsByPackage[pkg] ?: 0L) + (event.timeStamp - start)
                        }
                    }
                }
            }

            // Close any session currently open at query time.
            for ((pkg, start) in activeStartByPackage) {
                if (nowMs > start) {
                    durationsByPackage[pkg] = (durationsByPackage[pkg] ?: 0L) + (nowMs - start)
                }
            }
        }

        if (durationsByPackage.isNotEmpty()) {
            return durationsByPackage.filterValues { it > 0L }
        }

        // Fallback to aggregate bucket if device OEM trims UsageEvents.
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDayMs, nowMs)
        }.getOrNull().orEmpty()

        return stats
            .filter { it.packageName != context.packageName && it.totalTimeInForeground > 0L }
            .groupBy { it.packageName }
            .mapValues { (_, list) ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    list.maxOf { it.totalTimeInForeground }
                } else {
                    list.maxOf { it.totalTimeInForeground }
                }
            }
    }
}
