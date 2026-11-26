package com.scrollguard.app.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scrollguard.app.blocking.DetectionRuleRepository
import com.scrollguard.app.domain.repository.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageRepository,
    private val scheduleAlarmScheduler: ScheduleAlarmScheduler,
    private val detectionRuleRepository: DetectionRuleRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            usageRepository.syncTodayUsageFromSystem()
            detectionRuleRepository.refreshNow()
            scheduleAlarmScheduler.rescheduleNextBoundary()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "scrollguard_usage_sync"
    }
}
