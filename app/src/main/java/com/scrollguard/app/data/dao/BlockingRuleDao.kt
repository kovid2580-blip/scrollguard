package com.scrollguard.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.scrollguard.app.data.entity.BlockingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockingRuleDao {

    @Query("SELECT * FROM blocking_rules ORDER BY blockType ASC, appName ASC")
    fun observeAll(): Flow<List<BlockingRuleEntity>>

    @Query("SELECT * FROM blocking_rules")
    suspend fun getAll(): List<BlockingRuleEntity>

    @Query("SELECT * FROM blocking_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<BlockingRuleEntity>

    @Query("SELECT * FROM blocking_rules WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): List<BlockingRuleEntity>

    @Query("SELECT * FROM blocking_rules WHERE id = :id")
    suspend fun getById(id: Long): BlockingRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: BlockingRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(rules: List<BlockingRuleEntity>)

    @Update
    suspend fun update(rule: BlockingRuleEntity)

    @Query("UPDATE blocking_rules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM blocking_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocking_rules")
    suspend fun clearAll()
}
