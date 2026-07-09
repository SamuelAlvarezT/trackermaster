package com.trackermaster.core.domain

import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.HabitLog
import com.trackermaster.core.domain.model.ScheduleType
import com.trackermaster.core.domain.model.StreakInfo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object HabitScheduleLogic {

    fun isDueOnDate(habit: Habit, date: LocalDate): Boolean {
        if (habit.archived) return false
        return when (habit.scheduleType) {
            ScheduleType.DAILY -> true
            ScheduleType.WEEKLY -> {
                val bit = 1 shl (date.dayOfWeek.value - 1)
                if (habit.scheduleDaysBitmask != 0) {
                    habit.scheduleDaysBitmask and bit != 0
                } else {
                    date.dayOfWeek == DayOfWeek.MONDAY // Fallback
                }
            }
            ScheduleType.MONTHLY -> date.dayOfMonth == 1
            ScheduleType.YEARLY -> date.dayOfYear == 1
            ScheduleType.SPECIFIC_DAYS -> {
                val bit = 1 shl (date.dayOfWeek.value - 1)
                habit.scheduleDaysBitmask and bit != 0
            }
            ScheduleType.CUSTOM -> true
        }
    }

    fun isLogComplete(log: HabitLog): Boolean = log.completed || log.value > 0 || log.durationMs > 0

    fun calculateStreak(habit: Habit, logs: List<HabitLog>, today: LocalDate = LocalDate.now()): StreakInfo {
        val completedDates = logs
            .filter { isLogComplete(it) }
            .map { it.date }
            .toSet()

        var current = 0
        var date = today
        while (isDueOnDate(habit, date)) {
            if (date in completedDates) {
                current++
                date = date.minusDays(1)
            } else if (date == today) {
                date = date.minusDays(1)
            } else break
        }

        var best = 0
        var run = 0
        val sorted = completedDates.sorted()
        for (i in sorted.indices) {
            if (i == 0) run = 1
            else {
                val diff = ChronoUnit.DAYS.between(sorted[i - 1], sorted[i])
                run = if (diff == 1L) run + 1 else 1
            }
            best = maxOf(best, run)
        }
        best = maxOf(best, current)
        return StreakInfo(current, best)
    }

    fun defaultMoodLevels(): List<com.trackermaster.core.domain.model.MoodLevel> = listOf(
        com.trackermaster.core.domain.model.MoodLevel(1, "Awful", "😢", 0xFFE53935.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(2, "Bad", "😞", 0xFFEF5350.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(3, "Low", "😕", 0xFFFF7043.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(4, "Meh", "😐", 0xFFFFA726.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(5, "Okay", "🙂", 0xFFFFCA28.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(6, "Fine", "😊", 0xFFDCE775.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(7, "Good", "😄", 0xFFAED581.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(8, "Great", "😁", 0xFF66BB6A.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(9, "Amazing", "🤩", 0xFF26A69A.toInt()),
        com.trackermaster.core.domain.model.MoodLevel(10, "Perfect", "🥳", 0xFF42A5F5.toInt()),
    )

    fun calculateFocusScore(sessions: List<com.trackermaster.core.domain.model.FocusSession>): Int {
        val completed = sessions.filter { it.completed }
        if (completed.isEmpty()) return 0
        val totalPlanned = completed.sumOf { it.plannedMinutes }
        val totalActual = completed.sumOf { it.actualSeconds } / 60.0
        if (totalPlanned == 0) return 0
        return ((totalActual / totalPlanned) * 100).toInt().coerceIn(0, 100)
    }

    fun journalWritingStreak(dates: List<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        val set = dates.toSet()
        var streak = 0
        var d = today
        while (d in set) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }
}
