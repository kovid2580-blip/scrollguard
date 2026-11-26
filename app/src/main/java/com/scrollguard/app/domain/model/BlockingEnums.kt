package com.scrollguard.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockType {
    FULL_APP,
    SELECTIVE_CONTENT
}

@Serializable
enum class ContentCategory(
    val displayName: String,
    val defaultPackage: String?,
    val description: String
) {
    INSTAGRAM_REELS(
        displayName = "Instagram Reels",
        defaultPackage = "com.instagram.android",
        description = "Suppresses the Reels tab entry point where detectable and interrupts Reels playback while preserving Home, Stories, Posts, Profiles, Search, and DMs."
    ),
    INSTAGRAM_EXPLORE(
        displayName = "Instagram Explore",
        defaultPackage = "com.instagram.android",
        description = "Optional restriction for the Instagram Explore discovery grid."
    ),
    YOUTUBE_SHORTS(
        displayName = "YouTube Shorts",
        defaultPackage = "com.google.android.youtube",
        description = "Suppresses the Shorts tab entry point where detectable and interrupts Shorts playback while preserving normal videos, Search, Subscriptions, Playlists, and Comments."
    ),
    FACEBOOK_REELS(
        displayName = "Facebook Reels",
        defaultPackage = "com.facebook.katana",
        description = "Suppresses detectable Reels navigation and interrupts Facebook Reels while preserving Feed, Groups, Pages, Profiles, and Messenger."
    ),
    TIKTOK_SHORT_FORM(
        displayName = "TikTok Short-Form Feed",
        defaultPackage = "com.zhiliaoapp.musically",
        description = "Restricted-mode / full-feed interruption for TikTok short-video browsing. Normal TikTok browsing cannot be reliably separated from short-form feeds."
    ),
    SNAPCHAT_SPOTLIGHT(
        displayName = "Snapchat Spotlight",
        defaultPackage = "com.snapchat.android",
        description = "Suppresses detectable Spotlight tab entry points and interrupts Spotlight feeds while preserving Messaging and ordinary Camera functionality."
    ),
    CUSTOM_SHORT_FORM(
        displayName = "Custom Short-Form Rule",
        defaultPackage = null,
        description = "User-configured selective content rule using node IDs, descriptions, and keywords."
    ),
    FULL_APP(
        displayName = "Full Application",
        defaultPackage = null,
        description = "Blocks the entire selected application."
    )
}
