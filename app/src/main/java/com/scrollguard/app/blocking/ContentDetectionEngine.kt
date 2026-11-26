package com.scrollguard.app.blocking

import com.scrollguard.app.blocking.adapter.CustomRuleContentAdapter
import com.scrollguard.app.blocking.adapter.FacebookContentAdapter
import com.scrollguard.app.blocking.adapter.InstagramContentAdapter
import com.scrollguard.app.blocking.adapter.PlatformContentAdapter
import com.scrollguard.app.blocking.adapter.SnapchatContentAdapter
import com.scrollguard.app.blocking.adapter.TikTokContentAdapter
import com.scrollguard.app.blocking.adapter.YouTubeContentAdapter
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.monitoring.DiagnosticsLogBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentDetectionEngine @Inject constructor(
    instagramAdapter: InstagramContentAdapter,
    youtubeAdapter: YouTubeContentAdapter,
    facebookAdapter: FacebookContentAdapter,
    tiktokAdapter: TikTokContentAdapter,
    snapchatAdapter: SnapchatContentAdapter,
    customRuleAdapter: CustomRuleContentAdapter,
    private val diagnosticsLogBuffer: DiagnosticsLogBuffer
) {
    private val adapters: List<PlatformContentAdapter> = listOf(
        instagramAdapter,
        youtubeAdapter,
        facebookAdapter,
        tiktokAdapter,
        snapchatAdapter,
        customRuleAdapter
    )

    fun evaluate(
        screen: ScreenContext,
        activeSelectiveRules: List<BlockingRule>
    ): ContentMatchResult? {
        if (activeSelectiveRules.isEmpty() || screen.nodes.isEmpty()) return null

        val matchingPairs = activeSelectiveRules.mapNotNull { rule ->
            if (rule.blockType != BlockType.SELECTIVE_CONTENT) return@mapNotNull null
            val adapter = adapters.firstOrNull { it.supports(rule, screen.packageName) }
                ?: return@mapNotNull null
            rule to adapter
        }
        if (matchingPairs.isEmpty()) return null

        var bestMatch: ContentMatchResult? = null
        var fallbackUnmatched: ContentMatchResult? = null
        val discoveredEntryPoints = mutableListOf<EntryPointTarget>()
        var worstUiHealth = AdapterUiHealth.HEALTHY

        for ((rule, adapter) in matchingPairs) {
            val result = runCatching {
                adapter.evaluate(screen, rule)
            }.onFailure { err ->
                diagnosticsLogBuffer.record(
                    packageName = screen.packageName,
                    eventType = screen.eventTypeName,
                    matchedRule = rule.contentType.displayName,
                    confidence = 0f,
                    signals = listOf("adapter_error:${err.javaClass.simpleName}"),
                    decision = "SAFE_FAIL_ALLOW"
                )
            }.getOrNull() ?: continue

            if (result.entryPoints.isNotEmpty()) {
                discoveredEntryPoints.addAll(result.entryPoints)
            }
            if (result.uiHealth.ordinal > worstUiHealth.ordinal) {
                worstUiHealth = result.uiHealth
            }

            if (result.matched) {
                if (bestMatch == null || result.confidence > bestMatch.confidence) {
                    bestMatch = result
                }
            } else if (fallbackUnmatched == null ||
                result.screenClassification != ScreenClassification.UNKNOWN
            ) {
                if (fallbackUnmatched == null || fallbackUnmatched.screenClassification == ScreenClassification.UNKNOWN) {
                    fallbackUnmatched = result
                }
            }
        }

        val combinedEntryPoints = discoveredEntryPoints
            .distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }
            .take(MAX_ENTRY_POINTS_PER_SCREEN)

        return when {
            bestMatch != null -> bestMatch.copy(
                entryPoints = combinedEntryPoints,
                uiHealth = worstUiHealth
            )
            fallbackUnmatched != null -> fallbackUnmatched.copy(
                entryPoints = combinedEntryPoints,
                uiHealth = worstUiHealth
            )
            else -> null
        }
    }

    companion object {
        private const val MAX_ENTRY_POINTS_PER_SCREEN = 2
    }
}
