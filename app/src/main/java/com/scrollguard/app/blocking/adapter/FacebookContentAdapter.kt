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
class FacebookContentAdapter @Inject constructor(
    private val confidenceEvaluator: DetectionConfidenceEvaluator
) : PlatformContentAdapter {

    override fun supports(rule: BlockingRule, activePackage: String): Boolean {
        return activePackage in KnownPlatforms.FACEBOOK_PACKAGES &&
            rule.contentType == ContentCategory.FACEBOOK_REELS
    }

    override fun evaluate(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        val uiHealth = AdapterNodeHelpers.inferUiHealth(screen)

        // Preserve normal Feed, Groups, Pages, Profiles, Marketplace, Notifications, and Messenger
        val isNormalFacebookSurface = screen.nodes.any { node ->
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            (node.isSelected && (
                desc.contains("marketplace") ||
                    desc.contains("groups") ||
                    desc.contains("pages") ||
                    desc.contains("profile") ||
                    desc.contains("notifications") ||
                    desc.contains("home")
                )) ||
                text == "what's on your mind?"
        }

        val hasReelsResource = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.contains("reels_viewer") ||
                id.contains("fb_shorts") ||
                id.contains("short_form_video")
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

        val entryPoints = screen.nodes.mapNotNull { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val isReelsTabButton = !node.isSelected &&
                (id.contains("reels_tab") || desc == "reels" || desc.startsWith("reels,") || desc.contains("reels tab"))
            if (isReelsTabButton) {
                AdapterNodeHelpers.toSafeEntryPointTarget(
                    node = node,
                    screen = screen,
                    category = ContentCategory.FACEBOOK_REELS,
                    label = "Reels Tab"
                )
            } else {
                null
            }
        }.take(2)

        if (isNormalFacebookSurface && !hasReelsResource && !hasSelectedReelsTab) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.FACEBOOK_REELS,
                confidence = 0f,
                matchedSignals = listOf("facebook_normal_surface_protected"),
                ruleName = "FacebookContentAdapter",
                entryPoints = entryPoints,
                uiHealth = uiHealth
            )
        }

        val hasImmersivePager = AdapterNodeHelpers.hasImmersiveVerticalContainer(screen)
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
            ruleName = "FacebookContentAdapter",
            entryPoints = entryPoints,
            uiHealth = uiHealth
        )
    }
}
