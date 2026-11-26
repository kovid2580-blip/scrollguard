package com.scrollguard.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollguard.app.data.entity.DailyReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReportDao {

    @Query("SELECT * FROM daily_reports WHERE date = :date")
    fun observeByDate(date: String): Flow<DailyReportEntity?>

    @Query("SELECT * FROM daily_reports WHERE date = :date")
    suspend fun getByDate(date: String): DailyReportEntity?

    @Query("SELECT * FROM daily_reports ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 14): Flow<List<DailyReportEntity>>

    @Query("SELECT * FROM daily_reports ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 14): List<DailyReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: DailyReportEntity)

    @Query("UPDATE daily_reports SET focusDuration = focusDuration + :deltaMs WHERE date = :date")
    suspend fun addFocusDuration(date: String, deltaMs: Long)

    @Query("DELETE FROM daily_reports")
    suspend fun clearAll()
}
