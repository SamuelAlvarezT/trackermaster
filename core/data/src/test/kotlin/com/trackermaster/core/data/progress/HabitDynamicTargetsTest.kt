package com.trackermaster.core.data.progress

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.data.mapper.toDomain
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.HabitEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class HabitDynamicTargetsTest {
    private lateinit var db: TrackermasterDatabase
    private lateinit var targets: HabitDynamicTargets
    private val first = LocalDate.of(2026, 11, 16)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackermasterDatabase::class.java).allowMainThreadQueries().build()
        targets = HabitDynamicTargets(db)
    }
    @After fun close() = db.close()
    private fun epoch(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    @Test fun storesTargetsOnceAndUsesRecordedProgressForNextDay() = runTest {
        val id = db.habitDao().insert(HabitEntity(name="Dynamic",progressType="TIMER",scheduleType="DAILY",colorArgb=0,
            targetDurationMs=1_800_000, dynamicDurationEnabled=true, dynamicInitialMinutes=30,
            dynamicMinimumMinutes=1,dynamicMaximumMinutes=60,dynamicStartEpochDay=first.toEpochDay()))
        val habit = db.habitDao().getById(id)!!.toDomain()
        db.withTransaction { targets.ensureInTransaction(habit, first) }
        val log = db.habitLogDao().getForDate(id,epoch(first))!!
        db.habitLogDao().update(log.copy(durationMs=450_000))
        db.withTransaction { targets.ensureInTransaction(habit, first.plusDays(1)) }
        assertEquals(26L,db.habitDynamicGoalDao().forHabit(id).last().targetMinutes)
        db.habitLogDao().update(log.copy(durationMs=1_800_000))
        db.withTransaction { targets.ensureInTransaction(habit, first.plusDays(1)) }
        assertEquals(26L,db.habitDynamicGoalDao().forHabit(id).last().targetMinutes)
        assertEquals(1_560_000L, db.habitLogDao().getForDate(id,epoch(first.plusDays(1)))!!.targetDurationMsAtLog)
    }

    @Test fun scheduledDaysAdjustAndUnscheduledDaysDoNotCreateGoals() = runTest {
        val id = db.habitDao().insert(HabitEntity(name="Weekdays",progressType="TIMER",scheduleType="WEEKLY",colorArgb=0,
            scheduleDaysBitmask=1 or 4 or 16, targetDurationMs=1_800_000, dynamicDurationEnabled=true,
            dynamicInitialMinutes=30, dynamicMinimumMinutes=1,dynamicMaximumMinutes=60,dynamicStartEpochDay=first.toEpochDay()))
        val habit = db.habitDao().getById(id)!!.toDomain()
        db.withTransaction { targets.ensureInTransaction(habit, first.plusDays(1)) }
        assertEquals(listOf(30L),db.habitDynamicGoalDao().forHabit(id).map { it.targetMinutes })
        db.withTransaction { targets.ensureInTransaction(habit, first.plusDays(2)) }
        assertEquals(listOf(30L, 23L),db.habitDynamicGoalDao().forHabit(id).map { it.targetMinutes })
        val wednesday = db.habitLogDao().getForDate(id,epoch(first.plusDays(2)))!!
        db.habitLogDao().update(wednesday.copy(durationMs = 23 * 60_000L))
        db.withTransaction { targets.ensureInTransaction(habit, first.plusDays(4)) }
        assertEquals(listOf(30L,23L,25L),db.habitDynamicGoalDao().forHabit(id).map { it.targetMinutes })
        assertNull(db.habitLogDao().getForDate(id,epoch(first.plusDays(3))))
    }
}
