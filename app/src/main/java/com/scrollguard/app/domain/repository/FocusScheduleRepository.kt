package com.scrollguard.app.domain.repository

import com.scrollguard.app.domain.model.FocusSchedule
import kotlinx.coroutines.flow.Flow

interface FocusScheduleRepository {
    fun observeAllSchedules(): Flow<List<FocusSchedule>>
    suspend fun getAllSchedules(): List<FocusSchedule>
    suspend fun getEnabledSchedules(): List<FocusSchedule>
    suspend fun getScheduleById(id: Long): FocusSchedule?
    suspend fun upsertSchedule(schedule: FocusSchedule): Long
    suspend fun setScheduleEnabled(scheduleId: Long, enabled: Boolean)
    suspend fun deleteSchedule(scheduleId: Long)
    suspend fun replaceAllSchedules(schedules: List<FocusSchedule>)
}
