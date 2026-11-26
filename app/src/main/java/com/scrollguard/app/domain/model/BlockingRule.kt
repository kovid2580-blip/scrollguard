package com.scrollguard.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DetectionConfig(
    val targetPackage: String = "",
    val category: ContentCategory = ContentCategory.CUSTOM_SHORT_FORM,
    val resourceIdKeywords: List<String> = emptyList(),
    val contentDescriptionKeywords: List<String> = emptyList(),
    val textKeywords: List<String> = emptyList(),
    val exclusionKeywords: List<String> = emptyList(),
    val minConfidence: Float = 0.65f,
    val isBuiltIn: Boolean = false,
    val compatibilityNote: String = ""
)

data class BlockingRule(
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val blockType: BlockType,
    val contentType: ContentCategory,
    val enabled: Boolean,
    val dailyLimitMinutes: Int? = null,
    val detectionConfig: DetectionConfig = DetectionConfig(
        targetPackage = packageName,
        category = contentType
    ),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
