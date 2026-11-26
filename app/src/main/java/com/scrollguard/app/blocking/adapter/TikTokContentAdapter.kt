package com.scrollguard.app.blocking.adapter

import com.scrollguard.app.blocking.AdapterUiHealth
import com.scrollguard.app.blocking.ContentMatchResult
import com.scrollguard.app.blocking.DetectionConfidenceEvaluator
import com.scrollguard.app.blocking.KnownPlatforms
import com.scrollguard.app.blocking.ScreenContext
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TikTok adapter.
 * TikTok's primary surface is a full-screen short-video feed and uses custom/obfuscated views;
 * this adapter detects short-video browsing feeds when exposed and never claims that general
 * TikTok browsing can be cleanly separated without full-app or restricted-mode blocking.
 */
@Singleton
class TikTokContentAdapter @Inject constructor(
    private val confidenceEvaluator: DetectionConfidenceEvaluator
) : PlatformContentAdapter {

    override fun supports(rule: BlockingRule, activePackage: String): Boolean {
        return activePackage in KnownPlatforms.TIKTOK_PACKAGES &&
            rule.contentType == ContentCategory.TIKTOK_SHORT_FORM
    }

    override fun evaluate(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        val rawHealth = AdapterNodeHelpers.inferUiHealth(screen)

        // Preserve Inbox / Direct Messages / Profile Settings if clearly identifiable
        val inInboxOrProfile = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            (node.isSelected && (desc == "inbox" || desc == "profile" || text == "inbox" || text == "profile")) ||
                id.contains("chat_message") ||
                text == "direct messages"
        }

        val hasFeedPager = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.lowercase().orEmpty()
            val text = node.text?.lowercase().orEmpty()
            id.contains("feed_pager") ||
                id.contains("vertical_view_pager") ||
                (node.isSelected && (text == "for you" || text == "following" || desc == "for you" || desc == "following"))
        }

        val hasImmersivePager = AdapterNodeHelpers.hasImmersiveVerticalContainer(screen)

        if (inInboxOrProfile && !hasFeedPager) {
            return ContentMatchResult(
                matched = false,
                category = ContentCategory.TIKTOK_SHORT_FORM,
                confidence = 0f,
                matchedSignals = listOf("tiktok_inbox_or_profile"),
                ruleName = "TikTokContentAdapter",
                uiHealth = rawHealth
            )
        }

        val score = confidenceEvaluator.evaluate(
            screen = screen,
            config = rule.detectionConfig,
            hasImmersiveFeed = hasImmersivePager || hasFeedPager,
            hasSelectedNavTab = hasFeedPager,
            strongExclusionsPresent = inInboxOrProfile
        )

        val confidence = when {
            hasFeedPager && hasImmersivePager -> maxOf(score.confidence, 0.85f)
            hasFeedPager -> maxOf(score.confidence, 0.75f)
            else -> score.confidence
        }

        val uiHealth = if (screen.nodes.size <= 2) AdapterUiHealth.LIMITED_VISIBILITY else rawHealth
        return ContentMatchResult(
            matched = confidence >= rule.detectionConfig.minConfidence,
            category = ContentCategory.TIKTOK_SHORT_FORM,
            confidence = confidence,
            matchedSignals = score.signals,
            ruleName = "TikTokContentAdapter",
            uiHealth = uiHealth
        )
    }
}
