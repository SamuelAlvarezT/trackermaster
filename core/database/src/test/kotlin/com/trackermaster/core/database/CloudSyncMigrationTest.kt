package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class CloudSyncMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        TrackermasterDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    private fun count(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun insertFixture(db: SupportSQLiteDatabase, table: String) {
        val columns = mutableListOf<String>()
        val values = mutableListOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1); val type = cursor.getString(2)
                columns.add("`$name`")
                values.add(if (type == "TEXT") "'fixture'" else "1")
            }
        }
        db.execSQL("INSERT INTO `$table` (${columns.joinToString()}) VALUES (${values.joinToString()})")
    }

    @Test fun migrationPreservesEveryModuleAndQueuesInitialSnapshot() {
        helper.createDatabase("cloud-eight", 8).apply {
            execSQL("PRAGMA foreign_keys=OFF")
            CloudSyncSchema.tables.keys.forEach { insertFixture(this, it) }
            close()
        }
        val db = helper.runMigrationsAndValidate("cloud-eight", 9, true, CloudSyncSchema.MIGRATION_8_9)
        CloudSyncSchema.tables.keys.forEach { table ->
            assertEquals("local $table", 1L, count(db, "SELECT COUNT(*) FROM `$table`"))
            assertEquals("snapshot $table", 1L, count(db, "SELECT COUNT(*) FROM cloud_sync_outbox WHERE tableName='$table'"))
        }
        assertEquals(1L, count(db, "SELECT COUNT(*) FROM cloud_sync_state")); db.close()
    }

    @Test fun migrationFromSevenRetainsTheCurrentPlannedSpaceColumn() {
        helper.createDatabase("cloud-seven", 7).close()
        val db = helper.runMigrationsAndValidate("cloud-seven", 9, true,
            PlanningMigration.MIGRATION_7_8, CloudSyncSchema.MIGRATION_8_9)
        db.query("SELECT plannedSpaceId FROM money_plans").close()
        assertEquals(1L, count(db, "SELECT COUNT(*) FROM cloud_sync_state")); db.close()
    }

    @Test fun triggersCaptureAllWritersAndRollbackAtomically() {
        helper.createDatabase("cloud-triggers", 8).close()
        val db = helper.runMigrationsAndValidate("cloud-triggers", 9, true, CloudSyncSchema.MIGRATION_8_9)
        db.execSQL("PRAGMA foreign_keys=OFF")
        CloudSyncSchema.tables.forEach { (table, keys) ->
            insertFixture(db, table)
            db.execSQL("UPDATE `$table` SET `${keys.first()}`=`${keys.first()}`")
            db.execSQL("DELETE FROM `$table`")
            assertEquals(table, 3L, count(db, "SELECT COUNT(*) FROM cloud_sync_outbox WHERE tableName='$table'"))
        }
        val before = count(db, "SELECT COUNT(*) FROM cloud_sync_outbox")
        db.beginTransaction()
        try { insertFixture(db, "tasks") } finally { db.endTransaction() }
        assertEquals(0L, count(db, "SELECT COUNT(*) FROM tasks"))
        assertEquals(before, count(db, "SELECT COUNT(*) FROM cloud_sync_outbox")); db.close()
    }

    @Test fun changingPrimaryKeyAlsoQueuesDeletionOfOldKey() {
        helper.createDatabase("cloud-keys", 8).close()
        val db = helper.runMigrationsAndValidate("cloud-keys", 9, true, CloudSyncSchema.MIGRATION_8_9)
        insertFixture(db, "tasks")
        db.execSQL("DELETE FROM cloud_sync_outbox")
        db.execSQL("UPDATE tasks SET id=2 WHERE id=1")
        assertEquals(2L, count(db, "SELECT COUNT(*) FROM cloud_sync_outbox")); db.close()
    }
}
