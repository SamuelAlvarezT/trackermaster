package com.trackermaster.core.data.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.focus.FocusRunStatus
import com.trackermaster.core.domain.model.FocusCategoryType
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class FocusProgressRepositoryTest {
    private lateinit var db: TrackermasterDatabase
    private lateinit var clock: FakeClock
    private lateinit var repo: FocusProgressRepository
    class FakeClock(context: Context): ProgressClock(context) {
        var wall=LocalDate.of(2026,10,18).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var elapsed=1000L;var boot=1
        override fun wallMs()=wall
        override fun elapsedMs()=elapsed
        override fun bootCount()=boot
        fun advance(ms: Long) { wall+=ms;elapsed+=ms }
    }
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,TrackermasterDatabase::class.java).allowMainThreadQueries().build()
        clock=FakeClock(context);repo=FocusProgressRepository(db,clock)
    }
    @After fun close() { db.close() }
    private suspend fun habit(type: String="TIMER")=db.habitDao().insert(HabitEntity(name="Meditate",progressType=type,
        scheduleType="DAILY",targetDurationMs=30*60_000,colorArgb=0,createdEpochDay=clock.today().toEpochDay(),creationDateKnown=true))
    private suspend fun log(id: Long,day: LocalDate=clock.today())=db.habitLogDao().getForDate(id,day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    @Test fun tenPlusTwentyMinutesCompletesThirtyMinuteHabitExactlyOnce()=runTest {
        val h=habit();repo.start(FocusCategoryType.WORK,10,h);clock.advance(600_000);repo.checkpoint()
        assertEquals(600_000L,log(h)!!.durationMs);assertFalse(log(h)!!.completed)
        repo.start(FocusCategoryType.WORK,20,h);clock.advance(1_200_000);repo.checkpoint();repeat(3) { repo.checkpoint() }
        assertEquals(1_800_000L,log(h)!!.durationMs);assertTrue(log(h)!!.completed)
        assertEquals(2,db.openHelper.readableDatabase.query("SELECT id FROM focus_sessions").use { it.count })
    }
    @Test fun pauseExcludesTimeAndNewRepositoryRecoversWithoutDuplication()=runTest {
        val h=habit();repo.start(FocusCategoryType.WORK,30,h);clock.advance(120_005);repo.pause()
        clock.advance(900_000);repo=FocusProgressRepository(db,clock);repo.checkpoint()
        assertEquals(120_005L,log(h)!!.durationMs)
        repo.resume();clock.advance(65_123);repo.stop();repo.checkpoint()
        assertEquals(185_128L,log(h)!!.durationMs)
    }
    @Test fun startingAndStoppingWithoutTimeCreatesNoHabitProgress()=runTest {
        val h=habit();repo.start(FocusCategoryType.WORK,30,h);repo.stop();assertNull(log(h))
    }
    @Test fun sessionWithoutHabitRecordsEffectiveTimeOnly()=runTest {
        val id=repo.start(FocusCategoryType.WORK,10,null);clock.advance(15_200);repo.pause()
        assertEquals(15_200L,db.progressDao().session(id)!!.actualDurationMs)
        assertEquals(15L,repo.observeTodaySeconds().first())
        assertTrue(db.progressDao().observeAllLogs().first().isEmpty())
    }
    @Test fun midnightSplitsProgressByLocalDate()=runTest {
        val h=habit();val first=clock.today()
        clock.wall=first.atTime(23,55).atZone(clock.zone()).toInstant().toEpochMilli()
        repo.start(FocusCategoryType.WORK,10,h);clock.advance(600_000);repo.checkpoint()
        assertEquals(300_000L,log(h,first)!!.durationMs);assertEquals(300_000L,log(h,first.plusDays(1))!!.durationMs)
        assertEquals(300L,repo.observeTodaySeconds().first())
    }
    @Test fun rebootPausesAndDoesNotCountDeviceDowntime()=runTest {
        val h=habit();repo.start(FocusCategoryType.WORK,30,h);clock.advance(60_000);repo.checkpoint()
        clock.boot++;clock.elapsed=500;clock.wall+=7_200_000
        assertEquals(FocusRunStatus.PAUSED,repo.checkpoint().status);assertEquals(60_000L,log(h)!!.durationMs)
    }
    @Test fun manualCorrectionIsKeptAndOnlyNewTimeIsAdded()=runTest {
        val h=habit();repo.start(FocusCategoryType.WORK,30,h);clock.advance(600_000);repo.pause()
        db.habitLogDao().update(log(h)!!.copy(durationMs=120_000))
        repo.resume();clock.advance(60_000);repo.pause();assertEquals(180_000L,log(h)!!.durationMs)
    }
    @Test fun loggedGoalRemainsThirtyMinutesAfterGoalChange()=runTest {
        val h=habit();repo.start(FocusCategoryType.WORK,10,h);clock.advance(600_000);repo.checkpoint()
        db.habitDao().update(db.habitDao().getById(h)!!.copy(targetDurationMs=600_000))
        repo.start(FocusCategoryType.WORK,10,h);clock.advance(600_000);repo.checkpoint()
        assertEquals(1_800_000L,log(h)!!.targetDurationMsAtLog);assertFalse(log(h)!!.completed)
    }
    @Test fun quantitiesAreNotFabricatedFromTimerMinutes()=runTest {
        val h=habit("COUNTER");repo.start(FocusCategoryType.WORK,1,h);clock.advance(60_000);repo.checkpoint()
        assertEquals(0,log(h)!!.value);assertFalse(log(h)!!.completed)
    }
    @Test fun elapsedAfterPlannedEndIsExcludedAndFinishedSessionIsImmutable()=runTest {
        val h=habit();val id=repo.start(FocusCategoryType.WORK,10,h);clock.advance(3_600_000);repo.checkpoint()
        clock.advance(600_000);repo.pause();repo.stop()
        assertEquals(600_000L,log(h)!!.durationMs);assertEquals(600_000L,db.progressDao().session(id)!!.actualDurationMs)
    }
    @Test fun duplicateStartCannotCreateAnotherActiveSession()=runTest {
        repo.start(FocusCategoryType.WORK,10,null)
        assertTrue(runCatching { repo.start(FocusCategoryType.WORK,10,null) }.isFailure)
    }
    @Test fun effectiveTimeReplacesAMissedDayFlagWithPartialProgress()=runTest {
        val h=habit()
        db.habitLogDao().insert(HabitLogEntity(habitId=h,dateEpoch=clock.today().atStartOfDay(clock.zone()).toInstant().toEpochMilli(),value=-1))
        repo.start(FocusCategoryType.WORK,30,h);clock.advance(60_000);repo.pause()
        assertEquals(0,log(h)!!.value);assertEquals(60_000L,log(h)!!.durationMs);assertFalse(log(h)!!.completed)
    }
}
