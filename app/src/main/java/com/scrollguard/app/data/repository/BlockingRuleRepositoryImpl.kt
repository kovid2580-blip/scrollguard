package com.scrollguard.app.data.repository

import com.scrollguard.app.data.dao.BlockingRuleDao
import com.scrollguard.app.data.entity.BlockingRuleEntity
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.DetectionConfig
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingRuleRepositoryImpl @Inject constructor(
    private val dao: BlockingRuleDao,
    private val json: Json
) : BlockingRuleRepository {

    override fun observeAllRules(): Flow<List<BlockingRule>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAllRules(): List<BlockingRule> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getEnabledRules(): List<BlockingRule> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun getRulesForPackage(packageName: String): List<BlockingRule> =
        dao.getByPackage(packageName).map { it.toDomain() }

    override suspend fun upsertRule(rule: BlockingRule): Long =
        dao.upsert(rule.toEntity())

    override suspend fun setRuleEnabled(ruleId: Long, enabled: Boolean) {
        dao.setEnabled(ruleId, enabled, System.currentTimeMillis())
    }

    override suspend fun deleteRule(ruleId: Long) {
        dao.deleteById(ruleId)
    }

    override suspend fun replaceAllRules(rules: List<BlockingRule>) {
        dao.clearAll()
        if (rules.isNotEmpty()) {
            dao.insertAllIgnore(rules.map { it.toEntity() })
        }
    }

    override suspend fun seedBuiltInRulesIfMissing() {
        val now = System.currentTimeMillis()
        val defaults = listOf(
            BlockingRule(
                packageName = "com.instagram.android",
                appName = "Instagram",
                blockType = BlockType.SELECTIVE_CONTENT,
                contentType = ContentCategory.INSTAGRAM_REELS,
                enabled = true,
                detectionConfig = DetectionConfig(
                    targetPackage = "com.instagram.android",
                    category = ContentCategory.INSTAGRAM_REELS,
                    resourceIdKeywords = listOf(
                        "clips_viewer",
                        "clips_media",
                        "clips_video_container",
                        "clips_swipe_refresh_container"
                    ),
                    contentDescriptionKeywords = listOf("Reels", "Reel", "Clips"),
                    textKeywords = listOf("Reels", "Audio is unavailable", "Original audio"),
                    exclusionKeywords = listOf(
                        "Direct",
                        "Messages",
                        "Story",
                        "Your story",
                        "Edit profile",
                        "Message..."
                    ),
                    minConfidence = 0.65f,
                    isBuiltIn = true,
                    compatibilityNote = "Supported via multi-signal accessibility hierarchy & Clips resource-ID detection. Feed, Stories, DMs, and Profiles remain accessible."
                ),
                createdAt = now,
                updatedAt = now
            ),
            BlockingRule(
                packageName = "com.instagram.android",
                appName = "Instagram",
                blockType = BlockType.SELECTIVE_CONTENT,
                contentType = ContentCategory.INSTAGRAM_EXPLORE,
                enabled = false,
                detectionConfig = DetectionConfig(
                    targetPackage = "com.instagram.android",
                    category = ContentCategory.INSTAGRAM_EXPLORE,
                    resourceIdKeywords = listOf(
                        "explore_grid",
                        "search_tab",
                        "discovery_item",
                        "explore_action_bar"
                    ),
                    contentDescriptionKeywords = listOf("Search and explore", "Explore"),
                    textKeywords = listOf("Search", "For you"),
                    exclusionKeywords = listOf("Direct", "Messages", "Your story", "Edit profile"),
                    minConfidence = 0.70f,
                    isBuiltIn = true,
                    compatibilityNote = "Optional Explore grid restriction. Note: typing in Search may briefly interact with the Explore tab."
                ),
                createdAt = now,
                updatedAt = now
            ),
            BlockingRule(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                blockType = BlockType.SELECTIVE_CONTENT,
                contentType = ContentCategory.YOUTUBE_SHORTS,
                enabled = true,
                detectionConfig = DetectionConfig(
                    targetPackage = "com.google.android.youtube",
                    category = ContentCategory.YOUTUBE_SHORTS,
                    resourceIdKeywords = listOf(
                        "reel_watch_fragment",
                        "reel_recycler",
                        "reel_player",
                        "shorts_player",
                        "reel_progress_bar"
                    ),
                    contentDescriptionKeywords = listOf("Shorts", "Remix", "Subscribe"),
                    textKeywords = listOf("Shorts"),
                    exclusionKeywords = listOf(
                        "Search YouTube",
                        "Subscriptions",
                        "Watch later",
                        "Playlist",
                        "Up next"
                    ),
                    minConfidence = 0.65f,
                    isBuiltIn = true,
                    compatibilityNote = "Supported via Shorts player fragment IDs and selected Shorts navigation state. Standard videos, Search, and Subscriptions stay open."
                ),
                createdAt = now,
                updatedAt = now
            ),
            BlockingRule(
                packageName = "com.facebook.katana",
                appName = "Facebook",
                blockType = BlockType.SELECTIVE_CONTENT,
                contentType = ContentCategory.FACEBOOK_REELS,
                enabled = true,
                detectionConfig = DetectionConfig(
                    targetPackage = "com.facebook.katana",
                    category = ContentCategory.FACEBOOK_REELS,
                    resourceIdKeywords = listOf(
                        "reels_viewer",
                        "fb_shorts",
                        "short_form_video"
                    ),
                    contentDescriptionKeywords = listOf("Reels", "Facebook Reels", "Add a comment"),
                    textKeywords = listOf("Reels"),
                    exclusionKeywords = listOf("Marketplace", "Groups", "Pages", "Notifications"),
                    minConfidence = 0.65f,
                    isBuiltIn = true,
                    compatibilityNote = "Best-effort detection: Facebook uses obfuscated Litho/React views in some versions. Preserves Feed, Groups, Pages, Profiles, and Messenger."
                ),
                createdAt = now,
                updatedAt = now
            ),
            BlockingRule(
                packageName = "com.zhiliaoapp.musically",
                appName = "TikTok",
                blockType = BlockType.SELECTIVE_CONTENT,
                contentType = ContentCategory.TIKTOK_SHORT_FORM,
                enabled = false,
                detectionConfig = DetectionConfig(
                    targetPackage = "com.zhiliaoapp.musically",
                    category = ContentCategory.TIKTOK_SHORT_FORM,
                    resourceIdKeywords = listOf("feed_pager", "vertical_view_pager"),
                    contentDescriptionKeywords = listOf("For You", "Following"),
                    textKeywords = listOf("For You", "Following"),
                    exclusionKeywords = listOf("Inbox", "Direct messages", "Profile"),
                    minConfidence = 0.70f,
                    isBuiltIn = true,
                    compatibilityNote = "Restricted-mode interruption for TikTok short-video feeds. Because TikTok's primary UI is short-form video, general TikTok browsing cannot be guaranteed; use Full App Blocking for strict restriction."
                ),
                createdAt = now,
                updatedAt = now
            ),
            BlockingRule(
                packageName = "com.snapchat.android",
                appName = "Snapchat",
                blockType = BlockType.SELECTIVE_CONTENT,
                contentType = ContentCategory.SNAPCHAT_SPOTLIGHT,
                enabled = true,
                detectionConfig = DetectionConfig(
                    targetPackage = "com.snapchat.android",
                    category = ContentCategory.SNAPCHAT_SPOTLIGHT,
                    resourceIdKeywords = listOf(
                        "snap_spotlight",
                        "spotlight_feed",
                        "spotlight_player",
                        "spotlight_viewer"
                    ),
                    contentDescriptionKeywords = listOf("Spotlight", "Spotlight tab"),
                    textKeywords = listOf("Spotlight"),
                    exclusionKeywords = listOf("Take a Snap", "Send a chat", "Chat"),
                    minConfidence = 0.65f,
                    isBuiltIn = true,
                    compatibilityNote = "Best-effort Spotlight tab suppression & player interruption while preserving Camera and Messaging. Pending live device verification on latest Snapchat UI."
                ),
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insertAllIgnore(defaults.map { it.toEntity() })
    }

    private fun BlockingRuleEntity.toDomain(): BlockingRule {
        val parsedBlockType = runCatching { BlockType.valueOf(blockType) }.getOrDefault(BlockType.FULL_APP)
        val parsedCategory = runCatching { ContentCategory.valueOf(contentType) }
            .getOrDefault(ContentCategory.CUSTOM_SHORT_FORM)
        val parsedConfig = runCatching {
            json.decodeFromString<DetectionConfig>(detectionConfiguration)
        }.getOrElse {
            DetectionConfig(targetPackage = packageName, category = parsedCategory)
        }
        return BlockingRule(
            id = id,
            packageName = packageName,
            appName = appName,
            blockType = parsedBlockType,
            contentType = parsedCategory,
            enabled = enabled,
            dailyLimitMinutes = dailyLimitMinutes,
            detectionConfig = parsedConfig,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun BlockingRule.toEntity(): BlockingRuleEntity =
        BlockingRuleEntity(
            id = id,
            packageName = packageName,
            appName = appName,
            blockType = blockType.name,
            contentType = contentType.name,
            enabled = enabled,
            dailyLimitMinutes = dailyLimitMinutes,
            detectionConfiguration = json.encodeToString(detectionConfig),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}
