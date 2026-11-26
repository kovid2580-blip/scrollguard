package com.scrollguard.app.di

import android.content.Context
import androidx.room.Room
import com.scrollguard.app.data.dao.BlockedAttemptDao
import com.scrollguard.app.data.dao.BlockingRuleDao
import com.scrollguard.app.data.dao.DailyReportDao
import com.scrollguard.app.data.dao.FocusScheduleDao
import com.scrollguard.app.data.dao.UsageRecordDao
import com.scrollguard.app.data.local.ScrollGuardDatabase
import com.scrollguard.app.data.repository.BlockedAttemptRepositoryImpl
import com.scrollguard.app.data.repository.BlockingRuleRepositoryImpl
import com.scrollguard.app.data.repository.FocusScheduleRepositoryImpl
import com.scrollguard.app.data.repository.InstalledAppsRepositoryImpl
import com.scrollguard.app.data.repository.UsageRepositoryImpl
import com.scrollguard.app.domain.repository.BlockedAttemptRepository
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.domain.repository.FocusScheduleRepository
import com.scrollguard.app.domain.repository.InstalledAppsRepository
import com.scrollguard.app.domain.repository.UsageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScrollGuardDatabase {
        return Room.databaseBuilder(
            context,
            ScrollGuardDatabase::class.java,
            ScrollGuardDatabase.DATABASE_NAME
        )
            .addMigrations(ScrollGuardDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideBlockingRuleDao(db: ScrollGuardDatabase): BlockingRuleDao = db.blockingRuleDao()

    @Provides
    fun provideFocusScheduleDao(db: ScrollGuardDatabase): FocusScheduleDao = db.focusScheduleDao()

    @Provides
    fun provideUsageRecordDao(db: ScrollGuardDatabase): UsageRecordDao = db.usageRecordDao()

    @Provides
    fun provideBlockedAttemptDao(db: ScrollGuardDatabase): BlockedAttemptDao = db.blockedAttemptDao()

    @Provides
    fun provideDailyReportDao(db: ScrollGuardDatabase): DailyReportDao = db.dailyReportDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBlockingRuleRepository(impl: BlockingRuleRepositoryImpl): BlockingRuleRepository

    @Binds
    @Singleton
    abstract fun bindFocusScheduleRepository(impl: FocusScheduleRepositoryImpl): FocusScheduleRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindBlockedAttemptRepository(impl: BlockedAttemptRepositoryImpl): BlockedAttemptRepository

    @Binds
    @Singleton
    abstract fun bindInstalledAppsRepository(impl: InstalledAppsRepositoryImpl): InstalledAppsRepository
}
