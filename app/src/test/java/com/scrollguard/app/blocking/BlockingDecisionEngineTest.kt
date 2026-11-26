package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.DetectionConfig
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.monitoring.DiagnosticsLogBuffer
import com.scrollguard.app.scheduling.ScheduleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZonedDateTime

class BlockingDecisionEngineTest {

    private lateinit var decisionEngine: BlockingDecisionEngine
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
            resourceIdKeywords = listOf("clips_viewer", "clips_tab", "clips_media", "clips_video_container"),
            contentDescriptionKeywords = listOf("Reels", "Reel", "Clips"),
            textKeywords = listOf("Reels", "Original audio"),
            exclusionKeywords = listOf("Direct", "Messages", "Your story", "Edit profile", "Message..."),
            minConfidence = 0.65f,
            isBuiltIn = true
        )
    )

    private val ytShortsRule = BlockingRule(
        id = 2L,
        packageName = KnownPlatforms.YOUTUBE_PACKAGE,
        appName = "YouTube",
        blockType = BlockType.SELECTIVE_CONTENT,
        contentType = ContentCategory.YOUTUBE_SHORTS,
        enabled = true,
        detectionConfig = DetectionConfig(
            targetPackage = KnownPlatforms.YOUTUBE_PACKAGE,
            category = ContentCategory.YOUTUBE_SHORTS,
            resourceIdKeywords = listOf("reel_watch_fragment", "reel_recycler", "shorts_player", "pivot_shorts"),
            contentDescriptionKeywords = listOf("Shorts", "Remix"),
            textKeywords = listOf("Shorts"),
            exclusionKeywords = listOf("Search YouTube", "Subscriptions", "Up next"),
            minConfidence = 0.65f,
            isBuiltIn = true
        )
    )

    private val fbReelsRule = BlockingRule(
        id = 3L,
        packageName = KnownPlatforms.FACEBOOK_PACKAGE,
        appName = "Facebook",
        blockType = BlockType.SELECTIVE_CONTENT,
        contentType = ContentCategory.FACEBOOK_REELS,
        enabled = true,
        detectionConfig = DetectionConfig(
            targetPackage = KnownPlatforms.FACEBOOK_PACKAGE,
            category = ContentCategory.FACEBOOK_REELS,
            resourceIdKeywords = listOf("reels_viewer", "fb_shorts", "reels_tab"),
            contentDescriptionKeywords = listOf("Reels", "Facebook Reels"),
            textKeywords = listOf("Reels"),
            exclusionKeywords = listOf("Marketplace", "Groups", "Notifications"),
            minConfidence = 0.65f,
            isBuiltIn = true
        )
    )

    @Before
    fun setup() {
        val scheduleEvaluator = ScheduleEvaluator()
        val appEngine = AppDetectionEngine(scheduleEvaluator)
        val contentEngine = ContentDetectionEngine(
            confidenceEvaluator = DetectionConfidenceEvaluator(),
            diagnosticsLogBuffer = DiagnosticsLogBuffer()
        )
        decisionEngine = BlockingDecisionEngine(appEngine, contentEngine, scheduleEvaluator)
    }

    @Test
    fun `scenario 1 - instagram home feed remains accessible`() {
        val screen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/feed_tab", isSelected = true),
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/row_feed_photo"),
                ScreenNodeSnapshot(contentDescription = "Your story", text = "Your story"),
                ScreenNodeSnapshot(contentDescription = "Reels", isSelected = false)
            )
        )
        val decision = decisionEngine.decide(
            screen = screen,
            rules = listOf(igReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `scenario 2 - instagram reels is blocked when enabled`() {
        val screen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_viewer_root"),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2100
                ),
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_tab", contentDescription = "Reels", isSelected = true)
            )
        )
        val decision = decisionEngine.decide(
            screen = screen,
            rules = listOf(igReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertTrue(decision.shouldBlock)
        assertEquals(ContentCategory.INSTAGRAM_REELS, decision.contentCategory)
        assertTrue(decision.confidence >= 0.65f)
    }

    @Test
    fun `scenario 3 - instagram direct messages remain accessible even if message text mentions reels`() {
        val screen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/direct_inbox_container"),
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/message_list"),
                ScreenNodeSnapshot(text = "Reels"),
                ScreenNodeSnapshot(text = "Message...")
            )
        )
        val decision = decisionEngine.decide(
            screen = screen,
            rules = listOf(igReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `scenario 4 - youtube normal videos and search remain accessible`() {
        val screen = ScreenContext(
            packageName = KnownPlatforms.YOUTUBE_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/watch_player"),
                ScreenNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/watch_while_time_bar"),
                ScreenNodeSnapshot(text = "Up next"),
                ScreenNodeSnapshot(contentDescription = "Shorts", isSelected = false)
            )
        )
        val decision = decisionEngine.decide(
            screen = screen,
            rules = listOf(ytShortsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `scenario 5 - youtube shorts is blocked when enabled`() {
        val screen = ScreenContext(
            packageName = KnownPlatforms.YOUTUBE_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/reel_watch_fragment"),
                ScreenNodeSnapshot(
                    className = "androidx.recyclerview.widget.RecyclerView",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2000
                ),
                ScreenNodeSnapshot(contentDescription = "Shorts", isSelected = true)
            )
        )
        val decision = decisionEngine.decide(
            screen = screen,
            rules = listOf(ytShortsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertTrue(decision.shouldBlock)
        assertEquals(ContentCategory.YOUTUBE_SHORTS, decision.contentCategory)
    }

    @Test
    fun `scenario 6 - facebook reels blocked when detectable while normal feed allowed`() {
        val fbReelsScreen = ScreenContext(
            packageName = KnownPlatforms.FACEBOOK_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.facebook.katana:id/reels_viewer_container"),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2100
                ),
                ScreenNodeSnapshot(contentDescription = "Reels", isSelected = true)
            )
        )
        val blockDecision = decisionEngine.decide(
            screen = fbReelsScreen,
            rules = listOf(fbReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertTrue(blockDecision.shouldBlock)
        assertEquals(ContentCategory.FACEBOOK_REELS, blockDecision.contentCategory)

        val fbFeedScreen = ScreenContext(
            packageName = KnownPlatforms.FACEBOOK_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(contentDescription = "Home", isSelected = true),
                ScreenNodeSnapshot(text = "What's on your mind?")
            )
        )
        val allowDecision = decisionEngine.decide(
            screen = fbFeedScreen,
            rules = listOf(fbReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(allowDecision.shouldBlock)
    }

    @Test
    fun `scenario 7 - full app block takes priority over selective rules`() {
        val fullRule = BlockingRule(
            id = 10L,
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            appName = "Instagram",
            blockType = BlockType.FULL_APP,
            contentType = ContentCategory.FULL_APP,
            enabled = true
        )
        val homeScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/feed_tab", isSelected = true))
        )
        val decision = decisionEngine.decide(
            screen = homeScreen,
            rules = listOf(igReelsRule, fullRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertTrue(decision.shouldBlock)
        assertEquals(BlockingReason.FULL_APP_RULE, decision.reason)
        assertEquals(ContentCategory.FULL_APP, decision.contentCategory)
    }

    @Test
    fun `scenario 8 - unselected app and scrollguard itself remain unaffected`() {
        val slackScreen = ScreenContext(
            packageName = "com.Slack",
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(ScreenNodeSnapshot(text = "Reels"))
        )
        val slackDecision = decisionEngine.decide(
            screen = slackScreen,
            rules = listOf(igReelsRule, ytShortsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(slackDecision.shouldBlock)

        val selfFullRule = BlockingRule(
            id = 99L,
            packageName = selfPackage,
            appName = "ScrollGuard",
            blockType = BlockType.FULL_APP,
            contentType = ContentCategory.FULL_APP,
            enabled = true
        )
        val selfDecision = decisionEngine.decide(
            screen = ScreenContext(packageName = selfPackage, eventTypeName = "WINDOW_STATE_CHANGED"),
            rules = listOf(selfFullRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(selfDecision.shouldBlock)
        assertEquals(BlockingReason.EXEMPT_SELF_OR_SYSTEM, selfDecision.reason)
    }

    @Test
    fun `scenario 17 - text-only mention or unsupported layout does not cause indiscriminate blocking`() {
        val ambiguousScreen = ScreenContext(
            packageName = KnownPlatforms.YOUTUBE_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(text = "Shorts")
            )
        )
        val decision = decisionEngine.decide(
            screen = ambiguousScreen,
            rules = listOf(ytShortsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `daily usage limit triggers block once threshold exceeded`() {
        val limitRule = BlockingRule(
            id = 20L,
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            blockType = BlockType.FULL_APP,
            contentType = ContentCategory.FULL_APP,
            enabled = true,
            dailyLimitMinutes = 30
        )
        val screen = ScreenContext(packageName = "com.reddit.frontpage", eventTypeName = "WINDOW_STATE_CHANGED")

        val underLimit = decisionEngine.decide(
            screen = screen,
            rules = listOf(limitRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = mapOf("com.reddit.frontpage" to 15 * 60_000L),
            selfPackageName = selfPackage
        )
        assertFalse(underLimit.shouldBlock)

        val overLimit = decisionEngine.decide(
            screen = screen,
            rules = listOf(limitRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = mapOf("com.reddit.frontpage" to 31 * 60_000L),
            selfPackageName = selfPackage
        )
        assertTrue(overLimit.shouldBlock)
        assertEquals(BlockingReason.DAILY_LIMIT_EXCEEDED, overLimit.reason)
    }

    @Test
    fun `scheduled full app rule activates during schedule window`() {
        val schedule = FocusSchedule(
            id = 1L,
            name = "Work Hours",
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            weekdays = setOf(DayOfWeek.MONDAY),
            enabled = true,
            affectedPackages = setOf(KnownPlatforms.INSTAGRAM_PACKAGE),
            appliesToFullApp = true,
            appliesToSelectiveContent = true
        )
        val mondayNoon = ZonedDateTime.parse("2026-11-23T12:00:00Z")
        val decision = decisionEngine.decide(
            screen = ScreenContext(KnownPlatforms.INSTAGRAM_PACKAGE, "WINDOW_STATE_CHANGED"),
            rules = emptyList(),
            schedules = listOf(schedule),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage,
            now = mondayNoon
        )
        assertTrue(decision.shouldBlock)
        assertEquals(BlockingReason.SCHEDULED_FULL_APP, decision.reason)
    }

    @Test
    fun `regression - instagram stories viewer remains accessible without false positive`() {
        val storiesScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/story_viewer_root"),
                ScreenNodeSnapshot(contentDescription = "Your story", text = "Your story"),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2200
                )
            )
        )
        val decision = decisionEngine.decide(
            screen = storiesScreen,
            rules = listOf(igReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `regression - youtube home feed with shorts shelf card remains accessible`() {
        val ytHomeWithShelf = ScreenContext(
            packageName = KnownPlatforms.YOUTUBE_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(contentDescription = "Home", isSelected = true),
                ScreenNodeSnapshot(text = "Shorts"),
                ScreenNodeSnapshot(
                    className = "androidx.recyclerview.widget.RecyclerView",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2000
                )
            )
        )
        val decision = decisionEngine.decide(
            screen = ytHomeWithShelf,
            rules = listOf(ytShortsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `regression - facebook feed button mentioning watch reels does not false-positive`() {
        val fbFeed = ScreenContext(
            packageName = KnownPlatforms.FACEBOOK_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(contentDescription = "Watch Reels", isSelected = true),
                ScreenNodeSnapshot(text = "What's on your mind?")
            )
        )
        val decision = decisionEngine.decide(
            screen = fbFeed,
            rules = listOf(fbReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }
}
