package com.scrollguard.app.blocking

import com.scrollguard.app.domain.model.ContentCategory

/**
 * Lightweight, privacy-safe node snapshot extracted in-memory from AccessibilityNodeInfo.
 * Only structural identifiers, selection state, and short UI labels (<= 48 chars) are retained
 * for the duration of a single evaluation pass and never persisted.
 */
data class ScreenNodeSnapshot(
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val isSelected: Boolean = false,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val boundsWidth: Int = 0,
    val boundsHeight: Int = 0
)

data class ScreenContext(
    val packageName: String,
    val eventTypeName: String,
    val windowClassName: String? = null,
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400,
    val nodes: List<ScreenNodeSnapshot> = emptyList()
)

data class ContentMatchResult(
    val matched: Boolean,
    val category: ContentCategory,
    val confidence: Float,
    val matchedSignals: List<String>,
    val ruleName: String
)

object KnownPlatforms {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val FACEBOOK_PACKAGE = "com.facebook.katana"
    const val FACEBOOK_LITE_PACKAGE = "com.facebook.lite"

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
        "com.oneplus.launcher"
    )
}
