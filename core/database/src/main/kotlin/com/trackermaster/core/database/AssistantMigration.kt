package com.trackermaster.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "assistant_commands")
data class AssistantCommandReceipt(@PrimaryKey val id: String, val state: String, val effectiveDay: String)

@Entity(tableName = "cloud_record_versions")
data class CloudRecordVersion(@PrimaryKey val recordId: String, val revision: Long)

object AssistantMigration {
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habit_logs ADD COLUMN targetDurationMsAtLog INTEGER")
            db.execSQL("ALTER TABLE habit_logs ADD COLUMN completionSource TEXT")
            db.execSQL("ALTER TABLE tasks ADD COLUMN plannedDate TEXT")
            db.execSQL("ALTER TABLE tasks ADD COLUMN completedAtEpoch INTEGER")
            db.execSQL("ALTER TABLE tasks ADD COLUMN cancelledAtEpoch INTEGER")
            db.execSQL("ALTER TABLE focus_sessions ADD COLUMN startedAtEpoch INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS assistant_commands (id TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, effectiveDay TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS cloud_record_versions (recordId TEXT NOT NULL PRIMARY KEY, revision INTEGER NOT NULL)")
            db.execSQL("INSERT INTO cloud_sync_outbox(tableName,recordKey) SELECT 'journal_entries',${CloudSyncSchema.keyExpression("journal_entries")} FROM journal_entries")
            db.execSQL("INSERT INTO cloud_sync_outbox(tableName,recordKey) SELECT 'habits',${CloudSyncSchema.keyExpression("habits")} FROM habits")
            // Do not invent historical goals or completion timestamps during migration.
        }
    }
}
