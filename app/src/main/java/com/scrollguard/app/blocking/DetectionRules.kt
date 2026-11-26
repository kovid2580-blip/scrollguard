package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.ContentCategory

/**
 * Lightweight, privacy-safe node snapshot extracted in-memory from AccessibilityNodeInfo.
 * Only structural identifiers, selection state, bounds, and short UI labels (<= 48 chars)
 * are retained for the duration of a single evaluation pass and never persisted.
 */
data class ScreenNodeSnapshot(
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val isSelected: Boolean = false,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isFocused: Boolean = false,
    val boundsWidth: Int = 0,
    val boundsHeight: Int = 0,
    val boundsLeft: Int = 0,
    val boundsTop: Int = 0,
    val boundsRight: Int = 0,
    val boundsBottom: Int = 0
)

data class ScreenContext(
    val packageName: String,
    val eventTypeName: String,
    val windowClassName: String? = null,
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400,
    val nodes: List<ScreenNodeSnapshot> = emptyList(),
    val isImeVisible: Boolean = false
)

enum class ScreenClassification(val label: String) {
    UNKNOWN("Unknown"),
    HOME_FEED("Home Feed"),
    STORIES("Stories"),
    EXPLORE_SEARCH("Explore / Search"),
    DIRECT_MESSAGES("Direct Messages"),
    PROFILE("Profile"),
    POST_OR_NOTIFICATIONS("Post / Notifications"),
    REELS_PLAYBACK("Reels Playback"),
    SHORTS_PLAYBACK("Shorts Playback"),
    SPOTLIGHT_PLAYBACK("Spotlight Playback"),
    TIKTOK_FEED("TikTok Short-Video Feed"),
    CUSTOM_RESTRICTED("Custom Restricted Surface")
}

data class EntryPointTarget(
    val category: ContentCategory,
    val label: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun formatBounds(): String = "[$left, $top, $right, $bottom]"
}

enum class AdapterUiHealth(val label: String) {
    HEALTHY("Healthy"),
    LIMITED_VISIBILITY("Limited Node Visibility"),
    NEEDS_RULE_REVIEW("UI Changed / Review Needed")
}

data class ContentMatchResult(
    val matched: Boolean,
    val category: ContentCategory,
    val confidence: Float,
    val matchedSignals: List<String>,
    val ruleName: String,
    val entryPoints: List<EntryPointTarget> = emptyList(),
    val uiHealth: AdapterUiHealth = AdapterUiHealth.HEALTHY,
    val screenClassification: ScreenClassification = ScreenClassification.UNKNOWN,
    val failureReason: String? = null
)

object KnownPlatforms {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val FACEBOOK_PACKAGE = "com.facebook.katana"
    const val FACEBOOK_LITE_PACKAGE = "com.facebook.lite"
    const val MESSENGER_PACKAGE = "com.facebook.orca"
    const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    const val TIKTOK_TRILL_PACKAGE = "com.ss.android.ugc.trill"
    const val TIKTOK_LITE_PACKAGE = "com.zhiliaoapp.musically.go"
    const val SNAPCHAT_PACKAGE = "com.snapchat.android"

    val FACEBOOK_PACKAGES = setOf(FACEBOOK_PACKAGE, FACEBOOK_LITE_PACKAGE)
    val TIKTOK_PACKAGES = setOf(TIKTOK_PACKAGE, TIKTOK_TRILL_PACKAGE, TIKTOK_LITE_PACKAGE)

    val SYSTEM_EXEMPT_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
    )
}
