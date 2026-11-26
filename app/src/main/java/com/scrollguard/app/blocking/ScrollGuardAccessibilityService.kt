package com.scrollguard.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.repository.BlockedAttemptRepository
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.monitoring.DiagnosticsLogBuffer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScrollGuardAccessibilityService : AccessibilityService() {

    @Inject lateinit var ruleRepository: DetectionRuleRepository
    @Inject lateinit var blockingRuleRepository: BlockingRuleRepository
    @Inject lateinit var decisionEngine: BlockingDecisionEngine
    @Inject lateinit var overlayManager: BlockingOverlayManager
    @Inject lateinit var blockedAttemptRepository: BlockedAttemptRepository
    @Inject lateinit var preferences: ScrollGuardPreferences
    @Inject lateinit var diagnosticsLogBuffer: DiagnosticsLogBuffer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastProcessedTimeMs = 0L
    private var lastPackageName: String? = null
    private var lastAttemptLogKey: String? = null
    private var lastAttemptLogTimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            blockingRuleRepository.seedBuiltInRulesIfMissing()
            ruleRepository.refreshNow()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        // Ignore our own overlay/app events to prevent self-triggering loops
        if (pkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.className?.toString()?.contains("MainActivity") == true
            ) {
                overlayManager.dismiss()
            }
            return
        }

        // Throttle noisy content-changed events while reacting immediately on window/click/tab changes
        val nowElapsed = SystemClock.elapsedRealtime()
        val isHighPriorityEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED

        if (!isHighPriorityEvent &&
            pkg == lastPackageName &&
            nowElapsed - lastProcessedTimeMs < CONTENT_EVENT_DEBOUNCE_MS
        ) {
            return
        }
        lastProcessedTimeMs = nowElapsed
        lastPackageName = pkg

        // Quickly dismiss overlay if user switched to a launcher or exempt system app
        if (pkg in KnownPlatforms.SYSTEM_EXEMPT_PACKAGES) {
            overlayManager.dismiss()
            return
        }

        val rules = ruleRepository.cachedRules
        val schedules = ruleRepository.cachedSchedules
        val hasRelevantRules = rules.any {
            it.packageName == pkg ||
                (it.contentType == ContentCategory.FACEBOOK_REELS && pkg == KnownPlatforms.FACEBOOK_LITE_PACKAGE)
        }
        val hasRelevantSchedules = schedules.any {
            it.enabled && (it.affectedPackages.isEmpty() || pkg in it.affectedPackages)
        }

        if (!hasRelevantRules && !hasRelevantSchedules) {
            overlayManager.dismiss()
            return
        }

        val hasActiveSelectiveSchedule = schedules.any {
            it.enabled && it.appliesToSelectiveContent &&
                (it.affectedPackages.isEmpty() || pkg in it.affectedPackages)
        }
        val nodes = extractSnapshotIfNeeded(pkg, rules, hasActiveSelectiveSchedule)
        val displayMetrics = resources.displayMetrics
        val screenContext = ScreenContext(
            packageName = pkg,
            eventTypeName = eventTypeName(event.eventType),
            windowClassName = event.className?.toString(),
            screenWidthPx = displayMetrics.widthPixels,
            screenHeightPx = displayMetrics.heightPixels,
            nodes = nodes
        )

        val strictState = ruleRepository.cachedStrictModeState
        val decision = decisionEngine.decide(
            screen = screenContext,
            rules = rules,
            schedules = schedules,
            strictModeState = strictState,
            todayUsageMsByPackage = ruleRepository.cachedTodayUsageByPackage,
            selfPackageName = packageName
        )

        if (ruleRepository.cachedDiagnosticsEnabled) {
            diagnosticsLogBuffer.record(
                packageName = pkg,
                eventType = screenContext.eventTypeName,
                matchedRule = decision.contentCategory.displayName,
                confidence = decision.confidence,
                signals = decision.matchedSignals,
                decision = if (decision.shouldBlock) "BLOCK (${decision.reason.name})" else "ALLOW (${decision.reason.name})"
            )
        }

        if (decision.shouldBlock) {
            handleBlockDecision(decision, strictState)
        } else {
            overlayManager.dismiss()
        }
    }

    private fun handleBlockDecision(
        decision: BlockDecision,
        strictState: com.scrollguard.app.domain.model.StrictModeState
    ) {
        val nowMs = System.currentTimeMillis()
        val attemptKey = "${decision.packageName}:${decision.contentCategory.name}"
        val shouldRecordAttempt = attemptKey != lastAttemptLogKey ||
            (nowMs - lastAttemptLogTimeMs) > ATTEMPT_LOG_COOLDOWN_MS

        if (shouldRecordAttempt) {
            lastAttemptLogKey = attemptKey
            lastAttemptLogTimeMs = nowMs
        }

        serviceScope.launch {
            if (shouldRecordAttempt) {
                blockedAttemptRepository.recordAttempt(
                    packageName = decision.packageName,
                    contentType = decision.contentCategory,
                    reason = decision.reason.label,
                    confidence = decision.confidence
                )
            }
            val todayCount = blockedAttemptRepository.getTodayCount()
            val activeFocus = strictState.focusSessionActive && strictState.focusSessionEndEpochMs > nowMs
            val allowTempUnlock = (!strictState.isStrictModeEnabled && !activeFocus) ||
                strictState.remainingTempUnlocks > 0

            overlayManager.showOrUpdate(
                decision = decision,
                dailyAttemptCount = todayCount,
                allowTempUnlock = allowTempUnlock && strictState.remainingTempUnlocks > 0,
                remainingUnlocks = strictState.remainingTempUnlocks,
                onReturn = {
                    navigateSafelyOut(decision.contentCategory == ContentCategory.FULL_APP)
                },
                onTempUnlock = { targetPkg ->
                    serviceScope.launch {
                        preferences.grantTemporaryUnlock(
                            packageName = targetPkg,
                            expiresEpochMs = System.currentTimeMillis() + TEMP_UNLOCK_DURATION_MS
                        )
                    }
                }
            )
        }
    }

    private fun navigateSafelyOut(isFullAppBlock: Boolean) {
        if (isFullAppBlock) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            val wentBack = performGlobalAction(GLOBAL_ACTION_BACK)
            if (!wentBack) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun extractSnapshotIfNeeded(
        pkg: String,
        rules: List<com.scrollguard.app.domain.model.BlockingRule>,
        hasActiveSelectiveSchedule: Boolean
    ): List<ScreenNodeSnapshot> {
        val hasFullAppRule = rules.any {
            it.enabled && it.blockType == BlockType.FULL_APP && it.packageName == pkg &&
                (it.dailyLimitMinutes == null || it.dailyLimitMinutes <= 0)
        }
        if (hasFullAppRule) return emptyList()

        val needsSelectiveInspection = rules.any {
            it.blockType == BlockType.SELECTIVE_CONTENT &&
                (it.enabled || hasActiveSelectiveSchedule) &&
                (it.packageName == pkg ||
                    (it.contentType == ContentCategory.FACEBOOK_REELS && pkg == KnownPlatforms.FACEBOOK_LITE_PACKAGE))
        }
        if (!needsSelectiveInspection) return emptyList()

        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return emptyList()
        val snapshots = ArrayList<ScreenNodeSnapshot>(64)
        try {
            collectNodes(root, depth = 0, out = snapshots)
        } finally {
            recycleNode(root)
        }
        return snapshots
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: MutableList<ScreenNodeSnapshot>
    ) {
        if (depth > MAX_NODE_DEPTH || out.size >= MAX_SNAPSHOT_NODES) return

        val rect = Rect()
        node.getBoundsInScreen(rect)
        out.add(
            ScreenNodeSnapshot(
                viewIdResourceName = node.viewIdResourceName,
                className = node.className?.toString(),
                contentDescription = node.contentDescription?.toString()?.take(MAX_LABEL_LENGTH),
                text = node.text?.toString()?.take(MAX_LABEL_LENGTH),
                isSelected = node.isSelected,
                isClickable = node.isClickable,
                isScrollable = node.isScrollable,
                boundsWidth = rect.width(),
                boundsHeight = rect.height()
            )
        )

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (out.size >= MAX_SNAPSHOT_NODES) break
            val child = node.getChild(i) ?: continue
            try {
                collectNodes(child, depth + 1, out)
            } finally {
                recycleNode(child)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { node.recycle() }
        }
    }

    private fun eventTypeName(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
        else -> "EVENT_$eventType"
    }

    override fun onInterrupt() {
        overlayManager.dismiss()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlayManager.dismiss()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        overlayManager.dismiss()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CONTENT_EVENT_DEBOUNCE_MS = 180L
        private const val ATTEMPT_LOG_COOLDOWN_MS = 4_000L
        private const val TEMP_UNLOCK_DURATION_MS = 5 * 60_000L
        private const val MAX_NODE_DEPTH = 14
        private const val MAX_SNAPSHOT_NODES = 120
        private const val MAX_LABEL_LENGTH = 48
    }
}
