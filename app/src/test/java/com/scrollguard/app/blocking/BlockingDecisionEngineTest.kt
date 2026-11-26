package com.scrollguard.app.blocking

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
            resourceIdKeywords = listOf("clips_viewer", "clips_media", "clips_video_container"),
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
            resourceIdKeywords = listOf("reel_watch_fragment", "reel_recycler", "shorts_player"),
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

    private val tiktokRule = BlockingRule(
        id = 4L,
        packageName = KnownPlatforms.TIKTOK_PACKAGE,
        appName = "TikTok",
        blockType = BlockType.SELECTIVE_CONTENT,
        contentType = ContentCategory.TIKTOK_SHORT_FORM,
        enabled = true,
        detectionConfig = DetectionConfig(
            targetPackage = KnownPlatforms.TIKTOK_PACKAGE,
            category = ContentCategory.TIKTOK_SHORT_FORM,
            resourceIdKeywords = listOf("feed_pager", "vertical_view_pager"),
            contentDescriptionKeywords = listOf("For You", "Following"),
            textKeywords = listOf("For You", "Following"),
            exclusionKeywords = listOf("Inbox", "Direct messages", "Profile"),
            minConfidence = 0.70f,
            isBuiltIn = true
        )
    )

    private val snapchatSpotlightRule = BlockingRule(
        id = 5L,
        packageName = KnownPlatforms.SNAPCHAT_PACKAGE,
        appName = "Snapchat",
        blockType = BlockType.SELECTIVE_CONTENT,
        contentType = ContentCategory.SNAPCHAT_SPOTLIGHT,
        enabled = true,
        detectionConfig = DetectionConfig(
            targetPackage = KnownPlatforms.SNAPCHAT_PACKAGE,
            category = ContentCategory.SNAPCHAT_SPOTLIGHT,
            resourceIdKeywords = listOf("snap_spotlight", "spotlight_feed", "spotlight_player"),
            contentDescriptionKeywords = listOf("Spotlight", "Spotlight tab"),
            textKeywords = listOf("Spotlight"),
            exclusionKeywords = listOf("Take a Snap", "Send a chat", "Chat"),
            minConfidence = 0.65f,
            isBuiltIn = true
        )
    )

    @Before
    fun setup() {
        val scheduleEvaluator = ScheduleEvaluator()
        val evaluator = DetectionConfidenceEvaluator()
        val appEngine = AppDetectionEngine(scheduleEvaluator)
        val contentEngine = ContentDetectionEngine(
            instagramAdapter = InstagramContentAdapter(evaluator),
            youtubeAdapter = YouTubeContentAdapter(evaluator),
            facebookAdapter = FacebookContentAdapter(evaluator),
            tiktokAdapter = TikTokContentAdapter(evaluator),
            snapchatAdapter = SnapchatContentAdapter(evaluator),
            customRuleAdapter = CustomRuleContentAdapter(evaluator),
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

    @Test
    fun `instagram home exposes bounded reels entry point and preserves search`() {
        val homeWithTabBounds = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/feed_tab", isSelected = true),
                ScreenNodeSnapshot(
                    viewIdResourceName = "com.instagram.android:id/clips_tab",
                    contentDescription = "Reels",
                    isSelected = false,
                    isClickable = true,
                    boundsWidth = 200,
                    boundsHeight = 140,
                    boundsLeft = 640,
                    boundsTop = 2160,
                    boundsRight = 840,
                    boundsBottom = 2300
                )
            )
        )
        val homeDecision = decisionEngine.decide(
            screen = homeWithTabBounds,
            rules = listOf(igReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(homeDecision.shouldBlock)
        assertEquals(1, homeDecision.entryPointsToSuppress.size)
        assertEquals(ContentCategory.INSTAGRAM_REELS, homeDecision.entryPointsToSuppress.first().category)

        val searchScreen = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.instagram.android:id/action_bar_search_edit_text", text = "Reels")
            )
        )
        val searchDecision = decisionEngine.decide(
            screen = searchScreen,
            rules = listOf(igReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(searchDecision.shouldBlock)
    }

    @Test
    fun `youtube preserves comments and playlists while detecting shorts tab entry point`() {
        val watchWithComments = ScreenContext(
            packageName = KnownPlatforms.YOUTUBE_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/watch_player"),
                ScreenNodeSnapshot(viewIdResourceName = "com.google.android.youtube:id/comments_entry_point", text = "Comments"),
                ScreenNodeSnapshot(
                    viewIdResourceName = "com.google.android.youtube:id/pivot_shorts",
                    contentDescription = "Shorts",
                    isSelected = false,
                    boundsWidth = 210,
                    boundsHeight = 130,
                    boundsLeft = 220,
                    boundsTop = 2170,
                    boundsRight = 430,
                    boundsBottom = 2300
                )
            )
        )
        val decision = decisionEngine.decide(
            screen = watchWithComments,
            rules = listOf(ytShortsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
        assertEquals(1, decision.entryPointsToSuppress.size)
        assertEquals(ContentCategory.YOUTUBE_SHORTS, decision.entryPointsToSuppress.first().category)
    }

    @Test
    fun `messenger remains unaffected by facebook reels rule`() {
        val messengerScreen = ScreenContext(
            packageName = KnownPlatforms.MESSENGER_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(ScreenNodeSnapshot(text = "Reels"))
        )
        val decision = decisionEngine.decide(
            screen = messengerScreen,
            rules = listOf(fbReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(decision.shouldBlock)
    }

    @Test
    fun `tiktok interrupts short video feed while preserving inbox when identifiable`() {
        val tiktokFeed = ScreenContext(
            packageName = KnownPlatforms.TIKTOK_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.zhiliaoapp.musically:id/feed_pager"),
                ScreenNodeSnapshot(text = "For You", isSelected = true),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2200
                )
            )
        )
        val feedDecision = decisionEngine.decide(
            screen = tiktokFeed,
            rules = listOf(tiktokRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertTrue(feedDecision.shouldBlock)
        assertEquals(ContentCategory.TIKTOK_SHORT_FORM, feedDecision.contentCategory)

        val tiktokInbox = ScreenContext(
            packageName = KnownPlatforms.TIKTOK_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(text = "Inbox", isSelected = true),
                ScreenNodeSnapshot(text = "Direct messages")
            )
        )
        val inboxDecision = decisionEngine.decide(
            screen = tiktokInbox,
            rules = listOf(tiktokRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(inboxDecision.shouldBlock)
    }

    @Test
    fun `snapchat blocks spotlight while preserving camera and chat`() {
        val cameraScreen = ScreenContext(
            packageName = KnownPlatforms.SNAPCHAT_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.snapchat.android:id/camera_capture_button", contentDescription = "Take a Snap"),
                ScreenNodeSnapshot(
                    viewIdResourceName = "com.snapchat.android:id/spotlight_tab",
                    contentDescription = "Spotlight",
                    isSelected = false,
                    boundsWidth = 190,
                    boundsHeight = 130,
                    boundsLeft = 840,
                    boundsTop = 2180,
                    boundsRight = 1030,
                    boundsBottom = 2310
                )
            )
        )
        val cameraDecision = decisionEngine.decide(
            screen = cameraScreen,
            rules = listOf(snapchatSpotlightRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(cameraDecision.shouldBlock)
        assertEquals(1, cameraDecision.entryPointsToSuppress.size)

        val spotlightScreen = ScreenContext(
            packageName = KnownPlatforms.SNAPCHAT_PACKAGE,
            eventTypeName = "WINDOW_STATE_CHANGED",
            nodes = listOf(
                ScreenNodeSnapshot(viewIdResourceName = "com.snapchat.android:id/snap_spotlight_feed"),
                ScreenNodeSnapshot(contentDescription = "Spotlight", isSelected = true),
                ScreenNodeSnapshot(
                    className = "androidx.viewpager2.widget.ViewPager2",
                    isScrollable = true,
                    boundsWidth = 1080,
                    boundsHeight = 2200
                )
            )
        )
        val spotlightDecision = decisionEngine.decide(
            screen = spotlightScreen,
            rules = listOf(snapchatSpotlightRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertTrue(spotlightDecision.shouldBlock)
        assertEquals(ContentCategory.SNAPCHAT_SPOTLIGHT, spotlightDecision.contentCategory)
    }

    @Test
    fun `unselected nav tabs alone never cause false positive blocking`() {
        val igGenericScreenWithTabs = ScreenContext(
            packageName = KnownPlatforms.INSTAGRAM_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(
                    viewIdResourceName = "com.instagram.android:id/clips_tab",
                    contentDescription = "Reels",
                    isSelected = false,
                    boundsWidth = 200,
                    boundsHeight = 140,
                    boundsLeft = 640,
                    boundsTop = 2160,
                    boundsRight = 840,
                    boundsBottom = 2300
                )
            )
        )
        val igDecision = decisionEngine.decide(
            screen = igGenericScreenWithTabs,
            rules = listOf(igReelsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(igDecision.shouldBlock)
        assertEquals(1, igDecision.entryPointsToSuppress.size)

        val ytGenericScreenWithTabs = ScreenContext(
            packageName = KnownPlatforms.YOUTUBE_PACKAGE,
            eventTypeName = "WINDOW_CONTENT_CHANGED",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            nodes = listOf(
                ScreenNodeSnapshot(
                    viewIdResourceName = "com.google.android.youtube:id/pivot_shorts",
                    contentDescription = "Shorts",
                    isSelected = false,
                    boundsWidth = 210,
                    boundsHeight = 130,
                    boundsLeft = 220,
                    boundsTop = 2170,
                    boundsRight = 430,
                    boundsBottom = 2300
                )
            )
        )
        val ytDecision = decisionEngine.decide(
            screen = ytGenericScreenWithTabs,
            rules = listOf(ytShortsRule),
            schedules = emptyList(),
            strictModeState = StrictModeState(),
            todayUsageMsByPackage = emptyMap(),
            selfPackageName = selfPackage
        )
        assertFalse(ytDecision.shouldBlock)
        assertEquals(1, ytDecision.entryPointsToSuppress.size)
    }
}
