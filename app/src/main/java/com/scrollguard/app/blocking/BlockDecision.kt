package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.ContentCategory

enum class BlockingReason(val label: String) {
    NONE("Allowed"),
    EXEMPT_SELF_OR_SYSTEM("Exempt system or ScrollGuard app"),
    PAUSED_NORMAL_MODE("Blocking paused in Normal Mode"),
    TEMPORARY_UNLOCK_ACTIVE("Temporary unlock active"),
    FULL_APP_RULE("Full application block enabled"),
    DAILY_LIMIT_EXCEEDED("Daily usage limit reached"),
    SCHEDULED_FULL_APP("Active scheduled full-app restriction"),
    SELECTIVE_CONTENT_RULE("Selective short-form content rule"),
    SCHEDULED_SELECTIVE_CONTENT("Active scheduled selective restriction")
}

data class BlockDecision(
    val shouldBlock: Boolean,
    val packageName: String,
    val appName: String,
    val contentCategory: ContentCategory,
    val reason: BlockingReason,
    val confidence: Float,
    val matchedSignals: List<String> = emptyList(),
    val messageTitle: String = "",
    val messageBody: String = "",
    val entryPointsToSuppress: List<EntryPointTarget> = emptyList(),
    val uiHealth: AdapterUiHealth = AdapterUiHealth.HEALTHY,
    val adapterName: String = "",
    val screenClassification: ScreenClassification = ScreenClassification.UNKNOWN,
    val minConfidenceRequired: Float = 0.65f,
    val failureReason: String? = null
) {
    companion object {
        fun allow(
            packageName: String,
            appName: String = packageName,
            reason: BlockingReason = BlockingReason.NONE,
            contentCategory: ContentCategory = ContentCategory.FULL_APP,
            confidence: Float = 0f,
            matchedSignals: List<String> = emptyList(),
            entryPointsToSuppress: List<EntryPointTarget> = emptyList(),
            uiHealth: AdapterUiHealth = AdapterUiHealth.HEALTHY,
            adapterName: String = "",
            screenClassification: ScreenClassification = ScreenClassification.UNKNOWN,
            minConfidenceRequired: Float = 0.65f,
            failureReason: String? = null
        ) = BlockDecision(
            shouldBlock = false,
            packageName = packageName,
            appName = appName,
            contentCategory = contentCategory,
            reason = reason,
            confidence = confidence,
            matchedSignals = matchedSignals,
            entryPointsToSuppress = entryPointsToSuppress,
            uiHealth = uiHealth,
            adapterName = adapterName,
            screenClassification = screenClassification,
            minConfidenceRequired = minConfidenceRequired,
            failureReason = failureReason
        )
    }
}
