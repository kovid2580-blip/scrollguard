package com.scrollguard.app.domain.model

import java.time.DayOfWeek

data class FocusSchedule(
    val id: Long = 0L,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val weekdays: Set<DayOfWeek>,
    val enabled: Boolean,
    val affectedPackages: Set<String>,
    val appliesToFullApp: Boolean = true,
    val appliesToSelectiveContent: Boolean = true
) {
    val isOvernight: Boolean
        get() = (startHour * 60 + startMinute) > (endHour * 60 + endMinute)
}
