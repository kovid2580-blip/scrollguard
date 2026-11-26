package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.ContentCategory
import javax.inject.Inject
import javax.inject.Singleton

enum class DetectionSessionState {
    NORMAL,
    POSSIBLE_RESTRICTED_CONTENT,
    CONFIRMED_RESTRICTED_CONTENT,
    INTERRUPTING,
    COOLDOWN,
    RECOVERING
}

enum class NavigationAction {
    NONE,
    GLOBAL_BACK,
    GLOBAL_HOME
}

data class NavigationActionPlan(
    val sessionState: DetectionSessionState,
    val navigationAction: NavigationAction,
    val showOverlay: Boolean,
    val cooldownActive: Boolean,
    val shouldRecordAttempt: Boolean,
    val backCountInSession: Int,
    val totalNavigationActions: Int,
    val failureReason: String? = null
)

/**
 * Explicit state machine governing navigation interruptions for ScrollGuard.
 * Ensures at most ONE controlled Back action per confirmed selective Reels/Shorts session,
 * never calls GLOBAL_ACTION_HOME for ordinary Instagram selective blocking, and cleanly
 * resets when returning to normal app screens.
 */
@Singleton
class BlockingNavigationStateMachine @Inject constructor() {

    var currentState: DetectionSessionState = DetectionSessionState.NORMAL
        private set

    private var sessionPackage: String? = null
    private var sessionCategory: ContentCategory? = null
    private var possibleConfirmations: Int = 0
    private var backActionsInSession: Int = 0
    private var cooldownUntilMs: Long = 0L
    private var totalNavigationActions: Int = 0
    private var attemptRecordedForSession: Boolean = false

    @Synchronized
    fun reset() {
        currentState = DetectionSessionState.NORMAL
        sessionPackage = null
        sessionCategory = null
        possibleConfirmations = 0
        backActionsInSession = 0
        cooldownUntilMs = 0L
        attemptRecordedForSession = false
    }

    @Synchronized
    fun onAppSwitched(newPackage: String) {
        if (sessionPackage != null && sessionPackage != newPackage) {
            reset()
        }
    }

