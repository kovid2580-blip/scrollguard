package com.scrollguard.app.monitoring

import com.scrollguard.app.blocking.AdapterUiHealth
import com.scrollguard.app.blocking.ScreenClassification
import com.scrollguard.app.domain.model.ContentCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class AppAdapterStatus(
    val packageName: String,
    val lastCategory: ContentCategory,
    val screenClassification: ScreenClassification = ScreenClassification.UNKNOWN,
    val lastDecision: String,
    val lastConfidence: Float,
    val activeEntryPointSuppressedCount: Int,
    val detectedEntryPointBounds: String = "None",
    val entryPointOverlayActive: Boolean = false,
    val uiHealth: AdapterUiHealth,
    val consecutiveEmptyOrUnmatchedCount: Int,
    val lastFailureReason: String? = null,
    val updatedAtMs: Long
)

data class LiveDiagnosticsSnapshot(
    val foregroundPackage: String = "None",
    val screenClassification: String = ScreenClassification.UNKNOWN.label,
    val reelsConfidence: Float = 0f,
    val entryPointBounds: String = "Not detected",
    val entryPointOverlayActive: Boolean = false,
    val navigationState: String = "NORMAL",
    val navigationActionCount: Int = 0,
    val lastNavigationAction: String = "NONE",
    val cooldownActive: Boolean = false,
    val lastBlockingDecision: String = "NONE",
    val lastDetectionFailureReason: String = "None",
    val audioActiveAfterInterrupt: Boolean = false,
    val updatedAtMs: Long = 0L
)

/**
 * Tracks per-app adapter health and live diagnostic state in RAM only.
 * Never persists raw accessibility screen text or screenshots.
 */
@Singleton
class AdapterStatusTracker @Inject constructor() {

    private val _statusByPackage = MutableStateFlow<Map<String, AppAdapterStatus>>(emptyMap())
    val statusByPackage: StateFlow<Map<String, AppAdapterStatus>> = _statusByPackage.asStateFlow()

    private val _liveSnapshot = MutableStateFlow(LiveDiagnosticsSnapshot())
    val liveSnapshot: StateFlow<LiveDiagnosticsSnapshot> = _liveSnapshot.asStateFlow()

    fun update(
        packageName: String,
        category: ContentCategory,
        screenClassification: ScreenClassification = ScreenClassification.UNKNOWN,
        decision: String,
        confidence: Float,
        suppressedEntryPoints: Int,
        entryPointBoundsSummary: String = "Not detected",
        entryPointOverlayActive: Boolean = false,
        uiHealth: AdapterUiHealth,
        nodeCount: Int,
        navigationState: String = "NORMAL",
        navigationActionCount: Int = 0,
        lastNavigationAction: String = "NONE",
        cooldownActive: Boolean = false,
        failureReason: String? = null,
        audioActiveAfterInterrupt: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        _statusByPackage.update { current ->
            val prev = current[packageName]
            val emptyStreak = if (nodeCount == 0) {
                (prev?.consecutiveEmptyOrUnmatchedCount ?: 0) + 1
            } else {
                0
            }
            val effectiveHealth = when {
                emptyStreak >= EMPTY_NODE_WARNING_THRESHOLD -> AdapterUiHealth.NEEDS_RULE_REVIEW
                else -> uiHealth
            }
            current + (
                packageName to AppAdapterStatus(
                    packageName = packageName,
                    lastCategory = category,
                    screenClassification = screenClassification,
                    lastDecision = decision,
                    lastConfidence = confidence,
                    activeEntryPointSuppressedCount = suppressedEntryPoints,
                    detectedEntryPointBounds = entryPointBoundsSummary,
                    entryPointOverlayActive = entryPointOverlayActive,
                    uiHealth = effectiveHealth,
                    consecutiveEmptyOrUnmatchedCount = emptyStreak,
                    lastFailureReason = failureReason ?: prev?.lastFailureReason,
                    updatedAtMs = now
                )
            )
        }

        _liveSnapshot.update { prev ->
            LiveDiagnosticsSnapshot(
                foregroundPackage = packageName,
                screenClassification = screenClassification.label,
                reelsConfidence = confidence,
                entryPointBounds = entryPointBoundsSummary,
                entryPointOverlayActive = entryPointOverlayActive,
                navigationState = navigationState,
                navigationActionCount = navigationActionCount,
                lastNavigationAction = lastNavigationAction,
                cooldownActive = cooldownActive,
                lastBlockingDecision = decision,
                lastDetectionFailureReason = failureReason ?: prev.lastDetectionFailureReason,
                audioActiveAfterInterrupt = audioActiveAfterInterrupt,
                updatedAtMs = now
            )
        }
    }

    fun clearLiveCounters() {
        _liveSnapshot.value = LiveDiagnosticsSnapshot()
    }

    companion object {
        private const val EMPTY_NODE_WARNING_THRESHOLD = 4
    }
}
