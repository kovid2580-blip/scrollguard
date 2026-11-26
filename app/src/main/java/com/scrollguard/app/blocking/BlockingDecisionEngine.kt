package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.scheduling.ScheduleEvaluator
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingDecisionEngine @Inject constructor(
    private val appDetectionEngine: AppDetectionEngine,
    private val contentDetectionEngine: ContentDetectionEngine,
    private val scheduleEvaluator: ScheduleEvaluator
) {
    fun decide(
        screen: ScreenContext,
        rules: List<BlockingRule>,
        schedules: List<FocusSchedule>,
        strictModeState: StrictModeState,
        todayUsageMsByPackage: Map<String, Long>,
        selfPackageName: String,
        now: ZonedDateTime = ZonedDateTime.now()
    ): BlockDecision {
        val pkg = screen.packageName

        // 1. Never block ScrollGuard itself or essential system/launcher/emergency UI
        if (pkg.isBlank() || pkg == selfPackageName || pkg in KnownPlatforms.SYSTEM_EXEMPT_PACKAGES) {
            return BlockDecision.allow(pkg, reason = BlockingReason.EXEMPT_SELF_OR_SYSTEM)
        }

        val nowMs = now.toInstant().toEpochMilli()
        val activeFocusSession = strictModeState.focusSessionActive && strictModeState.focusSessionEndEpochMs > nowMs
        val strictLockActive = strictModeState.isStrictModeEnabled || activeFocusSession

        // 2. Normal Mode temporary pause
        if (strictModeState.isBlockingPaused && !strictLockActive) {
            return BlockDecision.allow(pkg, reason = BlockingReason.PAUSED_NORMAL_MODE)
        }

        // 3. Active temporary unlock for this package
        if (strictModeState.tempUnlockPackage == pkg && strictModeState.tempUnlockExpiresEpochMs > nowMs) {
            return BlockDecision.allow(pkg, reason = BlockingReason.TEMPORARY_UNLOCK_ACTIVE)
        }

        // 4. Full-app restrictions take precedence over selective content restrictions
        val fullAppDecision = appDetectionEngine.evaluateFullAppRestriction(
            packageName = pkg,
            rules = rules,
            schedules = schedules,
            todayUsageMsByPackage = todayUsageMsByPackage,
            now = now
        )
        if (fullAppDecision != null) {
            return fullAppDecision
        }

        // 5. Selective content restrictions (enabled rules OR active selective schedules)
        val activeSelectiveSchedule = scheduleEvaluator.activeSchedulesForPackage(schedules, pkg, now)
            .firstOrNull { it.appliesToSelectiveContent && (it.affectedPackages.isEmpty() || pkg in it.affectedPackages) }

        val candidateSelectiveRules = rules.filter { rule ->
            rule.blockType == BlockType.SELECTIVE_CONTENT &&
                (rule.enabled || activeSelectiveSchedule != null)
        }

        val contentMatch = contentDetectionEngine.evaluate(screen, candidateSelectiveRules)
        if (contentMatch != null && contentMatch.matched) {
            val matchedRule = candidateSelectiveRules.firstOrNull { it.contentType == contentMatch.category }
            val appName = matchedRule?.appName ?: pkg
            val isScheduledOnly = matchedRule?.enabled == false && activeSelectiveSchedule != null
            val reason = if (isScheduledOnly) {
                BlockingReason.SCHEDULED_SELECTIVE_CONTENT
            } else {
                BlockingReason.SELECTIVE_CONTENT_RULE
            }
            val (title, body) = buildSelectiveMessage(appName, contentMatch.category)
            return BlockDecision(
                shouldBlock = true,
                packageName = pkg,
                appName = appName,
                contentCategory = contentMatch.category,
                reason = reason,
                confidence = contentMatch.confidence,
                matchedSignals = contentMatch.matchedSignals,
                messageTitle = title,
                messageBody = body,
                entryPointsToSuppress = emptyList(),
                uiHealth = contentMatch.uiHealth,
                adapterName = contentMatch.ruleName,
                screenClassification = contentMatch.screenClassification,
                minConfidenceRequired = matchedRule?.detectionConfig?.minConfidence ?: 0.65f,
                failureReason = contentMatch.failureReason
            )
        }

        val unmatchedRule = contentMatch?.let { cm ->
            candidateSelectiveRules.firstOrNull { it.contentType == cm.category }
        }
        return BlockDecision.allow(
            packageName = pkg,
            contentCategory = contentMatch?.category ?: ContentCategory.FULL_APP,
            confidence = contentMatch?.confidence ?: 0f,
            matchedSignals = contentMatch?.matchedSignals ?: emptyList(),
            entryPointsToSuppress = contentMatch?.entryPoints ?: emptyList(),
            uiHealth = contentMatch?.uiHealth ?: AdapterUiHealth.HEALTHY,
            adapterName = contentMatch?.ruleName ?: "",
            screenClassification = contentMatch?.screenClassification ?: ScreenClassification.UNKNOWN,
            minConfidenceRequired = unmatchedRule?.detectionConfig?.minConfidence ?: 0.65f,
            failureReason = contentMatch?.failureReason
        )
    }

    private fun buildSelectiveMessage(appName: String, category: ContentCategory): Pair<String, String> {
        return when (category) {
            ContentCategory.INSTAGRAM_REELS ->
                "Instagram Reels is blocked." to
                    "You came here to use Instagram, not to lose another hour scrolling. Take back your time."

            ContentCategory.INSTAGRAM_EXPLORE ->
                "Instagram Explore is blocked." to
                    "Stay intentional on Instagram. Your feed, Stories, Search, and DMs remain available."

            ContentCategory.YOUTUBE_SHORTS ->
                "YouTube Shorts is blocked." to
                    "You came to YouTube with a purpose. Standard videos, Search, Subscriptions, Playlists, and Comments stay open."

            ContentCategory.FACEBOOK_REELS ->
                "Facebook Reels is blocked." to
                    "Short-form Reels are paused while your Feed, Groups, Pages, and Messenger stay available."

            ContentCategory.TIKTOK_SHORT_FORM ->
                "TikTok short-video feed is blocked." to
                    "ScrollGuard interrupted TikTok's continuous short-video feed so you can take back your time."

            ContentCategory.SNAPCHAT_SPOTLIGHT ->
                "Snapchat Spotlight is blocked." to
                    "Spotlight is paused while Messaging and ordinary Camera functionality stay open."

            else ->
                "${category.displayName} in $appName is blocked." to
                    "ScrollGuard blocked this addictive feed so you can take back your time."
        }
    }
}
