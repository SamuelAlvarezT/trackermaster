package com.trackermaster.core.domain

import com.trackermaster.core.domain.habits.*
import com.trackermaster.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HabitProgressRulesTest {
    private val day=LocalDate.of(2026,10,18)
    private fun habit(id: Long=1,type: ProgressType=ProgressType.TIMER,from: LocalDate=day.minusDays(1)) =
        Habit(id=id,name="Meditate",progressType=type,scheduleType=ScheduleType.DAILY,
            targetDurationMs=1_800_000,targetCount=20,createdEpochDay=from.toEpochDay())
    private fun pending(h: Habit,logs: List<HabitLog> = emptyList(),history: List<HabitScheduleVersion> = emptyList()) =
        HabitProgressRules.pending(listOf(h),logs,history,day)
    @Test fun halfTodayAndMissedYesterdayEqualsOneAndHalf() {
        val result=pending(habit(),listOf(HabitLog(habitId=1,date=day,durationMs=900_000))).single()
        assertEquals(1.5,result.equivalentDays,1e-9);assertEquals(0.5,result.todayDeficit,1e-9)
        assertTrue(result.includesProvisionalToday)
    }
    @Test fun tenOfThirtyMinutesLeavesTwoThirds() {
        val h=habit(from=day)
        assertEquals(2.0/3,pending(h,listOf(HabitLog(habitId=1,date=day,durationMs=600_000))).single().equivalentDays,1e-9)
    }
    @Test fun fullTodayResetsAllPreviousDeficit() {
        assertTrue(pending(habit(from=day.minusDays(100)),listOf(HabitLog(habitId=1,date=day,durationMs=1_800_000))).isEmpty())
    }
    @Test fun onlyDaysAfterLastFullCompletionCount() {
        val h=habit(from=day.minusDays(10))
        assertEquals(2.0,pending(h,listOf(HabitLog(habitId=1,date=day.minusDays(2),completed=true))).single().equivalentDays,0.0)
    }
    @Test fun activeNeverCompletedCountsFromCreationInclusive() {
        assertEquals(5.0,pending(habit(from=day.minusDays(4))).single().equivalentDays,0.0)
    }
    @Test fun archivedHabitsAreExcluded() { assertTrue(pending(habit().copy(archived=true)).isEmpty()) }
    @Test fun futureCreationIsExcluded() { assertTrue(pending(habit(from=day.plusDays(1))).isEmpty()) }
    @Test fun specificWeekdaysExcludeUnscheduledDaysAndProvisionalToday() {
        val h=habit(from=day.minusDays(6)).copy(scheduleType=ScheduleType.SPECIFIC_DAYS,scheduleDaysBitmask=1 or 4)
        val result=pending(h).single()
        assertEquals(2.0,result.equivalentDays,0.0);assertFalse(result.includesProvisionalToday)
    }
    @Test fun countHabitUsesActualQuantity() {
        val h=habit(type=ProgressType.COUNTER,from=day)
        assertEquals(0.5,pending(h,listOf(HabitLog(habitId=1,date=day,value=10))).single().equivalentDays,0.0)
    }
    @Test fun checklistUsesItemsAndClampsAtOne() {
        val h=habit(type=ProgressType.CHECKLIST,from=day).copy(checklistItems=listOf("a","b"))
        assertEquals(0.5,pending(h,listOf(HabitLog(habitId=1,date=day,checklistState=mapOf("a" to true)))).single().equivalentDays,0.0)
        assertTrue(pending(h,listOf(HabitLog(habitId=1,date=day,checklistState=mapOf("a" to true,"b" to true,"c" to true)))).isEmpty())
    }
    @Test fun simpleHabitRequiresCompletionAndDoesNotInferFromTime() {
        assertEquals(1.0,pending(habit(type=ProgressType.CHECKBOX,from=day),listOf(HabitLog(habitId=1,date=day,durationMs=60_000))).single().equivalentDays,0.0)
    }
    @Test fun explicitCompletionOverridesShorterMeasurement() {
        assertTrue(pending(habit(from=day),listOf(HabitLog(habitId=1,date=day,durationMs=1,completed=true))).isEmpty())
    }
    @Test fun negativeAndOverTargetValuesAreClamped() {
        val h=habit(type=ProgressType.COUNTER,from=day)
        assertEquals(1.0,pending(h,listOf(HabitLog(habitId=1,date=day,value=-1))).single().equivalentDays,0.0)
        assertTrue(pending(h,listOf(HabitLog(habitId=1,date=day,value=100))).isEmpty())
    }
    @Test fun goalHistoryKeepsYesterdayAtItsOriginalTarget() {
        val original=habit();val changed=original.copy(targetDurationMs=3_600_000)
        val history=listOf(HabitProgressRules.version(original,day.minusDays(1)),HabitProgressRules.version(changed,day))
        val logs=listOf(HabitLog(habitId=1,date=day.minusDays(1),durationMs=900_000))
        assertEquals(1.5,pending(changed,logs,history).single().equivalentDays,1e-9)
    }
    @Test fun loggedTargetWinsOverSameDayGoalEdit() {
        val h=habit(from=day).copy(targetDurationMs=3_600_000)
        assertEquals(0.5,pending(h,listOf(HabitLog(habitId=1,date=day,durationMs=900_000,targetDurationMsAtLog=1_800_000))).single().equivalentDays,0.0)
    }
    @Test fun archivedPeriodsAreNotAddedAfterRestore() {
        val h=habit(from=day.minusDays(3))
        val history=listOf(HabitProgressRules.version(h,day.minusDays(3)),HabitProgressRules.version(h.copy(archived=true),day.minusDays(2)),HabitProgressRules.version(h,day))
        assertEquals(2.0,pending(h,history=history).single().equivalentDays,0.0)
    }
    @Test fun tiesAreStableAndOnlyThreeAreReturned() {
        val rows=HabitProgressRules.pending(listOf(habit(4),habit(2),habit(3),habit(1)),emptyList(),emptyList(),day)
        assertEquals(listOf(1L,2L,3L),rows.map { it.habit.id })
    }
    @Test fun futureLogsDoNotResetCurrentDeficit() {
        assertEquals(2.0,pending(habit(),listOf(HabitLog(habitId=1,date=day.plusDays(1),completed=true))).single().equivalentDays,0.0)
    }
    @Test fun partialTimeIsNotFullCompletionForHomeOrStreaks() {
        assertFalse(HabitScheduleLogic.isLogComplete(HabitLog(habitId=1,date=day,durationMs=600_000)))
        assertFalse(HabitScheduleLogic.isLogComplete(HabitLog(habitId=1,date=day,durationMs=600_000,targetDurationMsAtLog=1_800_000)))
        assertTrue(HabitScheduleLogic.isLogComplete(HabitLog(habitId=1,date=day,durationMs=1_800_000,targetDurationMsAtLog=1_800_000)))
    }
}
