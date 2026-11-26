package com.scrollguard.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["packageName", "date"], unique = true)]
)
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val date: String,
    val foregroundDuration: Long
)
