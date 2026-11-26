package com.scrollguard.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.repository.BlockedAttemptRepository
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.monitoring.AdapterStatusTracker
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
    @Inject lateinit var navigationStateMachine: BlockingNavigationStateMachine
    @Inject lateinit var overlayManager: BlockingOverlayManager
    @Inject lateinit var entryPointOverlayManager: EntryPointOverlayManager
    @Inject lateinit var blockedAttemptRepository: BlockedAttemptRepository
    @Inject lateinit var preferences: ScrollGuardPreferences
    @Inject lateinit var diagnosticsLogBuffer: DiagnosticsLogBuffer
    @Inject lateinit var adapterStatusTracker: AdapterStatusTracker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastProcessedElapsedMs = 0L
    private var lastPackageName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        navigationStateMachine.reset()
        serviceScope.launch {
            blockingRuleRepository.seedBuiltInRulesIfMissing()
            ruleRepository.refreshNow()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Ignore stale queued accessibility events
        val nowUptime = SystemClock.uptimeMillis()
        if (event.eventTime > 0L && nowUptime - event.eventTime > STALE_EVENT_AGE_MS) {
            return
        }

        val eventPkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        // Ignore our own overlay/app events to prevent self-triggering loops
        if (eventPkg == packageName) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.className?.toString()?.contains("MainActivity") == true
            ) {
                overlayManager.dismiss()
                entryPointOverlayManager.dismissAll()
                navigationStateMachine.reset()
            }
            return
        }

        // Throttle noisy content-changed events while reacting promptly on window/click/tab changes
        val nowElapsed = SystemClock.elapsedRealtime()
        val isHighPriorityEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED

        if (!isHighPriorityEvent &&
            eventPkg == lastPackageName &&
            nowElapsed - lastProcessedElapsedMs < CONTENT_EVENT_DEBOUNCE_MS
        ) {
            return
        }
        lastProcessedElapsedMs = nowElapsed

        if (lastPackageName != eventPkg) {
            navigationStateMachine.onAppSwitched(eventPkg)
            entryPointOverlayManager.dismissAll()
            lastPackageName = eventPkg
        }

        // Dismiss overlays cleanly if user switched to launcher, keyboard, notification shade, or system UI
        if (eventPkg in KnownPlatforms.SYSTEM_EXEMPT_PACKAGES) {
            overlayManager.dismiss()
            entryPointOverlayManager.dismissAll()
            navigationStateMachine.reset()
            return
        }

        val rules = ruleRepository.cachedRules
        val schedules = ruleRepository.cachedSchedules
        val hasRelevantRules = rules.any { matchesRulePackage(eventPkg, it.packageName, it.contentType) }
        val hasRelevantSchedules = schedules.any {
            it.enabled && (it.affectedPackages.isEmpty() || eventPkg in it.affectedPackages)
        }

        if (!hasRelevantRules && !hasRelevantSchedules) {
            overlayManager.dismiss()
            entryPointOverlayManager.dismissAll()
            return
        }

        val imeVisible = isInputMethodVisible()
        if (imeVisible) {
            entryPointOverlayManager.dismissAll()
        }

        val hasActiveSelectiveSchedule = schedules.any {
            it.enabled && it.appliesToSelectiveContent &&
                (it.affectedPackages.isEmpty() || eventPkg in it.affectedPackages)
        }
        val nodes = extractSnapshotIfNeeded(eventPkg, rules, hasActiveSelectiveSchedule)
        val displayMetrics = resources.displayMetrics
        val screenContext = ScreenContext(
            packageName = eventPkg,
            eventTypeName = eventTypeName(event.eventType),
            windowClassName = event.className?.toString(),
            screenWidthPx = displayMetrics.widthPixels,
            screenHeightPx = displayMetrics.heightPixels,
            nodes = nodes,
            isImeVisible = imeVisible
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

        val audioActive = isAudioPlaying()
        val actionPlan = navigationStateMachine.evaluateTransition(
            decision = decision,
            screenClassification = decision.screenClassification,
            rawConfidence = decision.confidence,
            minConfidence = decision.minConfidenceRequired,
            nowMs = System.currentTimeMillis(),
            audioActive = audioActive
        )

        val suppressEntryPoints = !decision.shouldBlock &&
            !actionPlan.showOverlay &&
            !imeVisible &&
            ruleRepository.cachedEntryPointSuppressionEnabled &&
            decision.entryPointsToSuppress.isNotEmpty()

        val decisionLabel = if (decision.shouldBlock) {
            "BLOCK (${decision.reason.name})"
        } else {
            "ALLOW (${decision.reason.name})"
        }

        val boundsSummary = if (decision.entryPointsToSuppress.isNotEmpty()) {
            decision.entryPointsToSuppress.joinToString(", ") { it.formatBounds() }
        } else {
            "Not detected"
        }

        adapterStatusTracker.update(
            packageName = eventPkg,
            category = decision.contentCategory,
            screenClassification = decision.screenClassification,
            decision = decisionLabel,
            confidence = decision.confidence,
            suppressedEntryPoints = if (suppressEntryPoints) decision.entryPointsToSuppress.size else 0,
            entryPointBoundsSummary = boundsSummary,
            entryPointOverlayActive = suppressEntryPoints,
            uiHealth = decision.uiHealth,
            nodeCount = nodes.size,
            navigationState = actionPlan.sessionState.name,
            navigationActionCount = actionPlan.totalNavigationActions,
            lastNavigationAction = actionPlan.navigationAction.name,
            cooldownActive = actionPlan.cooldownActive,
            failureReason = actionPlan.failureReason ?: decision.failureReason,
            audioActiveAfterInterrupt = audioActive
        )

        if (ruleRepository.cachedDiagnosticsEnabled) {
            diagnosticsLogBuffer.record(
                packageName = eventPkg,
                eventType = screenContext.eventTypeName,
                screenClassification = decision.screenClassification.label,
                matchedRule = decision.contentCategory.displayName,
                confidence = decision.confidence,
                signals = decision.matchedSignals,
                decision = decisionLabel,
                navigationAction = actionPlan.navigationAction.name,
                sessionState = actionPlan.sessionState.name,
                cooldownActive = actionPlan.cooldownActive
            )
        }

        executeActionPlan(decision, actionPlan, strictState, suppressEntryPoints)
    }

    private fun executeActionPlan(
        decision: BlockDecision,
        plan: NavigationActionPlan,
        strictState: com.scrollguard.app.domain.model.StrictModeState,
        suppressEntryPoints: Boolean
    ) {
        when (plan.navigationAction) {
            NavigationAction.GLOBAL_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            NavigationAction.GLOBAL_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            NavigationAction.NONE -> Unit
        }

        if (plan.showOverlay) {
            entryPointOverlayManager.dismissAll()
            showBlockingOverlay(decision, plan, strictState)
        } else {
            overlayManager.dismiss()
            if (plan.shouldRecordAttempt) {
                serviceScope.launch {
                    blockedAttemptRepository.recordAttempt(
                        packageName = decision.packageName,
                        contentType = decision.contentCategory,
                        reason = decision.reason.label,
                        confidence = decision.confidence
                    )
                }
            }
            if (suppressEntryPoints) {
                entryPointOverlayManager.showOrUpdate(decision.entryPointsToSuppress)
            } else {
                entryPointOverlayManager.dismissAll()
            }
        }
    }

    private fun showBlockingOverlay(
        decision: BlockDecision,
        plan: NavigationActionPlan,
        strictState: com.scrollguard.app.domain.model.StrictModeState
    ) {
        val nowMs = System.currentTimeMillis()
        serviceScope.launch {
            if (plan.shouldRecordAttempt) {
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
            val isFullAppOrTikTok = decision.contentCategory == ContentCategory.FULL_APP ||
                decision.contentCategory == ContentCategory.TIKTOK_SHORT_FORM

            overlayManager.showOrUpdate(
                decision = decision,
                dailyAttemptCount = todayCount,
                allowTempUnlock = allowTempUnlock && strictState.remainingTempUnlocks > 0,
                remainingUnlocks = strictState.remainingTempUnlocks,
                onReturn = {
                    if (isFullAppOrTikTok) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    } else {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
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
                matchesRulePackage(pkg, it.packageName, it.contentType)
        }
        if (!needsSelectiveInspection) return emptyList()

        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return emptyList()
        // Verify root belongs to the active package to avoid stale cross-window reads
        val rootPkg = root.packageName?.toString()
        if (!rootPkg.isNullOrBlank() && rootPkg != pkg) {
            recycleNode(root)
            return emptyList()
        }

        return collectNodesBreadthFirst(root)
    }

    /**
     * Breadth-first bounded node collection skipping decorative wrapper nodes from the snapshot list
     * (while still traversing their children), ensuring bottom navigation bars at the end of
     * deep feed RecyclerViews are consistently captured.
     */
    private fun collectNodesBreadthFirst(root: AccessibilityNodeInfo): List<ScreenNodeSnapshot> {
        val snapshots = ArrayList<ScreenNodeSnapshot>(128)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)

        while (queue.isNotEmpty() && snapshots.size < MAX_SNAPSHOT_NODES) {
            val (node, depth) = queue.removeFirst()
            try {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val id = node.viewIdResourceName
                val desc = node.contentDescription?.toString()?.take(MAX_LABEL_LENGTH)
                val text = node.text?.toString()?.take(MAX_LABEL_LENGTH)
                val cls = node.className?.toString()

                val isMeaningful = !id.isNullOrBlank() ||
                    !desc.isNullOrBlank() ||
                    !text.isNullOrBlank() ||
                    node.isSelected ||
                    node.isScrollable ||
                    node.isClickable ||
                    node.isFocused

                if (isMeaningful) {
                    snapshots.add(
                        ScreenNodeSnapshot(
                            viewIdResourceName = id,
                            className = cls,
                            contentDescription = desc,
                            text = text,
                            isSelected = node.isSelected,
                            isClickable = node.isClickable,
                            isScrollable = node.isScrollable,
                            isFocused = node.isFocused,
                            boundsWidth = rect.width(),
                            boundsHeight = rect.height(),
                            boundsLeft = rect.left,
                            boundsTop = rect.top,
                            boundsRight = rect.right,
                            boundsBottom = rect.bottom
                        )
                    )
                }

                if (depth < MAX_NODE_DEPTH && snapshots.size < MAX_SNAPSHOT_NODES) {
                    val childCount = node.childCount
                    for (i in 0 until childCount) {
                        val child = node.getChild(i) ?: continue
                        queue.addLast(child to (depth + 1))
                    }
                }
            } finally {
                recycleNode(node)
            }
        }

        // Recycle any remaining queued nodes if MAX_SNAPSHOT_NODES was reached
        while (queue.isNotEmpty()) {
            recycleNode(queue.removeFirst().first)
        }
        return snapshots
    }

    private fun isInputMethodVisible(): Boolean {
        return runCatching {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }.getOrDefault(false)
    }

    private fun isAudioPlaying(): Boolean {
        return runCatching {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isMusicActive == true
        }.getOrDefault(false)
    }

    private fun matchesRulePackage(
        activePkg: String,
        rulePkg: String,
        category: ContentCategory
    ): Boolean {
        if (activePkg == rulePkg) return true
        if (category == ContentCategory.FACEBOOK_REELS && activePkg in KnownPlatforms.FACEBOOK_PACKAGES) {
            return true
        }
        if (category == ContentCategory.TIKTOK_SHORT_FORM && activePkg in KnownPlatforms.TIKTOK_PACKAGES) {
            return true
        }
        return false
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
        entryPointOverlayManager.dismissAll()
        navigationStateMachine.reset()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlayManager.dismiss()
        entryPointOverlayManager.dismissAll()
        navigationStateMachine.reset()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        overlayManager.dismiss()
        entryPointOverlayManager.dismissAll()
        navigationStateMachine.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CONTENT_EVENT_DEBOUNCE_MS = 180L
        private const val STALE_EVENT_AGE_MS = 750L
        private const val TEMP_UNLOCK_DURATION_MS = 5 * 60_000L
        private const val MAX_NODE_DEPTH = 18
        private const val MAX_SNAPSHOT_NODES = 300
        private const val MAX_LABEL_LENGTH = 48
    }
}
