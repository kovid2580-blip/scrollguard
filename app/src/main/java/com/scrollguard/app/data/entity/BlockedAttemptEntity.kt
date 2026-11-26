package com.scrollguard.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_attempts",
    indices = [Index(value = ["timestamp"]), Index(value = ["packageName"])]
)
data class BlockedAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val contentType: String,
    val timestamp: Long,
    val reason: String,
    val confidence: Float
)
