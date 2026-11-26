package com.scrollguard.app.data.repository

import com.scrollguard.app.data.dao.FocusScheduleDao
import com.scrollguard.app.data.entity.FocusScheduleEntity
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.domain.repository.FocusScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusScheduleRepositoryImpl @Inject constructor(
    private val dao: FocusScheduleDao
) : FocusScheduleRepository {

    override fun observeAllSchedules(): Flow<List<FocusSchedule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllSchedules(): List<FocusSchedule> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getEnabledSchedules(): List<FocusSchedule> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun getScheduleById(id: Long): FocusSchedule? =
        dao.getById(id)?.toDomain()

    override suspend fun upsertSchedule(schedule: FocusSchedule): Long =
        dao.upsert(schedule.toEntity())

    override suspend fun setScheduleEnabled(scheduleId: Long, enabled: Boolean) {
        dao.setEnabled(scheduleId, enabled)
    }

    override suspend fun deleteSchedule(scheduleId: Long) {
        dao.deleteById(scheduleId)
    }

    override suspend fun replaceAllSchedules(schedules: List<FocusSchedule>) {
        dao.clearAll()
        schedules.forEach { dao.upsert(it.toEntity()) }
    }

    private fun FocusScheduleEntity.toDomain(): FocusSchedule {
        val days = weekdays.split(",")
            .mapNotNull { token -> token.trim().toIntOrNull()?.let { runCatching { DayOfWeek.of(it) }.getOrNull() } }
            .toSet()
        val packages = affectedPackages.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return FocusSchedule(
            id = id,
            name = name,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            weekdays = days,
            enabled = enabled,
            affectedPackages = packages,
            appliesToFullApp = appliesToFullApp,
            appliesToSelectiveContent = appliesToSelectiveContent
        )
    }

    private fun FocusSchedule.toEntity(): FocusScheduleEntity =
        FocusScheduleEntity(
            id = id,
            name = name,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            weekdays = weekdays.sortedBy { it.value }.joinToString(",") { it.value.toString() },
            enabled = enabled,
            affectedPackages = affectedPackages.sorted().joinToString(","),
            appliesToFullApp = appliesToFullApp,
            appliesToSelectiveContent = appliesToSelectiveContent
        )
}
