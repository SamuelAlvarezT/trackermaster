package com.trackermaster.core.domain.habits

import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.model.*
import java.time.LocalDate

data class HabitScheduleVersion(
    val habitId: Long,
    val effectiveFrom: LocalDate,
    val progressType: ProgressType,
    val scheduleType: ScheduleType,
    val scheduleDaysBitmask: Int,
    val targetCount: Int,
    val targetDurationMs: Long,
    val checklistSize: Int,
    val archived: Boolean = false,
)

data class PendingHabit(
    val habit: Habit,
    val equivalentDays: Double,
    val todayDeficit: Double,
    val trackingFrom: LocalDate,
    val creationDateKnown: Boolean,
) {
    val includesProvisionalToday: Boolean get() = todayDeficit > 0.0
}

object HabitProgressRules {
    fun version(habit: Habit, from: LocalDate) = HabitScheduleVersion(habit.id, from,
        habit.progressType, habit.scheduleType, habit.scheduleDaysBitmask, habit.targetCount,
        habit.targetDurationMs, habit.checklistItems.size, habit.archived)

    fun versionOn(habit: Habit, date: LocalDate, history: List<HabitScheduleVersion>): HabitScheduleVersion =
        history.filter { it.habitId == habit.id && it.effectiveFrom <= date }.maxByOrNull { it.effectiveFrom }
            ?: version(habit, date)

    fun isDue(habit: Habit, date: LocalDate, version: HabitScheduleVersion): Boolean =
        HabitScheduleLogic.isDueOnDate(habit.copy(archived = version.archived,
            scheduleType = version.scheduleType, scheduleDaysBitmask = version.scheduleDaysBitmask), date)

    fun progress(log: HabitLog?, version: HabitScheduleVersion): Double {
        if (log == null) return 0.0
        if (log.completed) return 1.0
        val type = log.progressTypeAtLog ?: version.progressType
        val ratio = when (type) {
            ProgressType.TIMER, ProgressType.STOPWATCH -> {
                val target = log.targetDurationMsAtLog ?: version.targetDurationMs
                if (target <= 0) 0.0 else log.durationMs.coerceAtLeast(0).toDouble() / target
            }
            ProgressType.COUNTER -> log.value.coerceAtLeast(0).toDouble() /
                (log.targetCountAtLog ?: version.targetCount).coerceAtLeast(1)
            ProgressType.CHECKLIST -> log.checklistState.values.count { it }.toDouble() /
                (log.checklistSizeAtLog ?: version.checklistSize).coerceAtLeast(1)
            ProgressType.CHECKBOX -> 0.0
        }
        return ratio.coerceIn(0.0, 1.0)
    }

    fun pending(habits: List<Habit>, logs: List<HabitLog>, history: List<HabitScheduleVersion>,
        today: LocalDate, limit: Int = 3): List<PendingHabit> {
        require(limit >= 0)
        val byHabit = logs.groupBy { it.habitId }
        val versions = history.groupBy { it.habitId }
        return habits.asSequence().filterNot { it.archived }.mapNotNull { habit ->
            val from = if(habit.createdEpochDay==0L && !habit.creationDateKnown)
                byHabit[habit.id].orEmpty().minOfOrNull { it.date } ?: today
                else LocalDate.ofEpochDay(habit.createdEpochDay)
            if(from>today) return@mapNotNull null
            val dates = byHabit[habit.id].orEmpty().filter { it.date in from..today }
                .groupBy { it.date }.mapValues { (_, rows) -> rows.maxBy { it.id } }
            val timeline = versions[habit.id].orEmpty().sortedBy { it.effectiveFrom }
            val lastComplete = dates.values.filter { progress(it, versionOn(habit,it.date,timeline)) >= 1.0 }
                .maxOfOrNull { it.date }
            var date = lastComplete?.plusDays(1)?.coerceAtLeast(from) ?: from
            var total = 0.0
            var current = 0.0
            while (date <= today) {
                val v = versionOn(habit,date,timeline)
                if (isDue(habit,date,v)) {
                    val deficit = 1.0 - progress(dates[date],v)
                    total += deficit
                    if (date == today) current = deficit
                }
                date = date.plusDays(1)
            }
            if (total <= 1e-9) null else PendingHabit(habit,total,current,from,habit.creationDateKnown)
        }.sortedWith(compareByDescending<PendingHabit> { it.equivalentDays }.thenBy { it.habit.id })
            .take(limit).toList()
    }
}
