package com.trackermaster.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PlanningMigration {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE budgets ADD COLUMN startDateEpochDay INTEGER")
            db.execSQL("ALTER TABLE budgets ADD COLUMN endDateEpochDay INTEGER")
            db.execSQL("""CREATE TABLE IF NOT EXISTS money_plans (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, direction TEXT NOT NULL,
                amountMinor INTEGER, currencyCode TEXT NOT NULL, accountId INTEGER, pocketId INTEGER, categoryId INTEGER,
                anchorEpochDay INTEGER NOT NULL, nextDueEpochDay INTEGER NOT NULL, frequency TEXT NOT NULL,
                reminderDays INTEGER NOT NULL, remindOnEpochDay INTEGER, note TEXT NOT NULL, active INTEGER NOT NULL,
                archived INTEGER NOT NULL, version INTEGER NOT NULL, plannedSpaceId INTEGER)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_plans_nextDueEpochDay ON money_plans(nextDueEpochDay)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_plans_accountId ON money_plans(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_plans_categoryId ON money_plans(categoryId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS money_plan_occurrences (planId TEXT NOT NULL, dueEpochDay INTEGER NOT NULL,
                status TEXT NOT NULL, movementId INTEGER, fingerprint TEXT NOT NULL, PRIMARY KEY(planId,dueEpochDay))""")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_money_plan_occurrences_movementId ON money_plan_occurrences(movementId)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS money_planning_alerts (eventKey TEXT NOT NULL PRIMARY KEY,
                leaseToken TEXT NOT NULL, claimedAtEpoch INTEGER NOT NULL, delivered INTEGER NOT NULL)""")
        }
    }
}
