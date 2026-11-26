package com.scrollguard.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.scrollguard.app.data.entity.BlockedAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAttemptDao {

    @Query("SELECT * FROM blocked_attempts ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<BlockedAttemptEntity>>

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE timestamp >= :startOfDayMs AND timestamp < :endOfDayMs")
    fun observeCountBetween(startOfDayMs: Long, endOfDayMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE timestamp >= :startOfDayMs AND timestamp < :endOfDayMs")
    suspend fun countBetween(startOfDayMs: Long, endOfDayMs: Long): Int

    @Query("SELECT * FROM blocked_attempts WHERE timestamp >= :sinceMs ORDER BY timestamp DESC")
    suspend fun getSince(sinceMs: Long): List<BlockedAttemptEntity>

    @Insert
    suspend fun insert(attempt: BlockedAttemptEntity): Long

    @Query("DELETE FROM blocked_attempts")
    suspend fun clearAll()
}
