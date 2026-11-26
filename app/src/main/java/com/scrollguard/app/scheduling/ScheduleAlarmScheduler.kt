package com.scrollguard.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.scrollguard.app.domain.repository.FocusScheduleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleRepository: FocusScheduleRepository,
    private val scheduleEvaluator: ScheduleEvaluator
) {
    suspend fun rescheduleNextBoundary() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val schedules = scheduleRepository.getEnabledSchedules()
        val nextBoundaryMs = scheduleEvaluator.nextBoundaryEpochMs(schedules, ZonedDateTime.now())

        val pendingIntent = boundaryPendingIntent()
        alarmManager.cancel(pendingIntent)

        if (nextBoundaryMs != null) {
            // Use inexact allow-while-idle alarm; no SCHEDULE_EXACT_ALARM special permission required.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextBoundaryMs,
                pendingIntent
            )
        }
    }

    private fun boundaryPendingIntent(): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_SCHEDULE_BOUNDARY
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BOUNDARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_SCHEDULE_BOUNDARY = "com.scrollguard.app.ACTION_SCHEDULE_BOUNDARY"
        private const val REQUEST_CODE_BOUNDARY = 4001
    }
}
