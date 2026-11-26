package com.scrollguard.app.blocking.adapter

import com.scrollguard.app.blocking.ContentMatchResult
import com.scrollguard.app.blocking.DetectionConfidenceEvaluator
import com.scrollguard.app.blocking.KnownPlatforms
import com.scrollguard.app.blocking.ScreenContext
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapchat Spotlight adapter.
 * Targets Spotlight short-video feeds while preserving Messaging / Chat and ordinary Camera screens.
 */
@Singleton
class SnapchatContentAdapter @Inject constructor(
    private val confidenceEvaluator: DetectionConfidenceEvaluator
) : PlatformContentAdapter {

    override fun supports(rule: BlockingRule, activePackage: String): Boolean {
        return activePackage == KnownPlatforms.SNAPCHAT_PACKAGE &&
            rule.packageName == KnownPlatforms.SNAPCHAT_PACKAGE &&
            rule.contentType == ContentCategory.SNAPCHAT_SPOTLIGHT
    }

    override fun evaluate(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        val uiHealth = AdapterNodeHelpers.inferUiHealth(screen)

        // Preserve ordinary Camera and Chat/Messaging functionality
        val inCameraOrChat = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("camera_capture_button") ||
                id.contains("camera_flip_button") ||
                id.contains("chat_list") ||
                id.contains("friends_feed") ||
                desc.contains("take a snap") ||
                text == "send a chat" ||
                (node.isSelected && (desc == "chat" || desc == "camera" || text == "chat"))
        }

        val hasSpotlightResource = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            !id.contains("spotlight_tab") && (
                id.contains("snap_spotlight") ||
                    id.contains("spotlight_feed") ||
                    id.contains("spotlight_player") ||
                    id.contains("spotlight_viewer")
                )
        }

        val hasSelectedSpotlightTab = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            node.isSelected && (
                id.contains("spotlight_tab") ||
                    desc == "spotlight" ||
                    desc.contains("spotlight tab") ||
                    text == "spotlight"
                )
        }

        val entryPoints = screen.nodes.mapNotNull { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val isSpotlightTab = !node.isSelected &&
                (id.contains("spotlight_tab") || desc == "spotlight" || desc.contains("spotlight tab"))
            if (isSpotlightTab) {
                AdapterNodeHelpers.toSafeEntryPointTarget(
                    node = node,
                    screen = screen,
                    category = ContentCategory.SNAPCHAT_SPOTLIGHT,
                    label = "Spotlight Tab"
                )
            } else {
                null
            }
        }.take(2)

        if (inCameraOrChat && !hasSpotlightResource && !hasSelectedSpotlightTab) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.SNAPCHAT_SPOTLIGHT,
                confidence = 0f,
                matchedSignals = listOf("snapchat_camera_or_chat_protected"),
                ruleName = "SnapchatContentAdapter",
                entryPoints = entryPoints,
                uiHealth = uiHealth
            )
        }

        val hasImmersivePager = AdapterNodeHelpers.hasImmersiveVerticalContainer(screen)
        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasImmersivePager,
            hasSelectedNavTab = hasSelectedSpotlightTab,
            strongExclusionsPresent = inCameraOrChat
        )

        val confidence = when {
            hasSpotlightResource && hasImmersivePager -> maxOf(score.confidence, 0.88f)
            hasSelectedSpotlightTab -> maxOf(score.confidence, 0.82f)
            else -> score.confidence
        }

        return ContentMatchResult(
            matched = confidence >= rule.detectionConfig.minConfidence,
            category = ContentCategory.SNAPCHAT_SPOTLIGHT,
            confidence = confidence,
            matchedSignals = score.signals,
            ruleName = "SnapchatContentAdapter",
            entryPoints = entryPoints,
            uiHealth = uiHealth
        )
    }
}
