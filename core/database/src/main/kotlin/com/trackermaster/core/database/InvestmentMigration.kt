package com.trackermaster.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object InvestmentMigration {
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN investmentId TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN investmentCapital INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_investments (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, currency TEXT NOT NULL, spaceId INTEGER NOT NULL, openedEpochDay INTEGER NOT NULL, initialCostMinor INTEGER NOT NULL, initialValueMinor INTEGER NOT NULL, note TEXT NOT NULL, archived INTEGER NOT NULL, version INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_investments_spaceId ON money_investments(spaceId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_investment_events (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requestId TEXT NOT NULL, investmentId TEXT NOT NULL, kind TEXT NOT NULL, amountMinor INTEGER NOT NULL, costMinor INTEGER NOT NULL, dateEpochDay INTEGER NOT NULL, movementId INTEGER, createdMovement INTEGER NOT NULL, note TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_investment_events_investmentId ON money_investment_events(investmentId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_money_investment_events_requestId ON money_investment_events(requestId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_money_investment_events_movementId ON money_investment_events(movementId)")
        }
    }
}
