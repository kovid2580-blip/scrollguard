package com.scrollguard.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scrollguard.app.data.dao.BlockedAttemptDao
import com.scrollguard.app.data.dao.BlockingRuleDao
import com.scrollguard.app.data.dao.DailyReportDao
import com.scrollguard.app.data.dao.FocusScheduleDao
import com.scrollguard.app.data.dao.UsageRecordDao
import com.scrollguard.app.data.entity.BlockedAttemptEntity
import com.scrollguard.app.data.entity.BlockingRuleEntity
import com.scrollguard.app.data.entity.DailyReportEntity
import com.scrollguard.app.data.entity.FocusScheduleEntity
import com.scrollguard.app.data.entity.UsageRecordEntity

@Database(
    entities = [
        BlockingRuleEntity::class,
        FocusScheduleEntity::class,
        UsageRecordEntity::class,
        BlockedAttemptEntity::class,
        DailyReportEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ScrollGuardDatabase : RoomDatabase() {
    abstract fun blockingRuleDao(): BlockingRuleDao
    abstract fun focusScheduleDao(): FocusScheduleDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun blockedAttemptDao(): BlockedAttemptDao
    abstract fun dailyReportDao(): DailyReportDao

    companion object {
        const val DATABASE_NAME = "scrollguard.db"

        /**
         * Explicit non-destructive migration scaffold for future schema revisions.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future additive schema changes without losing user rules/history.
            }
        }
    }
}
