package com.trackermaster.core.domain.habits

import com.trackermaster.core.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HabitTimeSummaryTest {
    @Test fun plannedAndRemainingIncludePartialManualAndExcessWithoutDoubleCounting() {
        val habits = (1L..3L).map { Habit(it, "Timer", ProgressType.TIMER, ScheduleType.DAILY, targetDurationMs=1_800_000) }
        val day = LocalDate.of(2026, 11, 3)
        val rows = listOf(habits[0] to HabitLog(habitId=1, date=day, durationMs=600_000),
            habits[1] to HabitLog(habitId=2, date=day, completed=true, completionSource="manual"),
            habits[2] to HabitLog(habitId=3, date=day, durationMs=3_600_000, completionSource="measured"))
        val result=habitTimeSummary(rows)
        assertEquals(5_400_000L,result.plannedMs)
        assertEquals(1_200_000L,result.remainingMs)
        assertEquals("1 h 30 min", formatHabitMinutes(result.plannedMs))
    }
    @Test fun nonTimedHabitsDoNotInventDuration() {
        val habit=Habit(1,"Count",ProgressType.COUNTER,ScheduleType.DAILY,targetDurationMs=600_000)
        assertEquals(HabitTimeSummary(),habitTimeSummary(listOf(habit to null)))
    }
}
