package com.trackermaster.core.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.CloudSyncSchema
import com.trackermaster.core.database.TrackermasterDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.data.mapper.toEpochMilli
import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.HabitLog
import com.trackermaster.core.domain.model.ProgressType
import com.trackermaster.core.domain.model.ScheduleType
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class AssistantCommandTest {
    private lateinit var room: TrackermasterDatabase
    private lateinit var store: CloudSyncStore
    private lateinit var record: PendingRecord
    @Before fun setup() {
        room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrackermasterDatabase::class.java)
            .addCallback(CloudSyncSchema.callback).allowMainThreadQueries().build()
        store = CloudSyncStore(room)
        room.openHelper.writableDatabase.execSQL("INSERT INTO habits(id,name,progressType,scheduleType,scheduleDaysBitmask,targetCount,targetDurationMs,checklistItemsJson,colorArgb,archived,description,sortOrder) VALUES (1,'Timed','TIMER','DAILY',0,1,2400000,'[]',0,0,'',0)")
        record = store.pending().single(); store.acknowledge(listOf(record))
    }
    @After fun close() { room.close() }
    private fun apply(target: Long = 2100000) = store.applyTarget("command", CloudSyncStore.documentId("habits", record.key), record.key, 1,
        record.sequence, 2400000, target, LocalDate.now(ZoneId.of("America/Bogota")).toString())
    @Test fun retriesApplyExactlyOnce() {
        assertEquals("applied", apply()); val count = store.pendingCount()
        assertEquals("applied", apply()); assertEquals(count, store.pendingCount())
        room.openHelper.writableDatabase.query("SELECT targetDurationMs FROM habits WHERE id=1").use { it.moveToFirst(); assertEquals(2100000L, it.getLong(0)) }
    }
    @Test fun localEditsCauseConflict() {
        room.openHelper.writableDatabase.execSQL("UPDATE habits SET name='Edited' WHERE id=1")
        assertEquals("conflict", apply())
    }
    @Test fun rejectsGoalsAboveTheCap() { assertThrows(IllegalArgumentException::class.java) { apply(3600001) } }
    @Test fun snapshotsPreserveOriginalGoalAndDoNotInventPastGoals() = runTest {
        val repository = HabitRepository(room.habitDao(), room.habitLogDao(), room.achievementDao(), room, com.trackermaster.core.data.progress.ProgressClock(ApplicationProvider.getApplicationContext()))
        val habit = Habit(id = 1, name = "Timed", progressType = ProgressType.TIMER, scheduleType = ScheduleType.DAILY, targetDurationMs = 2400000)
        val today = LocalDate.now(ZoneId.of("America/Bogota"))
        repository.logHabit(habit, HabitLog(habitId = 1, date = today, durationMs = 60000))
        repository.saveHabit(habit.copy(targetDurationMs = 2100000))
        repository.logHabit(habit.copy(targetDurationMs = 2100000), HabitLog(habitId = 1, date = today, durationMs = 120000))
        repository.logHabit(habit.copy(targetDurationMs = 2100000), HabitLog(habitId = 1, date = today.minusDays(1), durationMs = 60000))
        assertEquals(2400000L, room.habitLogDao().getForDate(1, today.toEpochMilli())!!.targetDurationMsAtLog)
        assertNull(room.habitLogDao().getForDate(1, today.minusDays(1).toEpochMilli())!!.targetDurationMsAtLog)
    }
    @Test fun delayedApprovalWaitsUntilNextFullDay() {
        assertEquals("waiting", store.applyTarget("late", CloudSyncStore.documentId("habits", record.key), record.key, 1,
            record.sequence, 2400000, 2100000, LocalDate.now(ZoneId.of("America/Bogota")).minusDays(1).toString()))
        assertEquals(0, store.pendingCount())
    }
}
