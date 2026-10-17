package com.trackermaster.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CommitmentMigration {
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN principal INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_debts (id TEXT NOT NULL PRIMARY KEY, person TEXT NOT NULL, kind TEXT NOT NULL, principalMinor INTEGER NOT NULL, currency TEXT NOT NULL, spaceId INTEGER NOT NULL, dueEpochDay INTEGER, note TEXT NOT NULL, archived INTEGER NOT NULL, version INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_debts_spaceId ON money_debts(spaceId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_debt_payments (id TEXT NOT NULL PRIMARY KEY, debtId TEXT NOT NULL, movementId INTEGER NOT NULL, event TEXT NOT NULL, createdMovement INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_debt_payments_debtId ON money_debt_payments(debtId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_money_debt_payments_movementId ON money_debt_payments(movementId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_savings_goals (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, targetMinor INTEGER NOT NULL, currency TEXT NOT NULL, pocketId INTEGER NOT NULL, dueEpochDay INTEGER, archived INTEGER NOT NULL, version INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_money_savings_goals_pocketId ON money_savings_goals(pocketId)")
        }
    }
}
