package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.scheduling.ScheduleEvaluator
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDetectionEngine @Inject constructor(
    private val scheduleEvaluator: ScheduleEvaluator
) {
    fun evaluateFullAppRestriction(
        packageName: String,
        rules: List<BlockingRule>,
        schedules: List<FocusSchedule>,
        todayUsageMsByPackage: Map<String, Long>,
        now: ZonedDateTime = ZonedDateTime.now()
    ): BlockDecision? {
        val fullAppRules = rules.filter {
            it.enabled && it.packageName == packageName && it.blockType == BlockType.FULL_APP
        }

        // 1. Direct full-app rule without a daily limit (immediate full block)
        val unconditionalRule = fullAppRules.firstOrNull { it.dailyLimitMinutes == null || it.dailyLimitMinutes <= 0 }
        if (unconditionalRule != null) {
            return BlockDecision(
                shouldBlock = true,
                packageName = packageName,
                appName = unconditionalRule.appName,
                contentCategory = ContentCategory.FULL_APP,
                reason = BlockingReason.FULL_APP_RULE,
                confidence = 1.0f,
                matchedSignals = listOf("package_match", "full_app_rule"),
                messageTitle = "${unconditionalRule.appName} is blocked.",
                messageBody = "You chose to restrict ${unconditionalRule.appName} completely right now. Stay focused on what matters."
            )
        }

        // 2. Daily usage limit rule
        val limitRule = fullAppRules.firstOrNull { (it.dailyLimitMinutes ?: 0) > 0 }
        if (limitRule != null) {
            val limitMinutes = limitRule.dailyLimitMinutes!!
            val usedMs = todayUsageMsByPackage[packageName] ?: 0L
            val limitMs = limitMinutes * 60_000L
            if (usedMs >= limitMs) {
                return BlockDecision(
                    shouldBlock = true,
                    packageName = packageName,
                    appName = limitRule.appName,
                    contentCategory = ContentCategory.FULL_APP,
                    reason = BlockingReason.DAILY_LIMIT_EXCEEDED,
                    confidence = 1.0f,
                    matchedSignals = listOf("package_match", "daily_limit_${limitMinutes}m"),
                    messageTitle = "${limitRule.appName} daily limit reached.",
                    messageBody = "You've reached your ${limitMinutes}-minute daily limit for ${limitRule.appName}. Take a break and reclaim your focus."
                )
            }
        }

        // 3. Scheduled full-app restriction
        val activeFullSchedule = scheduleEvaluator.activeSchedulesForPackage(schedules, packageName, now)
            .firstOrNull { it.appliesToFullApp && packageName in it.affectedPackages }
        if (activeFullSchedule != null) {
            val appName = rules.firstOrNull { it.packageName == packageName }?.appName ?: packageName
            return BlockDecision(
                shouldBlock = true,
                packageName = packageName,
                appName = appName,
                contentCategory = ContentCategory.FULL_APP,
                reason = BlockingReason.SCHEDULED_FULL_APP,
                confidence = 1.0f,
                matchedSignals = listOf("package_match", "schedule:${activeFullSchedule.name}"),
                messageTitle = "$appName is blocked by schedule.",
                messageBody = "Your \"${activeFullSchedule.name}\" focus schedule is active. Take back your time."
            )
        }

        return null
    }
}
