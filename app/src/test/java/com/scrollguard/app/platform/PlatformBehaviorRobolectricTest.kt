package com.scrollguard.app.platform

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scrollguard.app.blocking.BlockDecision
import com.scrollguard.app.blocking.BlockingOverlayManager
import com.scrollguard.app.blocking.BlockingReason
import com.scrollguard.app.data.dao.FocusScheduleDao
import com.scrollguard.app.data.entity.FocusScheduleEntity
import com.scrollguard.app.data.local.ScrollGuardDatabase
import com.scrollguard.app.data.repository.FocusScheduleRepositoryImpl
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.monitoring.PermissionChecker
import com.scrollguard.app.monitoring.UsageStatsMonitor
import com.scrollguard.app.scheduling.ScheduleAlarmScheduler
import com.scrollguard.app.scheduling.ScheduleEvaluator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class PlatformBehaviorRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: ScrollGuardDatabase
    private lateinit var scheduleDao: FocusScheduleDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ScrollGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduleDao = db.focusScheduleDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `scenario 13 - schedule alarm scheduler sets alarm from persisted schedules for reboot recovery`() = runTest {
        scheduleDao.upsert(
            FocusScheduleEntity(
                name = "Night Focus",
                startHour = 22,
                startMinute = 0,
                endHour = 7,
                endMinute = 0,
                weekdays = "1,2,3,4,5,6,7",
                enabled = true,
                affectedPackages = "com.instagram.android"
            )
        )
        val repo = FocusScheduleRepositoryImpl(scheduleDao)
        val scheduler = ScheduleAlarmScheduler(context, repo, ScheduleEvaluator())
        scheduler.rescheduleNextBoundary()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = Shadows.shadowOf(alarmManager)
        assertNotNull(shadowAlarm.peekNextScheduledAlarm())
    }

    @Test
    fun `scenario 14 - usage monitor never fabricates data when permission is not granted`() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ERRORED
        )
        val checker = PermissionChecker(context)
        val monitor = UsageStatsMonitor(context, checker)
        assertFalse(checker.isUsageAccessGranted())
        assertTrue(monitor.queryTodayForegroundDurations().isEmpty())
    }

    @Test
    fun `scenario 15 and 16 - overlay permission revocation and repeated calls do not crash or duplicate`() = runTest {
        val checker = PermissionChecker(context)
        val status = checker.refresh()
        assertFalse(status.overlayGranted)
        assertFalse(status.accessibilityEnabled)

        val overlayManager = BlockingOverlayManager(context)
        val decision = BlockDecision(
            shouldBlock = true,
            packageName = "com.instagram.android",
            appName = "Instagram",
            contentCategory = ContentCategory.INSTAGRAM_REELS,
            reason = BlockingReason.SELECTIVE_CONTENT_RULE,
            confidence = 0.9f,
            messageTitle = "Instagram Reels is blocked.",
            messageBody = "Take back your time."
        )

        // Repeated calls when permission is missing/revoked must be a safe no-op without throwing
        repeat(5) {
            overlayManager.showOrUpdate(
                decision = decision,
                dailyAttemptCount = it + 1,
                allowTempUnlock = false,
                remainingUnlocks = 0,
                onReturn = {},
                onTempUnlock = {}
            )
        }
        assertFalse(overlayManager.isShowing)
        assertEquals(0, db.blockedAttemptDao().countBetween(0L, Long.MAX_VALUE))
    }
}
