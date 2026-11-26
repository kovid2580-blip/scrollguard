package com.scrollguard.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.scrollguard.app.blocking.DetectionRuleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleAlarmScheduler: ScheduleAlarmScheduler
    @Inject lateinit var detectionRuleRepository: DetectionRuleRepository

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                detectionRuleRepository.refreshNow()
                scheduleAlarmScheduler.rescheduleNextBoundary()
                enqueueUsageSyncWork(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun enqueueUsageSyncWork(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueue(OneTimeWorkRequestBuilder<UsageSyncWorker>().build())
            val periodic = PeriodicWorkRequestBuilder<UsageSyncWorker>(30, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(
                UsageSyncWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }
}
