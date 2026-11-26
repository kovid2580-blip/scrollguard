package com.scrollguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.scrollguard.app.blocking.DetectionRuleRepository
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.scheduling.BootReceiver
import com.scrollguard.app.scheduling.ScheduleAlarmScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScrollGuardApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var blockingRuleRepository: BlockingRuleRepository
    @Inject lateinit var detectionRuleRepository: DetectionRuleRepository
    @Inject lateinit var scheduleAlarmScheduler: ScheduleAlarmScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            blockingRuleRepository.seedBuiltInRulesIfMissing()
            detectionRuleRepository.refreshNow()
            scheduleAlarmScheduler.rescheduleNextBoundary()
        }
        BootReceiver.enqueueUsageSyncWork(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_FOCUS_ALERTS,
                getString(R.string.notification_channel_focus_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_focus_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_FOCUS_ALERTS = "scrollguard_focus_alerts"
    }
}
