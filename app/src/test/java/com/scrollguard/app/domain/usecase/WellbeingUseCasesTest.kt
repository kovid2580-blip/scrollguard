package com.scrollguard.app.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scrollguard.app.data.local.ScrollGuardDatabase
import com.scrollguard.app.data.repository.BlockingRuleRepositoryImpl
import com.scrollguard.app.data.repository.FocusScheduleRepositoryImpl
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.FocusSchedule
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class WellbeingUseCasesTest {

    private lateinit var db: ScrollGuardDatabase
    private lateinit var ruleRepo: BlockingRuleRepositoryImpl
    private lateinit var scheduleRepo: FocusScheduleRepositoryImpl
    private lateinit var backupRestoreUseCase: BackupRestoreUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ScrollGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        ruleRepo = BlockingRuleRepositoryImpl(db.blockingRuleDao(), json)
        scheduleRepo = FocusScheduleRepositoryImpl(db.focusScheduleDao())
        backupRestoreUseCase = BackupRestoreUseCase(ruleRepo, scheduleRepo, json)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `backup and restore round-trip preserves blocking rules and schedules`() = runTest {
        ruleRepo.seedBuiltInRulesIfMissing()
        ruleRepo.upsertRule(
            BlockingRule(
                packageName = "com.reddit.frontpage",
                appName = "Reddit",
                blockType = BlockType.FULL_APP,
                contentType = ContentCategory.FULL_APP,
                enabled = true,
                dailyLimitMinutes = 25
            )
        )
        scheduleRepo.upsertSchedule(
            FocusSchedule(
                name = "Evening Wind-Down",
                startHour = 22,
                startMinute = 0,
                endHour = 7,
                endMinute = 0,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                enabled = true,
                affectedPackages = setOf("com.instagram.android")
            )
        )

        val exportedJson = backupRestoreUseCase.exportConfigurationJson()
        db.blockingRuleDao().clearAll()
        db.focusScheduleDao().clearAll()

        val result = backupRestoreUseCase.importConfigurationJson(exportedJson)
        assertTrue(result.isSuccess)
        val (restoredRules, restoredSchedules) = result.getOrThrow()
        assertEquals(5, restoredRules)
        assertEquals(1, restoredSchedules)
        assertEquals(25, ruleRepo.getRulesForPackage("com.reddit.frontpage").first().dailyLimitMinutes)
    }
}
