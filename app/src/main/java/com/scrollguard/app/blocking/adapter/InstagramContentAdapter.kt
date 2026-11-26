package com.scrollguard.app.blocking.adapter

import com.scrollguard.app.blocking.AdapterUiHealth
import com.scrollguard.app.blocking.ContentMatchResult
import com.scrollguard.app.blocking.DetectionConfidenceEvaluator
import com.scrollguard.app.blocking.EntryPointTarget
import com.scrollguard.app.blocking.KnownPlatforms
import com.scrollguard.app.blocking.ScreenClassification
import com.scrollguard.app.blocking.ScreenContext
import com.scrollguard.app.blocking.ScreenNodeSnapshot
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstagramContentAdapter @Inject constructor(
    private val confidenceEvaluator: DetectionConfidenceEvaluator
) : PlatformContentAdapter {

    override fun supports(rule: BlockingRule, activePackage: String): Boolean {
        return activePackage == KnownPlatforms.INSTAGRAM_PACKAGE &&
            rule.packageName == KnownPlatforms.INSTAGRAM_PACKAGE &&
            (rule.contentType == ContentCategory.INSTAGRAM_REELS ||
                rule.contentType == ContentCategory.INSTAGRAM_EXPLORE)
    }

    override fun evaluate(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        return when (rule.contentType) {
            ContentCategory.INSTAGRAM_EXPLORE -> evaluateExplore(screen, rule)
            else -> evaluateReels(screen, rule)
        }
    }

    private fun evaluateReels(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        val uiHealth = AdapterNodeHelpers.inferUiHealth(screen)
        val classification = classifyScreen(screen)
        val entryPoints = if (!screen.isImeVisible && classification != ScreenClassification.DIRECT_MESSAGES) {
            findReelsEntryPoints(screen)
        } else {
            emptyList()
        }

        val hasStrongClipsViewer = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("clips_viewer") ||
                id.contains("clips_video_container") ||
                id.contains("clips_swipe_refresh")
        }

        val hasSelectedReelsTab = isBottomReelsTabSelected(screen)
        val hasImmersivePager = AdapterNodeHelpers.hasImmersiveVerticalContainer(screen)

        // Strict normal-surface protection: Home, Stories, Explore/Search, DMs, Profile, Posts/Notifications
        val isProtectedNormalScreen = classification in setOf(
            ScreenClassification.HOME_FEED,
            ScreenClassification.STORIES,
            ScreenClassification.EXPLORE_SEARCH,
            ScreenClassification.DIRECT_MESSAGES,
            ScreenClassification.PROFILE,
            ScreenClassification.POST_OR_NOTIFICATIONS
        )

        if (isProtectedNormalScreen && !hasStrongClipsViewer && !hasSelectedReelsTab) {
            val failureNote = if (entryPoints.isEmpty() && !screen.isImeVisible &&
                classification in setOf(ScreenClassification.HOME_FEED, ScreenClassification.EXPLORE_SEARCH, ScreenClassification.PROFILE)
            ) {
                "Reels nav icon bounds not exposed on ${classification.label}"
            } else {
                null
            }
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.INSTAGRAM_REELS,
                confidence = 0f,
                matchedSignals = listOf("instagram_protected:${classification.name}"),
                ruleName = "InstagramContentAdapter",
                entryPoints = entryPoints,
                uiHealth = if (failureNote != null && uiHealth == AdapterUiHealth.HEALTHY) {
                    AdapterUiHealth.LIMITED_VISIBILITY
                } else {
                    uiHealth
                },
                screenClassification = classification,
                failureReason = failureNote
            )
        }

        // If screen is completely unknown and has neither strong Reels player IDs nor selected Reels tab,
        // require multiple independent Reels player cues before blocking.
        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasImmersivePager,
            hasSelectedNavTab = hasSelectedReelsTab,
            strongExclusionsPresent = isProtectedNormalScreen
        )

        val hasReelsPlayerControls = hasReelsActionCluster(screen)
        val boostedConfidence = when {
            hasStrongClipsViewer && hasImmersivePager -> maxOf(score.confidence, 0.92f)
            hasSelectedReelsTab && !isProtectedNormalScreen -> maxOf(score.confidence, 0.88f)
            hasStrongClipsViewer -> maxOf(score.confidence, 0.82f)
            hasReelsPlayerControls && hasImmersivePager && !isProtectedNormalScreen ->
                maxOf(score.confidence, 0.80f)
            else -> score.confidence
        }

        val matched = boostedConfidence >= rule.detectionConfig.minConfidence &&
            (!isProtectedNormalScreen || hasStrongClipsViewer || hasSelectedReelsTab)

        val finalClassification = if (matched) {
            ScreenClassification.REELS_PLAYBACK
        } else {
            classification
        }

        return ContentMatchResult(
            matched = matched,
            category = ContentCategory.INSTAGRAM_REELS,
            confidence = boostedConfidence,
            matchedSignals = score.signals + if (hasReelsPlayerControls) listOf("reels_player_controls") else emptyList(),
            ruleName = "InstagramContentAdapter",
            entryPoints = if (matched) emptyList() else entryPoints,
            uiHealth = uiHealth,
            screenClassification = finalClassification
        )
    }

    private fun evaluateExplore(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        val uiHealth = AdapterNodeHelpers.inferUiHealth(screen)
        val hasActiveSearchInput = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("action_bar_search_edit_text") || id.contains("search_edit_text") || node.isFocused
        }
        val hasSelectedExploreTab = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            node.isSelected && (id.contains("search_tab") || desc.contains("search and explore"))
        }
        val hasExploreGrid = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("explore_grid") || id.contains("discovery_item")
        }

        if (hasActiveSearchInput && !hasExploreGrid) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.INSTAGRAM_EXPLORE,
                confidence = 0f,
                matchedSignals = listOf("instagram_search_preserved"),
                ruleName = "InstagramContentAdapter",
                uiHealth = uiHealth,
                screenClassification = ScreenClassification.EXPLORE_SEARCH
            )
        }

        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasExploreGrid,
            hasSelectedNavTab = hasSelectedExploreTab,
            strongExclusionsPresent = hasActiveSearchInput
        )
        val confidence = if (hasSelectedExploreTab && hasExploreGrid && !hasActiveSearchInput) {
            maxOf(score.confidence, 0.85f)
        } else {
            score.confidence
        }

        return ContentMatchResult(
            matched = confidence >= rule.detectionConfig.minConfidence,
            category = ContentCategory.INSTAGRAM_EXPLORE,
            confidence = confidence,
            matchedSignals = score.signals,
            ruleName = "InstagramContentAdapter",
            uiHealth = uiHealth,
            screenClassification = ScreenClassification.EXPLORE_SEARCH
        )
    }

    private fun classifyScreen(screen: ScreenContext): ScreenClassification {
        val hasStrongClipsViewer = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("clips_viewer") ||
                id.contains("clips_video_container") ||
                id.contains("clips_swipe_refresh")
        }
        if (hasStrongClipsViewer) return ScreenClassification.REELS_PLAYBACK

        val hasSelectedReels = isBottomReelsTabSelected(screen)
        if (hasSelectedReels) return ScreenClassification.REELS_PLAYBACK

        val hasDirectMessages = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("direct_inbox") ||
                id.contains("thread_fragment") ||
                id.contains("message_list") ||
                id.contains("direct_thread") ||
                text == "message..." ||
                text == "messages"
        }
        if (hasDirectMessages) return ScreenClassification.DIRECT_MESSAGES

        val hasStories = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("story_viewer") ||
                id.contains("reel_viewer_avatar") ||
                desc.contains("your story") ||
                text.startsWith("reply to ")
        }
        if (hasStories) return ScreenClassification.STORIES

        val hasSearchOrExplore = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            id.contains("action_bar_search_edit_text") ||
                id.contains("search_edit_text") ||
                id.contains("explore_grid") ||
                id.contains("discovery_item") ||
                (node.isSelected && (id.contains("search_tab") || desc.contains("search and explore")))
        }
        if (hasSearchOrExplore) return ScreenClassification.EXPLORE_SEARCH

        val hasProfile = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            (node.isSelected && id.contains("profile_tab")) ||
                id.contains("profile_header") ||
                text == "edit profile" ||
                text == "share profile"
        }
        if (hasProfile) return ScreenClassification.PROFILE

        val hasHomeFeed = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            (node.isSelected && (id.contains("feed_tab") || desc == "home")) ||
                id.contains("stories_tray") ||
                id.contains("row_feed_photo") ||
                id.contains("row_feed_button_like") ||
                id.contains("row_feed_profile_header") ||
                desc.contains("instagram logo")
        }
        if (hasHomeFeed) return ScreenClassification.HOME_FEED

        val hasPostOrNotifications = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("comment_composer") ||
                id.contains("newsfeed") ||
                text == "notifications" ||
                text == "comments"
        }
        if (hasPostOrNotifications) return ScreenClassification.POST_OR_NOTIFICATIONS

        return ScreenClassification.UNKNOWN
    }

    private fun isBottomReelsTabSelected(screen: ScreenContext): Boolean {
        val bottomBarTopThreshold = (screen.screenHeightPx * 0.78f).toInt()
        return screen.nodes.any { node ->
            if (!node.isSelected) return@any false
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase()?.trim().orEmpty()
            val text = node.text?.lowercase()?.trim().orEmpty()
            val inBottomZone = node.boundsHeight == 0 || node.boundsTop >= bottomBarTopThreshold
            inBottomZone && (
                id.contains("clips_tab") ||
                    id.contains("reels_tab") ||
                    desc == "reels" ||
                    desc.startsWith("reels,") ||
                    desc.contains("reels tab") ||
                    text == "reels"
                )
        }
    }

    private fun findReelsEntryPoints(screen: ScreenContext): List<EntryPointTarget> {
        val bottomNavTopFloor = (screen.screenHeightPx * 0.78f).toInt()
        return screen.nodes.mapNotNull { node ->
            if (node.isSelected) return@mapNotNull null
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase()?.trim().orEmpty()
            val text = node.text?.lowercase()?.trim().orEmpty()

            val inBottomBarZone = node.boundsTop >= bottomNavTopFloor &&
                node.boundsBottom <= screen.screenHeightPx &&
                node.boundsWidth in 36..(screen.screenWidthPx * 0.35f).toInt() &&
                node.boundsHeight in 36..(screen.screenHeightPx * 0.14f).toInt()

            val isReelsId = id.contains("clips_tab") || id.contains("reels_tab")
            val isReelsDesc = desc == "reels" ||
                desc.startsWith("reels,") ||
                desc.contains("reels tab") ||
                (inBottomBarZone && desc.contains("reel"))
            val isReelsBottomText = inBottomBarZone && text == "reels"

            if (inBottomBarZone && (isReelsId || isReelsDesc || isReelsBottomText)) {
                toValidatedBottomTabTarget(node, screen)
            } else {
                null
            }
        }.distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }.take(1)
    }

    private fun toValidatedBottomTabTarget(
        node: ScreenNodeSnapshot,
        screen: ScreenContext
    ): EntryPointTarget? {
        return AdapterNodeHelpers.toSafeEntryPointTarget(
            node = node,
            screen = screen,
            category = ContentCategory.INSTAGRAM_REELS,
            label = "Reels Tab"
        )
    }

    private fun hasReelsActionCluster(screen: ScreenContext): Boolean {
        var cues = 0
        val hasAudio = screen.nodes.any {
            val desc = it.contentDescription?.lowercase().orEmpty()
            val text = it.text?.lowercase().orEmpty()
            desc.contains("original audio") || text.contains("original audio") ||
                it.viewIdResourceName?.lowercase()?.contains("clips_ufi") == true
        }
        if (hasAudio) cues++

        val hasReelsAuthor = screen.nodes.any {
            it.viewIdResourceName?.lowercase()?.contains("clips_author") == true
        }
        if (hasReelsAuthor) cues++

        val hasRemixOrCamera = screen.nodes.any {
            val desc = it.contentDescription?.lowercase().orEmpty()
            desc.contains("remix") || desc.contains("create reel") || desc.contains("reels camera")
        }
        if (hasRemixOrCamera) cues++

        return cues >= 2
    }
}
