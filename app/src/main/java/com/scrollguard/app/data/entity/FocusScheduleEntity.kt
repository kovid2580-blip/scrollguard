package com.scrollguard.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_schedules")
data class FocusScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val weekdays: String,
    val enabled: Boolean,
    val affectedPackages: String,
    val appliesToFullApp: Boolean = true,
    val appliesToSelectiveContent: Boolean = true
)
