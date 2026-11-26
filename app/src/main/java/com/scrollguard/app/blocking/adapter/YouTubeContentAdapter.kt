package com.scrollguard.app.blocking.adapter

import com.scrollguard.app.blocking.ContentMatchResult
import com.scrollguard.app.blocking.DetectionConfidenceEvaluator
import com.scrollguard.app.blocking.KnownPlatforms
import com.scrollguard.app.blocking.ScreenContext
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeContentAdapter @Inject constructor(
    private val confidenceEvaluator: DetectionConfidenceEvaluator
) : PlatformContentAdapter {

    override fun supports(rule: BlockingRule, activePackage: String): Boolean {
        return activePackage == KnownPlatforms.YOUTUBE_PACKAGE &&
            rule.packageName == KnownPlatforms.YOUTUBE_PACKAGE &&
            rule.contentType == ContentCategory.YOUTUBE_SHORTS
    }

    override fun evaluate(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        val uiHealth = AdapterNodeHelpers.inferUiHealth(screen)

        // Preserve normal videos, Search, Subscriptions, Playlists, and Comments
        val isNormalYouTubeSurface = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("watch_player") ||
                id.contains("watch_while_time_bar") ||
                id.contains("search_edit_text") ||
                id.contains("comments_entry_point") ||
                id.contains("comment_composer") ||
                id.contains("playlist") ||
                (id.contains("pivot_subscriptions") && node.isSelected) ||
                (node.isSelected && desc.equals("subscriptions", ignoreCase = true)) ||
                (node.isSelected && desc.equals("home", ignoreCase = true)) ||
                (node.isSelected && desc.equals("you", ignoreCase = true)) ||
                text == "up next" ||
                text == "comments"
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

        val entryPoints = screen.nodes.mapNotNull { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val isShortsNavButton = !node.isSelected &&
                (id.contains("pivot_shorts") || desc == "shorts" || desc == "shorts tab")
            if (isShortsNavButton) {
                AdapterNodeHelpers.toSafeEntryPointTarget(
                    node = node,
                    screen = screen,
                    category = ContentCategory.YOUTUBE_SHORTS,
                    label = "Shorts Tab"
                )
            } else {
                null
            }
        }.take(2)

        if (isNormalYouTubeSurface && !hasShortsPlayerId && !hasSelectedShortsTab) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.YOUTUBE_SHORTS,
                confidence = 0f,
                matchedSignals = listOf("youtube_normal_surface_protected"),
                ruleName = "YouTubeContentAdapter",
                entryPoints = entryPoints,
                uiHealth = uiHealth
            )
        }

        val hasImmersivePager = AdapterNodeHelpers.hasImmersiveVerticalContainer(screen)
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
            ruleName = "YouTubeContentAdapter",
            entryPoints = entryPoints,
            uiHealth = uiHealth
        )
    }
}
