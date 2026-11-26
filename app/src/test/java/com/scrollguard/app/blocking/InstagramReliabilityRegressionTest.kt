package com.scrollguard.app.blocking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollguard.app.blocking.adapter.CustomRuleContentAdapter
import com.scrollguard.app.blocking.adapter.FacebookContentAdapter
import com.scrollguard.app.blocking.adapter.InstagramContentAdapter
import com.scrollguard.app.blocking.adapter.SnapchatContentAdapter
import com.scrollguard.app.blocking.adapter.TikTokContentAdapter
import com.scrollguard.app.blocking.adapter.YouTubeContentAdapter
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.DetectionConfig
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.monitoring.DiagnosticsLogBuffer
import com.scrollguard.app.scheduling.ScheduleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstagramReliabilityRegressionTest {

    private lateinit var decisionEngine: BlockingDecisionEngine
    private lateinit var stateMachine: BlockingNavigationStateMachine
    private lateinit var entryPointOverlayManager: EntryPointOverlayManager

    private val selfPackage = "com.scrollguard.app"

    private val igReelsRule = BlockingRule(
        id = 1L,
        packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
        appName = "Instagram",
        blockType = BlockType.SELECTIVE_CONTENT,
        contentType = ContentCategory.INSTAGRAM_REELS,
        enabled = true,
        detectionConfig = DetectionConfig(
            targetPackage = KnownPlatforms.INSTAGRAM_PACKAGE,
            category = ContentCategory.INSTAGRAM_REELS,
            resourceIdKeywords = listOf("clips_viewer", "clips_media", "clips_video_container"),
            contentDescriptionKeywords = listOf("Reels", "Reel", "Clips"),
            textKeywords = listOf("Reels", "Original audio"),
            exclusionKeywords = listOf("Direct", "Messages", "Your story", "Edit profile", "Message..."),
            minConfidence = 0.65f,
            isBuiltIn = true
        )
    )

    private val igExploreRuleDisabled = BlockingRule(
        id = 2L,
        packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
        appName = "Instagram",
        blockType = BlockType.SELECTIVE_CONTENT,
        contentType = ContentCategory.INSTAGRAM_EXPLORE,
        enabled = false,
        detectionConfig = DetectionConfig(
            targetPackage = KnownPlatforms.INSTAGRAM_PACKAGE,
            category = ContentCategory.INSTAGRAM_EXPLORE,
            resourceIdKeywords = listOf("explore_grid", "discovery_item"),
            contentDescriptionKeywords = listOf("Search and explore", "Explore"),
            textKeywords = listOf("Search", "For you"),
            exclusionKeywords = listOf("Direct", "Messages", "Your story", "Edit profile"),
            minConfidence = 0.70f,
            isBuiltIn = true
        )
    )

    @Before
    fun setup() {
        val scheduleEvaluator = ScheduleEvaluator()
        val evaluator = DetectionConfidenceEvaluator()
        val contentEngine = ContentDetectionEngine(
            instagramAdapter = InstagramContentAdapter(evaluator),
            youtubeAdapter = YouTubeContentAdapter(evaluator),
            facebookAdapter = FacebookContentAdapter(evaluator),
            tiktokAdapter = TikTokContentAdapter(evaluator),
            snapchatAdapter = SnapchatContentAdapter(evaluator),
            customRuleAdapter = CustomRuleContentAdapter(evaluator),
            diagnosticsLogBuffer = DiagnosticsLogBuffer()
        )
        decisionEngine = BlockingDecisionEngine(
            AppDetectionEngine(scheduleEvaluator),
            contentEngine,
            scheduleEvaluator
        )
        stateMachine = BlockingNavigationStateMachine()
        val context = ApplicationProvider.getApplicationContext<Context>()
        entryPointOverlayManager = EntryPointOverlayManager(context)
    }

    private fun evaluate(screen: ScreenContext, nowMs: Long = 10_000L, audioActive: Boolean = false): Pair<BlockDecision, NavigationActionPlan> {
        val decision = decisionEngine.decide(
            screen = screen,
            rules = listOf(igReelsRule, igExploreRuleDisabled),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        val plan = stateMachine.evaluateTransition(
            decision = decision,
            screenClassification = decision.screenClassification,
            rawConfidence = decision.confidence,
            minConfidence = decision.minConfidenceRequired,
            nowMs = nowMs,
            audioActive = audioActive
        )
        return decision to plan
    }

    @Test
    fun `1 - instagram home feed including inline reel preview does not trigger back`() {
        val homeScreenWithInlineReel = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/feed_tab", isSelected = true),
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/row_feed_button_like"),
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_media"),
                ScreenNodeSnapshot(contentDescription = "Reel", text = "Original audio"),
                ScreenNodeSnapshot(
                    className = "androidx.recyclerview.widget.RecyclerView",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2000
                )
            )
        )
        val (decision, plan) = evaluate(homeScreenWithInlineReel)
        assertFalse(decision.shouldBlock)
        assertEquals(ScreenClassification.HOME_FEED, decision.screenClassification)
        assertEquals(NavigationAction.NONE, plan.navigationAction)
        assertEquals(DetectionSessionState.NORMAL, plan.sessionState)
    }

    @Test
    fun `2 - instagram stories do not trigger back`() {
        val storiesScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/story_viewer_root"),
                ScreenNodeSnapshot(contentDescription = "Your story", text = "Reply to user"),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2200
                )
            )
        )
        val (decision, plan) = evaluate(storiesScreen)
        assertFalse(decision.shouldBlock)
        assertEquals(ScreenClassification.STORIES, decision.screenClassification)
        assertEquals(NavigationAction.NONE, plan.navigationAction)
    }

    @Test
    fun `3 - instagram search does not trigger back`() {
        val searchScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/action_bar_search_edit_text", text = "reels", isFocused = true)
            )
        )
        val (decision, plan) = evaluate(searchScreen)
        assertFalse(decision.shouldBlock)
        assertEquals(ScreenClassification.EXPLORE_SEARCH, decision.screenClassification)
        assertEquals(NavigationAction.NONE, plan.navigationAction)
    }

    @Test
    fun `4 - instagram profile does not trigger back`() {
        val profileScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/profile_tab", isSelected = true),
                ScreenNodeSnapshot(text = "Edit profile"),
                ScreenNodeSnapshot(contentDescription = "Reels")
            )
        )
        val (decision, plan) = evaluate(profileScreen)
        assertFalse(decision.shouldBlock)
        assertEquals(ScreenClassification.PROFILE, decision.screenClassification)
        assertEquals(NavigationAction.NONE, plan.navigationAction)
    }

    @Test
    fun `5 - instagram direct messages do not trigger back`() {
        val dmScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/direct_thread"),
                ScreenNodeSnapshot(text = "Message..."),
                ScreenNodeSnapshot(contentDescription = "Reels", text = "Reels")
            )
        )
        val (decision, plan) = evaluate(dmScreen)
        assertFalse(decision.shouldBlock)
        assertEquals(ScreenClassification.DIRECT_MESSAGES, decision.screenClassification)
        assertEquals(NavigationAction.NONE, plan.navigationAction)
    }

    @Test
    fun `6 - explore does not automatically trigger reels blocking`() {
        val exploreScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/search_tab", isSelected = true),
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/explore_grid"),
                ScreenNodeSnapshot(
                    className = "androidx.recyclerview.widget.RecyclerView",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2000
                )
            )
        )
        val (decision, plan) = evaluate(exploreScreen)
        assertFalse(decision.shouldBlock)
        assertEquals(ScreenClassification.EXPLORE_SEARCH, decision.screenClassification)
        assertEquals(NavigationAction.NONE, plan.navigationAction)
    }

    @Test
    fun `7 and 8 - confirmed reels triggers only ONE back action and never repeats on subsequent events`() {
        val reelsScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_viewer_root"),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2200
                ),
                ScreenNodeSnapshot(
                    viewIdResourceName = "com.instagram.android:id/clips_tab",
                    contentDescription = "Reels",
                    isSelected = true,
                    boundsTop = 2160
                )
            )
        )

        // First confirmed entry -> 1 Back, never Home
        val (d1, p1) = evaluate(reelsScreen, nowMs = 1_000L)
        assertTrue(d1.shouldBlock)
        assertEquals(NavigationAction.GLOBAL_BACK, p1.navigationAction)
        assertEquals(1, p1.backCountInSession)
        assertEquals(1, p1.totalNavigationActions)
        assertEquals(DetectionSessionState.COOLDOWN, p1.sessionState)

        // Repeated event during cooldown -> NONE
        val (_, p2) = evaluate(reelsScreen, nowMs = 1_500L)
        assertEquals(NavigationAction.NONE, p2.navigationAction)
        assertEquals(1, p2.totalNavigationActions)
        assertEquals(DetectionSessionState.COOLDOWN, p2.sessionState)

        // Still on Reels after cooldown expires -> RECOVERING + overlay, NEVER a 2nd Back or Home
        val (_, p3) = evaluate(reelsScreen, nowMs = 4_000L, audioActive = true)
        assertEquals(NavigationAction.NONE, p3.navigationAction)
        assertTrue(p3.showOverlay)
        assertEquals(1, p3.totalNavigationActions)
        assertEquals(DetectionSessionState.RECOVERING, p3.sessionState)
    }

    @Test
    fun `9 - reels session resets after returning to home`() {
        val reelsScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_viewer_root"),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2200
                )
            )
        )
        val homeScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/feed_tab", isSelected = true)
            )
        )

        val (_, p1) = evaluate(reelsScreen, nowMs = 1_000L)
        assertEquals(NavigationAction.GLOBAL_BACK, p1.navigationAction)

        // Return to Home immediately resets session to NORMAL
        val (_, pHome) = evaluate(homeScreen, nowMs = 1_300L)
        assertEquals(DetectionSessionState.NORMAL, pHome.sessionState)
        assertEquals(NavigationAction.NONE, pHome.navigationAction)

        // Re-entering Reels later starts a fresh session cleanly
        val (_, pReenter) = evaluate(reelsScreen, nowMs = 5_000L)
        assertEquals(NavigationAction.GLOBAL_BACK, pReenter.navigationAction)
        assertEquals(2, pReenter.totalNavigationActions)
    }

    @Test
    fun `10 - stale or ambiguous reels event does not affect next normal screen`() {
        val ambiguousScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(contentDescription = "Reels"),
                ScreenNodeSnapshot(text = "Original audio"),
                ScreenNodeSnapshot(
                    className = "androidx.recyclerview.widget.RecyclerView",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2000
                )
            )
        )
        val normalProfile = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/profile_tab", isSelected = true),
                ScreenNodeSnapshot(text = "Edit profile")
            )
        )

        val (_, pAmbiguous) = evaluate(ambiguousScreen, nowMs = 1_000L)
        assertEquals(NavigationAction.NONE, pAmbiguous.navigationAction)

        val (dProfile, pProfile) = evaluate(normalProfile, nowMs = 1_200L)
        assertFalse(dProfile.shouldBlock)
        assertEquals(NavigationAction.NONE, pProfile.navigationAction)
        assertEquals(DetectionSessionState.NORMAL, pProfile.sessionState)
    }

    @Test
    fun `11 - missing or oversized tab bounds do not create an incorrectly positioned overlay`() {
        val homeWithBadBounds = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/feed_tab", isSelected = true),
                // Missing bounds (0x0)
                ScreenNodeSnapshot(
                    viewIdResourceName = "com.instagram.android:id/clips_tab",
                    contentDescription = "Reels",
                    boundsWidth = 0,
                    boundsHeight = 0
                ),
                // Oversized bounds covering feed
                ScreenNodeSnapshot(
                    contentDescription = "Reels",
                    boundsWidth = 1000,
                    boundsHeight = 800,
                    boundsLeft = 40,
                    boundsTop = 1500,
                    boundsRight = 1040,
                    boundsBottom = 2300
                )
            )
        )
        val (decision, _) = evaluate(homeWithBadBounds)
        assertFalse(decision.shouldBlock)
        assertTrue(decision.entryPointsToSuppress.isEmpty())
        assertEquals(AdapterUiHealth.LIMITED_VISIBILITY, decision.uiHealth)
    }

    @Test
    fun `12 - overlay teardown and state reset work during app switching`() {
        val reelsScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_viewer_root"),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2200
                )
            )
        )
        evaluate(reelsScreen, nowMs = 1_000L)
        assertEquals(DetectionSessionState.COOLDOWN, stateMachine.currentState)

        stateMachine.onAppSwitched("com.google.android.apps.nexuslauncher")
        entryPointOverlayManager.dismissAll()

        assertEquals(DetectionSessionState.NORMAL, stateMachine.currentState)
        assertEquals(0, entryPointOverlayManager.activeCount)
    }
}
