package com.trackermaster.core.domain

import com.trackermaster.core.domain.insights.*
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.domain.habits.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class LifeInsightsTest {
    private val today=LocalDate.of(2026,10,24)
    @Test fun rollingAndCalendarComparisonsUseEquivalentElapsedDays() {
        val rolling=LifePeriods.comparison(LifePeriod.LAST_SEVEN,today,today)
        assertEquals(DateSpan(today.minusDays(6),today),rolling.current)
        assertEquals(DateSpan(today.minusDays(13),today.minusDays(7)),rolling.previous)
        val month=LifePeriods.comparison(LifePeriod.MONTH,today,today)
        assertEquals(DateSpan(LocalDate.of(2026,9,1),LocalDate.of(2026,9,24)),month.previous)
        assertTrue(month.provisional)
        val closed=LifePeriods.comparison(LifePeriod.MONTH,today.minusMonths(1),today)
        assertEquals(LocalDate.of(2026,8,31),closed.previous.end)
        assertFalse(closed.provisional)
    }
    @Test fun calendarBoundariesHandleLeapYearQuarterHalfAndSunday() {
        assertEquals(LocalDate.of(2024,2,29),LifePeriods.bounds(LifePeriod.MONTH,LocalDate.of(2024,2,1)).end)
        assertEquals(LocalDate.of(2026,10,1),LifePeriods.bounds(LifePeriod.QUARTER,today).start)
        assertEquals(LocalDate.of(2026,7,1),LifePeriods.bounds(LifePeriod.HALF,today).start)
        assertEquals(LocalDate.of(2026,1,1),LifePeriods.bounds(LifePeriod.YEAR,today).start)
        assertTrue(LifePeriods.comparison(LifePeriod.WEEK,today.plusDays(1),today.plusDays(1)).provisional)
    }
    @Test fun halfTimerAndScheduledDaysAreProportionalAndLatestDuplicateWins() {
        val h=Habit(id=1,name="Private",progressType=ProgressType.TIMER,scheduleType=ScheduleType.DAILY,targetDurationMs=1_800_000,createdEpochDay=today.toEpochDay())
        val logs=listOf(HabitLog(id=1,habitId=1,date=today,durationMs=600_000),HabitLog(id=2,habitId=1,date=today,durationMs=900_000))
        val stats=LifeCalculations.habits(DateSpan(today.minusDays(6),today),listOf(h),logs,emptyList())
        assertEquals(1,stats.due);assertEquals(0.5,stats.equivalent,0.0);assertEquals(50.0,stats.percent!!,0.0)
        val archived=HabitProgressRules.version(h,today).copy(archived=true)
        assertEquals(0,LifeCalculations.habits(DateSpan(today,today),listOf(h),logs,listOf(archived)).due)
    }
    @Test fun tasksOnTimeUseDeadlineCohortAndDontInventOldCompletionDates() {
        val zone=ZoneId.of("America/Bogota")
        val time=today.atTime(23,59).atZone(zone).toInstant().toEpochMilli()
        val tasks=listOf(Task(id=1,title="A",completed=true,plannedDate=today.toString(),completedAtEpoch=time),
            Task(id=2,title="B",completed=true,plannedDate=today.toString()),Task(id=3,title="C",plannedDate=today.minusDays(1).toString()),
            Task(id=4,title="D",completed=true,plannedDate=today.plusDays(1).toString(),completedAtEpoch=time))
        val stats=LifeCalculations.tasks(DateSpan(today.minusDays(6),today),tasks,today,zone)
        assertEquals(3,stats.due);assertEquals(1,stats.onTime);assertEquals(1,stats.unknownCompletion);assertEquals(2,stats.completed);assertEquals(1,stats.overdue)
    }
    @Test fun parentPercentagesNormalizeDifferentUnitsAndDoNotAutoCompleteState() {
        assertEquals(50.0,GoalRules.childrenProgress(listOf(15.0 to 30.0,2.0 to 4.0)),0.0)
        val parent=LifeGoal(id="p",title="Year",period=LifePeriod.YEAR,start=today.withDayOfYear(1),end=today.withDayOfYear(365))
        val child=LifeGoal(id="c",title="Week",start=today,end=today,parentId="p")
        GoalRules.validate(child,listOf(parent,child))
        assertEquals("ACTIVE",parent.status)
        assertThrows(IllegalArgumentException::class.java) {GoalRules.validate(child.copy(parentId="c"),listOf(child))}
    }
    @Test fun zeroBaselinesNeverProduceInfinityOrNaN() {
        assertTrue(LifeCalculations.variation(1.0,0.0).contains("Sin base"))
        assertTrue(LifeCalculations.variation(0.0,0.0).contains("Sin cambio"))
        assertFalse(LifeCalculations.variation(0.0,2.0).contains("NaN"))
    }
}
