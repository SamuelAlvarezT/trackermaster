package com.trackermaster.core.domain.habits

import com.trackermaster.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UnrecordedHabitDaysTest {
    @Test fun reportsOnlyBlankScheduledPastDaysAcrossEntireHistory() {
        val today = LocalDate.of(2026, 11, 16)
        val habit = Habit(id = 1, name = "Leer", progressType = ProgressType.TIMER, scheduleType = ScheduleType.DAILY,
            createdEpochDay = today.minusDays(40).toEpochDay())
        val archived = habit.copy(id = 2, archived = true)
        val logs = listOf(HabitLog(habitId = 1, date = today.minusDays(2), value = -1),
            HabitLog(habitId = 1, date = today.minusDays(3), durationMs = 1000))
        val result = HabitProgressRules.unrecordedDays(listOf(habit, archived), logs, emptyList(), today)
        assertEquals(38, result.size)
        assertEquals(today.minusDays(1), result.first().date)
        assertEquals(today.minusDays(40), result.last().date)
    }

    @Test fun usesHistoricalScheduleAndExcludesUnscheduledDates() {
        val today = LocalDate.of(2026, 11, 16)
        val habit = Habit(id = 1, name = "Leer", progressType = ProgressType.CHECKBOX, scheduleType = ScheduleType.DAILY,
            createdEpochDay = today.minusDays(2).toEpochDay())
        val pausedVersion = HabitProgressRules.version(habit.copy(archived = true), today.minusDays(1))
        val result = HabitProgressRules.unrecordedDays(listOf(habit), emptyList(), listOf(pausedVersion), today)
        assertEquals(listOf(today.minusDays(2)), result.map { it.date })
    }
}
