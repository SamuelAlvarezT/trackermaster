package com.trackermaster.feature.habits

import com.trackermaster.core.domain.habits.HabitProgressRules
import com.trackermaster.core.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HabitDateScheduleTest {
    @Test fun consultedDateUsesTheScheduleThatWasEffectiveThen() {
        val monday=LocalDate.of(2026, 10, 19)
        val habit=Habit(id=1,name="Read",progressType=ProgressType.TIMER,scheduleType=ScheduleType.WEEKLY,
            scheduleDaysBitmask=1 shl 1,targetDurationMs=1_800_000,createdEpochDay=monday.toEpochDay())
        val earlier=HabitProgressRules.version(habit.copy(scheduleDaysBitmask=1),monday)
        val later=HabitProgressRules.version(habit,monday.plusDays(7))
        assertTrue(habitDue(habit,monday,monday.plusDays(8),listOf(earlier,later)))
        assertFalse(habitDue(habit,monday.plusDays(7),monday.plusDays(8),listOf(earlier,later)))
        assertTrue(habitDue(habit,monday.plusDays(8),monday.plusDays(8),listOf(earlier,later)))
    }
    @Test fun missingHistoryIsNotAssumedForPastDates() {
        val habit=Habit(id=1,name="Read",progressType=ProgressType.CHECKBOX,scheduleType=ScheduleType.DAILY,
            createdEpochDay=LocalDate.of(2026,1,1).toEpochDay())
        assertFalse(habitDue(habit,LocalDate.of(2026,10,1),LocalDate.of(2026,11,1),emptyList()))
    }
}
