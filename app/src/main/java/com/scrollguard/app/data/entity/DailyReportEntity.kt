package com.scrollguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reports")
data class DailyReportEntity(
    @PrimaryKey
    val date: String,
    val totalScreenTime: Long,
    val blockedAttempts: Int,
    val focusDuration: Long,
    val applicationUsageSummary: String
)
