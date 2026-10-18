package com.trackermaster.core.data.progress

import com.trackermaster.core.database.entity.HabitScheduleEntity
import com.trackermaster.core.domain.habits.HabitScheduleVersion
import com.trackermaster.core.domain.model.ProgressType
import com.trackermaster.core.domain.model.ScheduleType
import java.time.LocalDate

fun HabitScheduleEntity.toDomain() = HabitScheduleVersion(habitId,LocalDate.ofEpochDay(effectiveFromEpochDay),
    ProgressType.valueOf(progressType),ScheduleType.valueOf(scheduleType),scheduleDaysBitmask,targetCount,
    targetDurationMs,checklistSize,archived)
fun HabitScheduleVersion.toEntity() = HabitScheduleEntity(habitId,effectiveFrom.toEpochDay(),progressType.name,
    scheduleType.name,scheduleDaysBitmask,targetCount,targetDurationMs,checklistSize,archived)
