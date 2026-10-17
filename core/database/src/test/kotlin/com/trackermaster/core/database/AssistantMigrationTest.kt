package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class AssistantMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        TrackermasterDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    @Test fun preservesOldTasksWithoutInventingDates() {
        helper.createDatabase("assistant-nine", 9).apply {
            execSQL("INSERT INTO tasks(id,title,completed,createdAtEpoch,subtasksJson) VALUES (1,'Existing',1,123,'')"); close()
        }
        helper.runMigrationsAndValidate("assistant-nine", 10, true, AssistantMigration.MIGRATION_9_10).use { db ->
            db.query("SELECT title,completed,plannedDate,completedAtEpoch FROM tasks WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals("Existing", it.getString(0)); assertEquals(1, it.getInt(1)); assertTrue(it.isNull(2)); assertTrue(it.isNull(3))
            }
        }
    }
}
