package com.scrollguard.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocking_rules",
    indices = [Index(value = ["packageName", "blockType", "contentType"], unique = true)]
)
data class BlockingRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val blockType: String,
    val contentType: String,
    val enabled: Boolean,
    val dailyLimitMinutes: Int? = null,
    val detectionConfiguration: String,
    val createdAt: Long,
    val updatedAt: Long
)
