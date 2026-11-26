package com.scrollguard.app.domain.model

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val foregroundDurationMs: Long,
    val date: String
)

data class DailyReport(
    val date: String,
    val totalScreenTimeMs: Long,
    val blockedAttempts: Int,
    val focusDurationMs: Long,
    val applicationUsageSummary: Map<String, Long>
) {
    /**
     * Explicit, transparent estimate: each blocked short-form or restricted app attempt
     * is estimated at 5 minutes of avoided mindless scrolling. Always displayed separately
     * from measured foreground time.
     */
    val estimatedReclaimedMinutes: Int
        get() = blockedAttempts * ESTIMATED_MINUTES_PER_BLOCKED_ATTEMPT

    companion object {
        const val ESTIMATED_MINUTES_PER_BLOCKED_ATTEMPT = 5
    }
}

data class BlockedAttempt(
    val id: Long = 0L,
    val packageName: String,
    val contentType: ContentCategory,
    val timestamp: Long,
    val reason: String,
    val confidence: Float
)

data class DiagnosticEvent(
    val id: Long,
    val timestamp: Long,
    val packageName: String,
    val eventType: String,
    val screenClassification: String = "Unknown",
    val matchedRule: String,
    val confidence: Float,
    val signals: List<String>,
    val decision: String,
    val navigationAction: String = "NONE",
    val sessionState: String = "NORMAL",
    val cooldownActive: Boolean = false
)
