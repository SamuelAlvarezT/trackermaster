package com.trackermaster.core.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName="life_goals")
data class LifeGoalEntity(@PrimaryKey val id: String,val payload: String)
@Entity(tableName="life_goal_versions",indices=[Index("goalId")])
data class LifeGoalVersionEntity(@PrimaryKey(autoGenerate=true) val id: Long=0,val goalId: String,val savedAt: Long,val payload: String)
@Entity(tableName="life_reviews")
data class LifeReviewEntity(@PrimaryKey val id: String,val period: String,val startDay: Long,val endDay: Long,
    val provisional: Boolean,val createdAt: Long,val fingerprint: String,val calculated: String,val analysis: String)
@Entity(tableName="journal_versions",indices=[Index("entryId")])
data class JournalVersionEntity(@PrimaryKey(autoGenerate=true) val id: Long=0,val entryId: Long,val savedAt: Long,val payload: String)
@Entity(tableName="journal_contributions")
data class JournalContributionEntity(@PrimaryKey val entryId: Long,val habitId: Long,val day: Long,val count: Int,val durationMs: Long)

object LifeMigration {
    val MIGRATION_13_14=object: Migration(13,14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN kind TEXT NOT NULL DEFAULT 'PERSONAL'")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN contentEpochDay INTEGER")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN emotions TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN lucid INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN habitId INTEGER")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN contribution INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE journal_entries ADD COLUMN deletedAt INTEGER")
            // Legacy entries have no type marker. Keep PERSONAL and derive the content date at read time
            // from the original timestamp in the same zone used by the existing app; never invent dates.
            db.execSQL("CREATE TABLE IF NOT EXISTS life_goals (id TEXT NOT NULL PRIMARY KEY,payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS life_goal_versions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,goalId TEXT NOT NULL,savedAt INTEGER NOT NULL,payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_life_goal_versions_goalId ON life_goal_versions(goalId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS life_reviews (id TEXT NOT NULL PRIMARY KEY,period TEXT NOT NULL,startDay INTEGER NOT NULL,endDay INTEGER NOT NULL,provisional INTEGER NOT NULL,createdAt INTEGER NOT NULL,fingerprint TEXT NOT NULL,calculated TEXT NOT NULL,analysis TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS journal_versions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,entryId INTEGER NOT NULL,savedAt INTEGER NOT NULL,payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_versions_entryId ON journal_versions(entryId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS journal_contributions (entryId INTEGER NOT NULL PRIMARY KEY,habitId INTEGER NOT NULL,day INTEGER NOT NULL,count INTEGER NOT NULL,durationMs INTEGER NOT NULL)")
        }
    }
}
