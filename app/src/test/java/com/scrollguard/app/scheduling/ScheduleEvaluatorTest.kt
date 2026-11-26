package com.scrollguard.app.scheduling

import com.scrollguard.app.domain.model.FocusSchedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleEvaluatorTest {

    private val evaluator = ScheduleEvaluator()

    @Test
    fun `scenario 11 - same-day schedule activates only during window and selected weekdays`() {
        val schedule = FocusSchedule(
            id = 1L,
            name = "Deep Work",
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            enabled = true,
            affectedPackages = setOf("com.instagram.android")
        )

        val wed10am = ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, ZoneId.of("UTC"))
        val wed18pm = ZonedDateTime.of(2026, 11, 25, 18, 0, 0, 0, ZoneId.of("UTC"))
        val thu10am = ZonedDateTime.of(2026, 11, 26, 10, 0, 0, 0, ZoneId.of("UTC"))

        assertTrue(evaluator.isScheduleActive(schedule, wed10am))
        assertFalse(evaluator.isScheduleActive(schedule, wed18pm))
        assertFalse(evaluator.isScheduleActive(schedule, thu10am))
    }

    @Test
    fun `scenario 12 - overnight schedule mon-fri 22_00 to 07_00 spans midnight accurately`() {
        val overnight = FocusSchedule(
            id = 2L,
            name = "Sleep Guard",
            startHour = 22,
            startMinute = 0,
            endHour = 7,
            endMinute = 0,
            weekdays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            enabled = true,
            affectedPackages = setOf("com.instagram.android", "com.google.android.youtube")
        )

        // Friday 23:30 -> Active
        val fridayNight = ZonedDateTime.of(2026, 11, 27, 23, 30, 0, 0, ZoneId.of("UTC"))
        // Saturday 06:30 (spillover from Friday night) -> Active
        val saturdayEarly = ZonedDateTime.of(2026, 11, 28, 6, 30, 0, 0, ZoneId.of("UTC"))
        // Saturday 07:05 -> Inactive
        val saturdayMorning = ZonedDateTime.of(2026, 11, 28, 7, 5, 0, 0, ZoneId.of("UTC"))
        // Saturday 23:00 (Saturday not in weekdays) -> Inactive
        val saturdayNight = ZonedDateTime.of(2026, 11, 28, 23, 0, 0, 0, ZoneId.of("UTC"))

        assertTrue(evaluator.isScheduleActive(overnight, fridayNight))
        assertTrue(evaluator.isScheduleActive(overnight, saturdayEarly))
        assertFalse(evaluator.isScheduleActive(overnight, saturdayMorning))
        assertFalse(evaluator.isScheduleActive(overnight, saturdayNight))

        val nextBoundary = evaluator.nextBoundaryEpochMs(listOf(overnight), saturdayMorning)
        assertNotNull(nextBoundary)
    }
}
