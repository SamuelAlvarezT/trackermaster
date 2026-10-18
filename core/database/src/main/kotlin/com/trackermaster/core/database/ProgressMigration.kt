package com.trackermaster.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ProgressMigration {
    val MIGRATION_12_13 = object : Migration(12,13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN createdEpochDay INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN creationDateKnown INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habit_logs ADD COLUMN progressTypeAtLog TEXT")
            db.execSQL("ALTER TABLE habit_logs ADD COLUMN targetCountAtLog INTEGER")
            db.execSQL("ALTER TABLE habit_logs ADD COLUMN checklistSizeAtLog INTEGER")
            db.execSQL("ALTER TABLE focus_sessions ADD COLUMN habitId INTEGER")
            db.execSQL("ALTER TABLE focus_sessions ADD COLUMN habitName TEXT")
            db.execSQL("ALTER TABLE focus_sessions ADD COLUMN actualDurationMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE focus_sessions SET actualDurationMs=MAX(0,actualSeconds)*1000")
            db.execSQL("""CREATE TABLE IF NOT EXISTS habit_schedule_history (habitId INTEGER NOT NULL,
                effectiveFromEpochDay INTEGER NOT NULL,progressType TEXT NOT NULL,scheduleType TEXT NOT NULL,
                scheduleDaysBitmask INTEGER NOT NULL,targetCount INTEGER NOT NULL,targetDurationMs INTEGER NOT NULL,
                checklistSize INTEGER NOT NULL,archived INTEGER NOT NULL,PRIMARY KEY(habitId,effectiveFromEpochDay))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_schedule_history_habitId ON habit_schedule_history(habitId)")
            val today=LocalDate.now().toEpochDay()
            db.query("SELECT h.id,h.progressType,h.scheduleType,h.scheduleDaysBitmask,h.targetCount,h.targetDurationMs,h.checklistItemsJson,h.archived,(SELECT MIN(dateEpoch) FROM habit_logs WHERE habitId=h.id) FROM habits h").use { cursor ->
                while (cursor.moveToNext()) {
                    // Legacy rows do not contain a creation date: preserve a documented tracking baseline.
                    val from=if (cursor.isNull(8)) today else Instant.ofEpochMilli(cursor.getLong(8)).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay().coerceAtMost(today)
                    val raw=cursor.getString(6)
                    val count=if (raw.isBlank() || raw=="[]") 0 else raw.split('\u001E').size
                    db.execSQL("UPDATE habits SET createdEpochDay=? WHERE id=?",arrayOf(from,cursor.getLong(0)))
                    db.execSQL("INSERT INTO habit_schedule_history VALUES(?,?,?,?,?,?,?,?,?)",arrayOf(cursor.getLong(0),from,cursor.getString(1),cursor.getString(2),cursor.getInt(3),cursor.getInt(4),cursor.getLong(5),count,cursor.getInt(7)))
                }
            }
            db.execSQL("""CREATE TABLE IF NOT EXISTS focus_runtime(id INTEGER NOT NULL PRIMARY KEY,
                sessionId INTEGER NOT NULL,category TEXT NOT NULL,habitId INTEGER,plannedMs INTEGER NOT NULL,
                effectiveMs INTEGER NOT NULL,status TEXT NOT NULL,lastElapsedMs INTEGER NOT NULL,
                lastWallMs INTEGER NOT NULL,bootCount INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS focus_habit_contributions(sessionId INTEGER NOT NULL,
                dateEpochDay INTEGER NOT NULL,habitId INTEGER,durationMs INTEGER NOT NULL,
                PRIMARY KEY(sessionId,dateEpochDay))""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_habit_contributions_habitId ON focus_habit_contributions(habitId)")
            ProgressHistorySchema.install(db)
        }
    }
}
