package com.trackermaster.core.domain

import com.trackermaster.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitScheduleLogicTest {

    @Test
    fun dailyHabitAlwaysDue() {
        val habit = Habit(name = "Water", progressType = ProgressType.CHECKBOX, scheduleType = ScheduleType.DAILY, colorArgb = 0)
        assertTrue(HabitScheduleLogic.isDueOnDate(habit, LocalDate.now()))
    }

    @Test
    fun streakCountsConsecutiveDays() {
        val habit = Habit(id = 1, name = "Read", progressType = ProgressType.CHECKBOX, scheduleType = ScheduleType.DAILY, colorArgb = 0)
        val today = LocalDate.of(2026, 5, 21)
        val logs = listOf(
            HabitLog(habitId = 1, date = today, completed = true),
            HabitLog(habitId = 1, date = today.minusDays(1), completed = true),
            HabitLog(habitId = 1, date = today.minusDays(2), completed = true),
        )
        val streak = HabitScheduleLogic.calculateStreak(habit, logs, today)
        assertEquals(3, streak.current)
    }

    @Test
    fun focusScoreInRange() {
        val sessions = listOf(
            FocusSession(category = FocusCategoryType.WORK, plannedMinutes = 25, actualSeconds = 1500, completed = true, completedAt = java.time.LocalDateTime.now()),
        )
        val score = HabitScheduleLogic.calculateFocusScore(sessions)
        assertTrue(score in 0..100)
    }
}
