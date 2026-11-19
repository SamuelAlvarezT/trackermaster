package com.trackermaster.core.data.progress

import androidx.room.withTransaction
import com.trackermaster.core.data.mapper.toDomain
import com.trackermaster.core.data.mapper.toEntity
import com.trackermaster.core.data.mapper.toEpochMilli
import com.trackermaster.core.database.HabitDynamicGoalEntity
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.domain.habits.DynamicDuration
import com.trackermaster.core.domain.habits.HabitProgressRules
import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.HabitLog
import com.trackermaster.core.domain.model.ProgressType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Materializes each scheduled target once; already recorded targets are never recalculated. */
@Singleton
class HabitDynamicTargets @Inject constructor(private val db: TrackermasterDatabase) {
    suspend fun ensureAll(day: LocalDate) = db.withTransaction {
        db.habitDao().dynamicHabits().forEach { ensureInTransaction(it.toDomain(), day) }
    }

    suspend fun ensureInTransaction(habit: Habit, through: LocalDate) {
        val start = habit.dynamicStartEpochDay?.let(LocalDate::ofEpochDay) ?: return
        if (!habit.dynamicDurationEnabled || habit.archived || through < start ||
            habit.progressType !in listOf(ProgressType.TIMER, ProgressType.STOPWATCH)) return
        val minimum = habit.dynamicMinimumMinutes.toLong()
        val maximum = habit.dynamicMaximumMinutes.toLong()
        if (minimum < 1 || maximum < minimum || habit.dynamicInitialMinutes.toLong() !in minimum..maximum ||
            habit.dynamicAdjustmentPercent !in 0..200) return
        val dao = db.habitDynamicGoalDao()
        val saved = dao.forHabit(habit.id).filter { it.epochDay >= start.toEpochDay() }.associateBy { it.epochDay }
        val logs = db.habitLogDao().getAllForHabit(habit.id).map { it.toDomain() }.groupBy { it.date }
            .mapValues { (_, rows) -> rows.maxBy { it.id } }.toMutableMap()
        val history = db.progressDao().history(habit.id).map { it.toDomain() }
        var previous: HabitDynamicGoalEntity? = null
        var date = start
        while (date <= through) {
            val version = HabitProgressRules.versionOn(habit, date, history)
            if (HabitProgressRules.isDue(habit, date, version)) {
                val existingGoal = saved[date.toEpochDay()]
                if (existingGoal != null) {
                    previous = existingGoal
                } else {
                    val prior = previous
                    val priorProgress = prior?.let { logs[LocalDate.ofEpochDay(it.epochDay)]?.durationMs ?: 0L }
                    val next = prior?.let { DynamicDuration.next(it.targetMinutes, priorProgress ?: 0L, minimum,
                        maximum, true, habit.dynamicAdjustmentPercent) }
                    val existingLog = logs[date]
                    // A fixed target already recorded for today remains authoritative.
                    val minutes = existingLog?.targetDurationMsAtLog?.takeIf { it > 0 }?.let { (it + 59_999) / 60_000 }
                        ?: next?.nextMinutes ?: habit.dynamicInitialMinutes.toLong()
                    val goal = HabitDynamicGoalEntity(habit.id, date.toEpochDay(), minutes,
                        prior?.targetMinutes, priorProgress, if (existingLog?.targetDurationMsAtLog != null) "EXISTING" else next?.reason?.name ?: "INITIAL")
                    dao.insert(goal)
                    val target = minutes * 60_000L
                    if (existingLog == null) {
                        val log = HabitLog(habitId = habit.id, date = date, targetDurationMsAtLog = target,
                            progressTypeAtLog = version.progressType, targetCountAtLog = version.targetCount,
                            checklistSizeAtLog = version.checklistSize)
                        val id = db.habitLogDao().insert(log.toEntity())
                        logs[date] = log.copy(id = id)
                    } else if (existingLog.targetDurationMsAtLog == null) {
                        val log = existingLog.copy(targetDurationMsAtLog = target)
                        db.habitLogDao().update(log.toEntity())
                        logs[date] = log
                    }
                    previous = goal
                }
            }
            date = date.plusDays(1)
        }
    }
}