    @Synchronized
    fun evaluateTransition(
        decision: BlockDecision,
        screenClassification: ScreenClassification,
        rawConfidence: Float,
        minConfidence: Float,
        nowMs: Long,
        audioActive: Boolean = false
    ): NavigationActionPlan {
        // Different package starts fresh
        if (sessionPackage != null && sessionPackage != decision.packageName) {
            reset()
        }

        val inCooldown = nowMs < cooldownUntilMs

        // Normal or non-blocking screen
        if (!decision.shouldBlock) {
            val isAmbiguous = rawConfidence >= AMBIGUOUS_FLOOR_CONFIDENCE && rawConfidence < minConfidence
            val isExplicitNormalScreen = screenClassification in NORMAL_SCREEN_CLASSES

            if (inCooldown && !isExplicitNormalScreen) {
                currentState = DetectionSessionState.COOLDOWN
                return NavigationActionPlan(
                    sessionState = currentState,
                    navigationAction = NavigationAction.NONE,
                    showOverlay = false,
                    cooldownActive = true,
                    shouldRecordAttempt = false,
                    backCountInSession = backActionsInSession,
                    totalNavigationActions = totalNavigationActions
                )
            }

            if (isAmbiguous && !isExplicitNormalScreen) {
                currentState = DetectionSessionState.POSSIBLE_RESTRICTED_CONTENT
                return NavigationActionPlan(
                    sessionState = currentState,
                    navigationAction = NavigationAction.NONE,
                    showOverlay = false,
                    cooldownActive = false,
                    shouldRecordAttempt = false,
                    backCountInSession = backActionsInSession,
                    totalNavigationActions = totalNavigationActions
                )
            }

            // Confirmed return to normal content -> reset session completely
            reset()
            return NavigationActionPlan(
                sessionState = DetectionSessionState.NORMAL,
                navigationAction = NavigationAction.NONE,
                showOverlay = false,
                cooldownActive = false,
                shouldRecordAttempt = false,
                backCountInSession = 0,
                totalNavigationActions = totalNavigationActions
            )
        }

        // Restricted screen detected
        val isNewSession = sessionPackage != decision.packageName || sessionCategory != decision.contentCategory
        if (isNewSession) {
            sessionPackage = decision.packageName
            sessionCategory = decision.contentCategory
            possibleConfirmations = 0
            backActionsInSession = 0
            attemptRecordedForSession = false
        }

        val isSelectiveMode = decision.contentCategory != ContentCategory.FULL_APP &&
            decision.contentCategory != ContentCategory.TIKTOK_SHORT_FORM

        // Full-app / TikTok full-feed mode
        if (!isSelectiveMode) {
            if (inCooldown) {
                currentState = DetectionSessionState.COOLDOWN
                return NavigationActionPlan(
                    sessionState = currentState,
                    navigationAction = NavigationAction.NONE,
                    showOverlay = true,
                    cooldownActive = true,
                    shouldRecordAttempt = false,
                    backCountInSession = backActionsInSession,
                    totalNavigationActions = totalNavigationActions
                )
            }
            cooldownUntilMs = nowMs + FULL_APP_COOLDOWN_MS
            totalNavigationActions += 1
            currentState = DetectionSessionState.INTERRUPTING
            val recordAttempt = !attemptRecordedForSession
            attemptRecordedForSession = true
            return NavigationActionPlan(
                sessionState = DetectionSessionState.COOLDOWN.also { currentState = it },
                navigationAction = NavigationAction.GLOBAL_HOME,
                showOverlay = true,
                cooldownActive = true,
                shouldRecordAttempt = recordAttempt,
                backCountInSession = backActionsInSession,
                totalNavigationActions = totalNavigationActions
            )
        }

        // Selective mode: NEVER call GLOBAL_ACTION_HOME
        // Still inside cooldown from previous Back action: ignore repeated events cleanly
        if (inCooldown) {
            currentState = DetectionSessionState.COOLDOWN
            return NavigationActionPlan(
                sessionState = currentState,
                navigationAction = NavigationAction.NONE,
                showOverlay = false,
                cooldownActive = true,
                shouldRecordAttempt = false,
                backCountInSession = backActionsInSession,
                totalNavigationActions = totalNavigationActions
            )
        }

        // Require strong immediate confidence (>= 0.78) OR 2 confirming evaluations
        possibleConfirmations += 1
        val isHighConfidence = decision.confidence >= HIGH_CONFIDENCE_THRESHOLD || possibleConfirmations >= 2
        if (!isHighConfidence) {
            currentState = DetectionSessionState.POSSIBLE_RESTRICTED_CONTENT
            return NavigationActionPlan(
                sessionState = currentState,
                navigationAction = NavigationAction.NONE,
                showOverlay = false,
                cooldownActive = false,
                shouldRecordAttempt = false,
                backCountInSession = backActionsInSession,
                totalNavigationActions = totalNavigationActions
            )
        }

        // Already used our single Back action for this restricted session and user is still in Reels
        if (backActionsInSession >= MAX_BACK_ACTIONS_PER_SESSION) {
            currentState = DetectionSessionState.RECOVERING
            val note = if (audioActive) {
                "REELS_BACK_EXIT_INCOMPLETE (audio still active; showing fallback overlay)"
            } else {
                "REELS_BACK_EXIT_INCOMPLETE (showing fallback overlay instead of repeating Back)"
            }
            return NavigationActionPlan(
                sessionState = currentState,
                navigationAction = NavigationAction.NONE,
                showOverlay = true,
                cooldownActive = false,
                shouldRecordAttempt = false,
                backCountInSession = backActionsInSession,
                totalNavigationActions = totalNavigationActions,
                failureReason = note
            )
        }

        // First confirmed interruption -> execute exactly ONE controlled GLOBAL_ACTION_BACK
        currentState = DetectionSessionState.INTERRUPTING
        backActionsInSession = 1
        totalNavigationActions += 1
        cooldownUntilMs = nowMs + SELECTIVE_BACK_COOLDOWN_MS
        val recordAttempt = !attemptRecordedForSession
        attemptRecordedForSession = true
        currentState = DetectionSessionState.COOLDOWN

        return NavigationActionPlan(
            sessionState = currentState,
            navigationAction = NavigationAction.GLOBAL_BACK,
            showOverlay = false,
            cooldownActive = true,
            shouldRecordAttempt = recordAttempt,
            backCountInSession = backActionsInSession,
            totalNavigationActions = totalNavigationActions
        )
    }

    companion object {
        const val SELECTIVE_BACK_COOLDOWN_MS = 2_000L
        const val FULL_APP_COOLDOWN_MS = 2_000L
        const val MAX_BACK_ACTIONS_PER_SESSION = 1
        const val HIGH_CONFIDENCE_THRESHOLD = 0.78f
        const val AMBIGUOUS_FLOOR_CONFIDENCE = 0.40f

        private val NORMAL_SCREEN_CLASSES = setOf(
            ScreenClassification.HOME_FEED,
            ScreenClassification.STORIES,
            ScreenClassification.EXPLORE_SEARCH,
            ScreenClassification.DIRECT_MESSAGES,
            ScreenClassification.PROFILE,
            ScreenClassification.POST_OR_NOTIFICATIONS
        )
    }
}
