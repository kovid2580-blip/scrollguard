package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.monitoring.DiagnosticsLogBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentDetectionEngine @Inject constructor(
    private val confidenceEvaluator: DetectionConfidenceEvaluator,
    private val diagnosticsLogBuffer: DiagnosticsLogBuffer
) {
    fun evaluate(
        screen: ScreenContext,
        activeSelectiveRules: List<BlockingRule>
    ): ContentMatchResult? {
        if (activeSelectiveRules.isEmpty() || screen.nodes.isEmpty()) return null

        val matchingRules = activeSelectiveRules.filter { rule ->
            rule.blockType == BlockType.SELECTIVE_CONTENT &&
                matchesTargetPackage(screen.packageName, rule.packageName, rule.contentType)
        }
        if (matchingRules.isEmpty()) return null

        var bestMatch: ContentMatchResult? = null
        for (rule in matchingRules) {
            val result = runCatching {
                when (rule.contentType) {
                    ContentCategory.INSTAGRAM_REELS -> evaluateInstagramReels(screen, rule)
                    ContentCategory.INSTAGRAM_EXPLORE -> evaluateInstagramExplore(screen, rule)
                    ContentCategory.YOUTUBE_SHORTS -> evaluateYouTubeShorts(screen, rule)
                    ContentCategory.FACEBOOK_REELS -> evaluateFacebookReels(screen, rule)
                    ContentCategory.CUSTOM_SHORT_FORM -> evaluateCustomRule(screen, rule)
                    ContentCategory.FULL_APP -> null
                }
            }.onFailure { err ->
                diagnosticsLogBuffer.record(
                    packageName = screen.packageName,
                    eventType = screen.eventTypeName,
                    matchedRule = rule.contentType.displayName,
                    confidence = 0f,
                    signals = listOf("adapter_error:${err.javaClass.simpleName}"),
                    decision = "SAFE_FAIL_ALLOW"
                )
            }.getOrNull()

            if (result != null && result.matched) {
                if (bestMatch == null || result.confidence > bestMatch.confidence) {
                    bestMatch = result
                }
            }
        }
        return bestMatch
    }

    private fun matchesTargetPackage(
        activePackage: String,
        rulePackage: String,
        category: ContentCategory
    ): Boolean {
        if (activePackage == rulePackage) return true
        return category == ContentCategory.FACEBOOK_REELS &&
            (activePackage == KnownPlatforms.FACEBOOK_PACKAGE ||
                activePackage == KnownPlatforms.FACEBOOK_LITE_PACKAGE)
    }

    private fun evaluateInstagramReels(
        screen: ScreenContext,
        rule: BlockingRule
    ): ContentMatchResult? {
        // Explicit protection for Direct Messages, Stories tray/viewer, and Profile editing
        val inDmOrProfileOrStories = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("direct_inbox") ||
                id.contains("thread_fragment") ||
                id.contains("message_list") ||
                id.contains("story_viewer") ||
                id.contains("stories_tray") ||
                id.contains("profile_tab") && node.isSelected ||
                id.contains("feed_tab") && node.isSelected ||
                desc.contains("your story") ||
                text == "edit profile" ||
                text == "message..."
        }

        val hasClipsResource = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("clips_viewer") ||
                id.contains("clips_video_container") ||
                id.contains("clips_swipe_refresh") ||
                id.contains("clips_media")
        }

        val hasSelectedReelsTab = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            node.isSelected && (
                id.contains("clips_tab") ||
                    desc == "reels" ||
                    desc.contains("reels tab") ||
                    text == "reels"
                )
        }

        val hasImmersivePager = hasImmersiveVerticalContainer(screen)

        // If user is clearly on Home Feed, Stories, DMs, or Profile without active Clips player/tab, allow
        if (inDmOrProfileOrStories && !hasClipsResource && !hasSelectedReelsTab) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.INSTAGRAM_REELS,
                confidence = 0f,
                matchedSignals = listOf("instagram_safe_surface_protected"),
                ruleName = "Instagram Reels Adapter"
            )
        }

        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasImmersivePager,
            hasSelectedNavTab = hasSelectedReelsTab,
            strongExclusionsPresent = inDmOrProfileOrStories
        )

        val boostedConfidence = when {
            hasClipsResource && hasImmersivePager -> maxOf(score.confidence, 0.90f)
            hasSelectedReelsTab -> maxOf(score.confidence, 0.85f)
            else -> score.confidence
        }

        val matched = boostedConfidence >= rule.detectionConfig.minConfidence
        return ContentMatchResult(
            matched = matched,
            category = ContentCategory.INSTAGRAM_REELS,
            confidence = boostedConfidence,
            matchedSignals = score.signals,
            ruleName = "Instagram Reels Adapter"
        )
    }

    private fun evaluateInstagramExplore(
        screen: ScreenContext,
        rule: BlockingRule
    ): ContentMatchResult? {
        val hasSelectedExploreTab = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            node.isSelected && (id.contains("search_tab") || desc.contains("search and explore"))
        }
        val hasExploreGrid = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("explore_grid") || id.contains("discovery_item")
        }

        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasExploreGrid,
            hasSelectedNavTab = hasSelectedExploreTab
        )
        val confidence = if (hasSelectedExploreTab && hasExploreGrid) {
            maxOf(score.confidence, 0.85f)
        } else {
            score.confidence
        }

        return ContentMatchResult(
            matched = confidence >= rule.detectionConfig.minConfidence,
            category = ContentCategory.INSTAGRAM_EXPLORE,
            confidence = confidence,
            matchedSignals = score.signals,
            ruleName = "Instagram Explore Adapter"
        )
    }

    private fun evaluateYouTubeShorts(
        screen: ScreenContext,
        rule: BlockingRule
    ): ContentMatchResult? {
        // Protect normal Watch page, Search, Subscriptions, and Playlists
        val isNormalYouTubeSurface = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("watch_player") ||
                id.contains("watch_while_time_bar") ||
                id.contains("search_edit_text") ||
                (id.contains("pivot_subscriptions") && node.isSelected) ||
                (node.isSelected && desc.equals("subscriptions", ignoreCase = true)) ||
                (node.isSelected && desc.equals("home", ignoreCase = true)) ||
                text == "up next"
        }

        val hasShortsPlayerId = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("reel_watch_fragment") ||
                id.contains("reel_recycler") ||
                id.contains("reel_player") ||
                id.contains("shorts_player") ||
                id.contains("reel_progress_bar")
        }

        val hasSelectedShortsTab = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            node.isSelected && (
                id.contains("pivot_shorts") ||
                    desc == "shorts" ||
                    text == "shorts"
                )
        }

        if (isNormalYouTubeSurface && !hasShortsPlayerId && !hasSelectedShortsTab) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.YOUTUBE_SHORTS,
                confidence = 0f,
                matchedSignals = listOf("youtube_normal_video_protected"),
                ruleName = "YouTube Shorts Adapter"
            )
        }

        val hasImmersivePager = hasImmersiveVerticalContainer(screen)
        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasImmersivePager,
            hasSelectedNavTab = hasSelectedShortsTab,
            strongExclusionsPresent = isNormalYouTubeSurface
        )

        val boostedConfidence = when {
            hasShortsPlayerId -> maxOf(score.confidence, 0.90f)
            hasSelectedShortsTab -> maxOf(score.confidence, 0.85f)
            else -> score.confidence
        }

        return ContentMatchResult(
            matched = boostedConfidence >= rule.detectionConfig.minConfidence,
            category = ContentCategory.YOUTUBE_SHORTS,
            confidence = boostedConfidence,
            matchedSignals = score.signals,
            ruleName = "YouTube Shorts Adapter"
        )
    }

    private fun evaluateFacebookReels(
        screen: ScreenContext,
        rule: BlockingRule
    ): ContentMatchResult? {
        val isNormalFacebookSurface = screen.nodes.any { node ->
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            (node.isSelected && (desc.contains("marketplace") || desc.contains("groups") || desc.contains("notifications") || desc.contains("home"))) ||
                text == "what's on your mind?"
        }

        val hasReelsResource = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("reels_viewer") || id.contains("fb_shorts") || id.contains("reels_tab")
        }

        val hasSelectedReelsTab = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            node.isSelected && (
                id.contains("reels_tab") ||
                    desc == "reels" ||
                    desc.startsWith("reels,") ||
                    desc.contains("reels tab") ||
                    text == "reels"
                )
        }

        if (isNormalFacebookSurface && !hasReelsResource && !hasSelectedReelsTab) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.FACEBOOK_REELS,
                confidence = 0f,
                matchedSignals = listOf("facebook_normal_surface_protected"),
                ruleName = "Facebook Reels Adapter"
            )
        }

        val hasImmersivePager = hasImmersiveVerticalContainer(screen)
        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasImmersivePager,
            hasSelectedNavTab = hasSelectedReelsTab,
            strongExclusionsPresent = isNormalFacebookSurface
        )

        val confidence = when {
            hasReelsResource && hasImmersivePager -> maxOf(score.confidence, 0.88f)
            hasSelectedReelsTab -> maxOf(score.confidence, 0.80f)
            else -> score.confidence
        }

        return ContentMatchResult(
            matched = confidence >= rule.detectionConfig.minConfidence,
            category = ContentCategory.FACEBOOK_REELS,
            confidence = confidence,
            matchedSignals = score.signals,
            ruleName = "Facebook Reels Adapter"
        )
    }

    private fun evaluateCustomRule(
        screen: ScreenContext,
        rule: BlockingRule
    ): ContentMatchResult {
        val hasImmersive = hasImmersiveVerticalContainer(screen)
        val hasSelectedTab = screen.nodes.any { node ->
            if (!node.isSelected) return@any false
            val label = "${node.contentDescription.orEmpty()} ${node.text.orEmpty()} ${node.viewIdResourceName.orEmpty()}".lowercase()
            rule.detectionConfig.contentDescriptionKeywords.any { label.contains(it.lowercase()) } ||
                rule.detectionConfig.textKeywords.any { label.contains(it.lowercase()) }
        }
        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasImmersive,
            hasSelectedNavTab = hasSelectedTab
        )
        return ContentMatchResult(
            matched = score.confidence >= rule.detectionConfig.minConfidence,
            category = rule.contentType,
            confidence = score.confidence,
            matchedSignals = score.signals,
            ruleName = "Custom Rule: ${rule.appName}"
        )
    }

    private fun hasImmersiveVerticalContainer(screen: ScreenContext): Boolean {
        val minWidth = (screen.screenWidthPx * 0.85f).toInt()
        val minHeight = (screen.screenHeightPx * 0.65f).toInt()
        return screen.nodes.any { node ->
            val cls = node.className?.lowercase().orEmpty()
            val isPagerOrList = cls.contains("viewpager") || cls.contains("recyclerview") || cls.contains("framelayout")
            val isLarge = node.boundsWidth >= minWidth && node.boundsHeight >= minHeight
            isPagerOrList && isLarge && (node.isScrollable || cls.contains("viewpager"))
        }
    }
}
