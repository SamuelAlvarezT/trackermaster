package com.trackermaster.app.ui

import com.trackermaster.core.domain.model.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayPlanTest {
    @Test fun includesDueAndUndatedTasksButNotFutureCompletedOrCancelled() {
        val today = LocalDate.of(2026, 11, 18)
        val tasks = listOf(Task(id=1,title="overdue",plannedDate="2026-11-17"), Task(id=2,title="undated"),
            Task(id=3,title="today",plannedDate="2026-11-18"), Task(id=4,title="future",plannedDate="2026-11-19"),
            Task(id=5,title="complete",completed=true), Task(id=6,title="cancelled",cancelledAtEpoch=1))
        assertEquals(listOf(1L,2L,3L), buildTodayPlan(today, emptyList(), tasks).tasks.map { it.id })
    }
    @Test fun excludesCompletedAndArchivedHabits() {
        val today=LocalDate.of(2026,11,18)
        val habit=Habit(id=1,name="Timed",progressType=ProgressType.TIMER,scheduleType=ScheduleType.DAILY,targetDurationMs=1_800_000)
        val done=HabitLog(habitId=1,date=today,completed=true)
        val archived=habit.copy(id=2,archived=true)
        val pending=habit.copy(id=3)
        assertEquals(listOf(3L), buildTodayPlan(today,listOf(habit to done,archived to null,pending to null),emptyList()).habits.map { it.first.id })
    }
}
