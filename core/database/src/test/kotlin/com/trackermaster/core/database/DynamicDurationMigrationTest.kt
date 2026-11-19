package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class DynamicDurationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TrackermasterDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    @Test fun preservesExistingHabitsAndTasks() {
        helper.createDatabase("dynamic-fifteen", 15).apply {
            execSQL("INSERT INTO habits(id,name,progressType,scheduleType,scheduleDaysBitmask,targetCount,targetDurationMs,checklistItemsJson,colorArgb,archived,description,sortOrder,createdEpochDay,creationDateKnown) VALUES(1,'Habit','TIMER','DAILY',0,1,1800000,'[]',0,0,'',0,1,1)")
            execSQL("INSERT INTO tasks(id,title,completed,createdAtEpoch,subtasksJson) VALUES(1,'Task',0,1,'')")
            close()
        }
        helper.runMigrationsAndValidate("dynamic-fifteen", 16, true, DynamicDurationMigration.MIGRATION_15_16).use { db ->
            db.query("SELECT name,targetDurationMs,dynamicDurationEnabled FROM habits WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("Habit", it.getString(0)); assertEquals(1800000L, it.getLong(1)); assertEquals(0,it.getInt(2)) }
            db.query("SELECT title,archived FROM tasks WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("Task",it.getString(0)); assertEquals(0,it.getInt(1)) }
        }
    }
}
