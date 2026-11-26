package com.scrollguard.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scrollguard.app.blocking.DetectionRuleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleAlarmScheduler: ScheduleAlarmScheduler
    @Inject lateinit var detectionRuleRepository: DetectionRuleRepository

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                detectionRuleRepository.refreshNow()
                scheduleAlarmScheduler.rescheduleNextBoundary()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
