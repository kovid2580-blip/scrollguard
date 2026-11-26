package com.scrollguard.app.domain.usecase

import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.DetectionConfig
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.domain.repository.FocusScheduleRepository
import com.scrollguard.app.domain.repository.UsageRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ScrollGuardBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val rules: List<BackupRuleDto>,
    val schedules: List<BackupScheduleDto>
)

@Serializable
data class BackupRuleDto(
    val packageName: String,
    val appName: String,
    val blockType: BlockType,
    val contentType: ContentCategory,
    val enabled: Boolean,
    val dailyLimitMinutes: Int? = null,
    val detectionConfig: DetectionConfig
)

@Serializable
data class BackupScheduleDto(
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val weekdayValues: List<Int>,
    val enabled: Boolean,
    val affectedPackages: List<String>,
    val appliesToFullApp: Boolean,
    val appliesToSelectiveContent: Boolean
)

@Singleton
class BackupRestoreUseCase @Inject constructor(
    private val ruleRepository: BlockingRuleRepository,
    private val scheduleRepository: FocusScheduleRepository,
    private val json: Json
) {
    suspend fun exportConfigurationJson(): String {
        val rules = ruleRepository.getAllRules().map {
            BackupRuleDto(
                packageName = it.packageName,
                appName = it.appName,
                blockType = it.blockType,
                contentType = it.contentType,
                enabled = it.enabled,
                dailyLimitMinutes = it.dailyLimitMinutes,
                detectionConfig = it.detectionConfig
            )
        }
        val schedules = scheduleRepository.getAllSchedules().map {
            BackupScheduleDto(
                name = it.name,
                startHour = it.startHour,
                startMinute = it.startMinute,
                endHour = it.endHour,
                endMinute = it.endMinute,
                weekdayValues = it.weekdays.map { d -> d.value }.sorted(),
                enabled = it.enabled,
                affectedPackages = it.affectedPackages.sorted(),
                appliesToFullApp = it.appliesToFullApp,
                appliesToSelectiveContent = it.appliesToSelectiveContent
            )
        }
        return json.encodeToString(ScrollGuardBackupPayload(rules = rules, schedules = schedules))
    }

    suspend fun importConfigurationJson(rawJson: String): Result<Pair<Int, Int>> {
        return runCatching {
            val payload = json.decodeFromString<ScrollGuardBackupPayload>(rawJson)
            val now = System.currentTimeMillis()
            val rules = payload.rules.map { dto ->
                BlockingRule(
                    packageName = dto.packageName,
                    appName = dto.appName,
                    blockType = dto.blockType,
                    contentType = dto.contentType,
                    enabled = dto.enabled,
                    dailyLimitMinutes = dto.dailyLimitMinutes,
                    detectionConfig = dto.detectionConfig,
                    createdAt = now,
                    updatedAt = now
                )
            }
            val schedules = payload.schedules.map { dto ->
                FocusSchedule(
                    name = dto.name,
                    startHour = dto.startHour,
                    startMinute = dto.startMinute,
                    endHour = dto.endHour,
                    endMinute = dto.endMinute,
                    weekdays = dto.weekdayValues.mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet(),
                    enabled = dto.enabled,
                    affectedPackages = dto.affectedPackages.toSet(),
                    appliesToFullApp = dto.appliesToFullApp,
                    appliesToSelectiveContent = dto.appliesToSelectiveContent
                )
            }
            ruleRepository.replaceAllRules(rules)
            scheduleRepository.replaceAllSchedules(schedules)
            rules.size to schedules.size
        }
    }
}

@Singleton
class ExportReportsUseCase @Inject constructor(
    private val usageRepository: UsageRepository
) {
    suspend fun exportRecentReportsCsv(days: Int = 14): String {
        val reports = usageRepository.observeRecentReports(days).first()
        val sb = StringBuilder()
        sb.appendLine("date,total_screen_time_minutes,blocked_attempts,focus_duration_minutes,estimated_reclaimed_minutes")
        for (report in reports) {
            val screenMin = report.totalScreenTimeMs / 60_000L
            val focusMin = report.focusDurationMs / 60_000L
            sb.appendLine("${report.date},$screenMin,${report.blockedAttempts},$focusMin,${report.estimatedReclaimedMinutes}")
        }
        return sb.toString()
    }
}
