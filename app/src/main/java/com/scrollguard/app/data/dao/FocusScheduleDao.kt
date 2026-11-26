package com.scrollguard.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.scrollguard.app.data.entity.FocusScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusScheduleDao {

    @Query("SELECT * FROM focus_schedules ORDER BY startHour ASC, startMinute ASC")
    fun observeAll(): Flow<List<FocusScheduleEntity>>

    @Query("SELECT * FROM focus_schedules")
    suspend fun getAll(): List<FocusScheduleEntity>

    @Query("SELECT * FROM focus_schedules WHERE enabled = 1")
    suspend fun getEnabled(): List<FocusScheduleEntity>

    @Query("SELECT * FROM focus_schedules WHERE id = :id")
    suspend fun getById(id: Long): FocusScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: FocusScheduleEntity): Long

    @Update
    suspend fun update(schedule: FocusScheduleEntity)

    @Query("UPDATE focus_schedules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM focus_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM focus_schedules")
    suspend fun clearAll()
}
