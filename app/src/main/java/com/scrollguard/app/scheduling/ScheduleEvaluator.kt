package com.scrollguard.app.scheduling

import com.scrollguard.app.domain.model.FocusSchedule
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleEvaluator @Inject constructor() {

    fun isScheduleActive(schedule: FocusSchedule, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (!schedule.enabled || schedule.weekdays.isEmpty()) return false

        val nowMinutes = now.hour * 60 + now.minute
        val startMinutes = schedule.startHour * 60 + schedule.startMinute
        val endMinutes = schedule.endHour * 60 + schedule.endMinute

        if (startMinutes == endMinutes) {
            // 24-hour schedule on selected weekdays
            return now.dayOfWeek in schedule.weekdays
        }

        return if (startMinutes < endMinutes) {
            // Same-day schedule (e.g., 09:00 - 17:00)
            now.dayOfWeek in schedule.weekdays && nowMinutes in startMinutes until endMinutes
        } else {
            // Overnight schedule (e.g., 22:00 - 07:00)
            val activeFromTodayStart =
                now.dayOfWeek in schedule.weekdays && nowMinutes >= startMinutes
            val previousDay = now.dayOfWeek.minus(1)
            val activeFromYesterdayOvernight =
                previousDay in schedule.weekdays && nowMinutes < endMinutes
            activeFromTodayStart || activeFromYesterdayOvernight
        }
    }

    fun activeSchedulesForPackage(
        schedules: List<FocusSchedule>,
        packageName: String,
        now: ZonedDateTime = ZonedDateTime.now()
    ): List<FocusSchedule> {
        return schedules.filter { schedule ->
            isScheduleActive(schedule, now) &&
                (schedule.affectedPackages.isEmpty() || packageName in schedule.affectedPackages)
        }
    }

    fun nextBoundaryEpochMs(
        schedules: List<FocusSchedule>,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Long? {
        val enabled = schedules.filter { it.enabled && it.weekdays.isNotEmpty() }
        if (enabled.isEmpty()) return null

        val candidates = mutableListOf<ZonedDateTime>()
        for (schedule in enabled) {
            for (dayOffset in 0L..7L) {
                val date = now.toLocalDate().plusDays(dayOffset)
                val dow = date.dayOfWeek
                if (dow in schedule.weekdays) {
                    val startCandidate = date.atTime(schedule.startHour, schedule.startMinute)
                        .atZone(now.zone)
                    if (startCandidate.isAfter(now)) {
                        candidates.add(startCandidate)
                    }
                    val endDate = if (schedule.isOvernight) date.plusDays(1) else date
                    val endCandidate = endDate.atTime(schedule.endHour, schedule.endMinute)
                        .atZone(now.zone)
                    if (endCandidate.isAfter(now)) {
                        candidates.add(endCandidate)
                    }
                }
            }
        }
        return candidates.minOrNull()?.toInstant()?.toEpochMilli()
    }
}
