package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.DetectionConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes multi-signal detection confidence from structural accessibility cues.
 * Text keywords alone are capped below the default threshold so incidental text
 * (e.g., a message saying "watch this reel") never triggers a false positive.
 */
@Singleton
class DetectionConfidenceEvaluator @Inject constructor() {

    fun evaluate(
        screen: ScreenContext,
        config: DetectionConfig,
        hasImmersiveFeed: Boolean = false,
        hasSelectedNavTab: Boolean = false,
        strongExclusionsPresent: Boolean = false
    ): EvaluationScore {
        if (screen.nodes.isEmpty()) {
            return EvaluationScore(confidence = 0f, signals = emptyList())
        }

        val signals = mutableListOf<String>()
        var score = 0f

        val matchedResourceIds = screen.nodes.any { node ->
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            id.isNotEmpty() && config.resourceIdKeywords.any { kw -> id.contains(kw.lowercase()) }
        }
        if (matchedResourceIds) {
            score += WEIGHT_RESOURCE_ID
            signals.add("resource_id_match")
        }

        if (hasSelectedNavTab) {
            score += WEIGHT_SELECTED_TAB
            signals.add("selected_nav_tab")
        }

        val matchedContentDesc = screen.nodes.any { node ->
            val desc = node.contentDescription?.lowercase().orEmpty()
            desc.isNotEmpty() && config.contentDescriptionKeywords.any { kw ->
                desc.contains(kw.lowercase())
            }
        }
        if (matchedContentDesc) {
            score += WEIGHT_CONTENT_DESC
            signals.add("content_desc_match")
        }

        if (hasImmersiveFeed) {
            score += WEIGHT_IMMERSIVE_FEED
            signals.add("immersive_vertical_feed")
        }

        val matchedText = screen.nodes.any { node ->
            val text = node.text?.lowercase().orEmpty()
            text.isNotEmpty() && config.textKeywords.any { kw -> text.equals(kw.lowercase()) || text.contains(kw.lowercase()) }
        }
        if (matchedText) {
            score += WEIGHT_TEXT_ONLY
            signals.add("text_keyword_match")
        }

        val exclusionFound = strongExclusionsPresent || hasExclusionSignals(screen, config)
        if (exclusionFound && !matchedResourceIds) {
            score -= PENALTY_EXCLUSION
            signals.add("exclusion_context_penalty")
        }

        val bounded = score.coerceIn(0f, 1f)
        return EvaluationScore(confidence = bounded, signals = signals)
    }

    private fun hasExclusionSignals(screen: ScreenContext, config: DetectionConfig): Boolean {
        if (config.exclusionKeywords.isEmpty()) return false
        return screen.nodes.any { node ->
            val combined = buildString {
                node.viewIdResourceName?.let { append(it).append(' ') }
                node.contentDescription?.let { append(it).append(' ') }
                node.text?.let { append(it) }
            }.lowercase()
            combined.isNotBlank() && config.exclusionKeywords.any { excl ->
                combined.contains(excl.lowercase())
            }
        }
    }

    data class EvaluationScore(
        val confidence: Float,
        val signals: List<String>
    )

    companion object {
        private const val WEIGHT_RESOURCE_ID = 0.45f
        private const val WEIGHT_SELECTED_TAB = 0.35f
        private const val WEIGHT_CONTENT_DESC = 0.25f
        private const val WEIGHT_IMMERSIVE_FEED = 0.20f
        private const val WEIGHT_TEXT_ONLY = 0.15f
        private const val PENALTY_EXCLUSION = 0.45f
    }
}
