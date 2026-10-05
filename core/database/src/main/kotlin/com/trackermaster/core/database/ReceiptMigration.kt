package com.trackermaster.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ReceiptMigration {
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS money_receipts (
                id TEXT NOT NULL PRIMARY KEY, path TEXT NOT NULL, mimeType TEXT NOT NULL,
                sha256 TEXT NOT NULL, name TEXT NOT NULL, text TEXT NOT NULL,
                previewPath TEXT, stage TEXT NOT NULL, draftJson TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_receipts_sha256 ON money_receipts(sha256)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_receipts_stage ON money_receipts(stage)")
        }
    }
}
