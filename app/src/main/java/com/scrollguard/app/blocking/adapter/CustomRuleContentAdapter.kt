package com.scrollguard.app.blocking.adapter

import com.scrollguard.app.blocking.ContentMatchResult
import com.scrollguard.app.blocking.DetectionConfidenceEvaluator
import com.scrollguard.app.blocking.ScreenContext
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomRuleContentAdapter @Inject constructor(
    private val confidenceEvaluator: DetectionConfidenceEvaluator
) : PlatformContentAdapter {

    override fun supports(rule: BlockingRule, activePackage: String): Boolean {
        return rule.contentType == ContentCategory.CUSTOM_SHORT_FORM &&
            rule.packageName == activePackage
    }

    override fun evaluate(screen: ScreenContext, rule: BlockingRule): ContentMatchResult {
        val uiHealth = AdapterNodeHelpers.inferUiHealth(screen)
        val hasImmersive = AdapterNodeHelpers.hasImmersiveVerticalContainer(screen)
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
            ruleName = "CustomRuleContentAdapter (${rule.appName})",
            uiHealth = uiHealth
        )
    }
}
