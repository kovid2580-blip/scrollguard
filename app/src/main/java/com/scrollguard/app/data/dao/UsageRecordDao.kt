package com.scrollguard.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollguard.app.data.entity.UsageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY foregroundDuration DESC")
    fun observeByDate(date: String): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE date = :date ORDER BY foregroundDuration DESC")
    suspend fun getByDate(date: String): List<UsageRecordEntity>

    @Query("SELECT * FROM usage_records WHERE packageName = :packageName AND date = :date LIMIT 1")
    suspend fun getForPackageAndDate(packageName: String, date: String): UsageRecordEntity?

    @Query("SELECT * FROM usage_records WHERE date >= :startDate ORDER BY date ASC")
    fun observeSince(startDate: String): Flow<List<UsageRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<UsageRecordEntity>)

    @Query("DELETE FROM usage_records WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
