package com.trackermaster.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habit_schedule_history", primaryKeys = ["habitId","effectiveFromEpochDay"], indices = [Index("habitId")])
data class HabitScheduleEntity(
    val habitId: Long,
    val effectiveFromEpochDay: Long,
    val progressType: String,
    val scheduleType: String,
    val scheduleDaysBitmask: Int,
    val targetCount: Int,
    val targetDurationMs: Long,
    val checklistSize: Int,
    val archived: Boolean,
)

@Entity(tableName = "focus_runtime")
data class FocusRuntimeEntity(
    @PrimaryKey val id: Int = 1,
    val sessionId: Long,
    val category: String,
    val habitId: Long?,
    val plannedMs: Long,
    val effectiveMs: Long,
    val status: String,
    val lastElapsedMs: Long,
    val lastWallMs: Long,
    val bootCount: Int,
)

@Entity(tableName = "focus_habit_contributions", primaryKeys = ["sessionId","dateEpochDay"], indices = [Index("habitId")])
data class FocusHabitContributionEntity(
    val sessionId: Long,
    val dateEpochDay: Long,
    val habitId: Long?,
    val durationMs: Long,
)
