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
        description = "Blocks Reels tab and Reels player while preserving Feed, Stories, DMs, and Profiles."
    ),
    INSTAGRAM_EXPLORE(
        displayName = "Instagram Explore",
        defaultPackage = "com.instagram.android",
        description = "Optional restriction for the Instagram Explore discovery grid."
    ),
    YOUTUBE_SHORTS(
        displayName = "YouTube Shorts",
        defaultPackage = "com.google.android.youtube",
        description = "Blocks YouTube Shorts player and Shorts tab while keeping regular videos, Search, and Subscriptions."
    ),
    FACEBOOK_REELS(
        displayName = "Facebook Reels",
        defaultPackage = "com.facebook.katana",
        description = "Blocks Facebook Reels surface when identifiable via accessibility nodes."
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
