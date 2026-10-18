package com.trackermaster.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Capture schedule changes from every local writer, including existing sync commands. */
object ProgressHistorySchema {
    val callback=object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) { install(db) }
    }
    fun install(db: SupportSQLiteDatabase) {
        val day="CAST(julianday(date('now','localtime'))-julianday('1970-01-01') AS INTEGER)"
        val checklist="CASE WHEN checklistItemsJson='' OR checklistItemsJson='[]' THEN 0 ELSE LENGTH(checklistItemsJson)-LENGTH(REPLACE(checklistItemsJson,char(30),''))+1 END"
        val snapshot="INSERT OR REPLACE INTO habit_schedule_history SELECT id,$day,progressType,scheduleType,scheduleDaysBitmask,targetCount,targetDurationMs,$checklist,archived FROM habits WHERE id=NEW.id;"
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS progress_habit_created AFTER INSERT ON habits BEGIN
            UPDATE habits SET createdEpochDay=$day WHERE id=NEW.id AND createdEpochDay=0 AND creationDateKnown=0;
            $snapshot END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS progress_habit_schedule AFTER UPDATE OF progressType,scheduleType,
            scheduleDaysBitmask,targetCount,targetDurationMs,checklistItemsJson,archived ON habits
            WHEN NEW.progressType!=OLD.progressType OR NEW.scheduleType!=OLD.scheduleType OR
            NEW.scheduleDaysBitmask!=OLD.scheduleDaysBitmask OR NEW.targetCount!=OLD.targetCount OR
            NEW.targetDurationMs!=OLD.targetDurationMs OR NEW.checklistItemsJson!=OLD.checklistItemsJson OR NEW.archived!=OLD.archived
            BEGIN $snapshot END""")
        // Do not invent targets for historical imported records that lack a snapshot.
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS progress_log_target AFTER INSERT ON habit_logs
            WHEN date(NEW.dateEpoch/1000,'unixepoch','localtime')=date('now','localtime')
            BEGIN UPDATE habit_logs SET
                progressTypeAtLog=COALESCE(progressTypeAtLog,(SELECT progressType FROM habits WHERE id=NEW.habitId)),
                targetCountAtLog=COALESCE(targetCountAtLog,(SELECT targetCount FROM habits WHERE id=NEW.habitId)),
                checklistSizeAtLog=COALESCE(checklistSizeAtLog,(SELECT $checklist FROM habits WHERE id=NEW.habitId)),
                targetDurationMsAtLog=COALESCE(targetDurationMsAtLog,(SELECT CASE WHEN progressType IN ('TIMER','STOPWATCH') THEN targetDurationMs ELSE NULL END FROM habits WHERE id=NEW.habitId))
            WHERE id=NEW.id; END""")
    }
}
