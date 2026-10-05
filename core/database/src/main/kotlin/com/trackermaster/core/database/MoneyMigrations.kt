package com.trackermaster.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trackermaster.core.domain.money.Money
import java.time.Instant
import java.time.ZoneId

object MoneyMigrations {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN entryRequestId TEXT")
            db.execSQL("CREATE UNIQUE INDEX index_transactions_entryRequestId ON transactions (entryRequestId)")
            db.execSQL("ALTER TABLE expense_categories ADD COLUMN builtinKey TEXT")
            db.execSQL("CREATE UNIQUE INDEX index_expense_categories_builtinKey ON expense_categories (builtinKey)")
        }
    }

    fun seedSpaces(db: SupportSQLiteDatabase) {
        listOf("UNASSIGNED", "PERSONAL", "BUSINESS", "SAVINGS", "TRAVEL").forEachIndexed { index, kind ->
            db.execSQL("INSERT OR IGNORE INTO money_spaces (id, kind, name) VALUES (?, ?, '')", arrayOf(index + 1, kind))
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS money_spaces (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_money_spaces_kind_name ON money_spaces (kind, name)")
            seedSpaces(db)
            db.execSQL("ALTER TABLE accounts ADD COLUMN initialBalanceMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE accounts ADD COLUMN spaceId INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE accounts ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_pockets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId INTEGER NOT NULL, spaceId INTEGER NOT NULL, name TEXT NOT NULL, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(spaceId) REFERENCES money_spaces(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_pockets_accountId ON money_pockets (accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_pockets_spaceId ON money_pockets (spaceId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_transfers (id TEXT NOT NULL PRIMARY KEY, sourceAccountId INTEGER NOT NULL, targetAccountId INTEGER NOT NULL, FOREIGN KEY(sourceAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(targetAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_transfers_sourceAccountId ON money_transfers (sourceAccountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_transfers_targetAccountId ON money_transfers (targetAccountId)")
            db.execSQL("ALTER TABLE transactions ADD COLUMN amountMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN dateEpochDay INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN spaceId INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE transactions ADD COLUMN pocketId INTEGER REFERENCES money_pockets(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN transferId TEXT REFERENCES money_transfers(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_pocketId ON transactions (pocketId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_dateEpochDay ON transactions (dateEpochDay)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_spaceId_dateEpochDay ON transactions (spaceId, dateEpochDay)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_transferId_type ON transactions (transferId, type)")
            db.execSQL("ALTER TABLE budgets ADD COLUMN limitAmountMinor INTEGER")
            db.execSQL("ALTER TABLE budgets ADD COLUMN currencyCode TEXT")
            db.execSQL("ALTER TABLE budgets ADD COLUMN spaceId INTEGER")

            // Do not infer a currency for old budgets: the old schema did not record one.
            val currencies = mutableMapOf<Long, String>()
            db.query("SELECT id, currencyCode, initialBalance FROM accounts").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val currency = cursor.getString(1)
                    val amount = Money.fromLegacy(cursor.getDouble(2), currency)
                    currencies[id] = currency
                    db.execSQL("UPDATE accounts SET initialBalanceMinor = ? WHERE id = ?", arrayOf(amount.minorUnits, id))
                }
            }
            val zone = ZoneId.systemDefault()
            db.query("SELECT id, accountId, amount, dateEpoch FROM transactions").use { cursor ->
                while (cursor.moveToNext()) {
                    val currency = requireNotNull(currencies[cursor.getLong(1)]) { "Money migration: transaction has no account" }
                    val amount = Money.fromLegacy(cursor.getDouble(2), currency)
                    val day = Instant.ofEpochMilli(cursor.getLong(3)).atZone(zone).toLocalDate().toEpochDay()
                    db.execSQL("UPDATE transactions SET amountMinor = ?, dateEpochDay = ? WHERE id = ?",
                        arrayOf(amount.minorUnits, day, cursor.getLong(0)))
                }
            }
            // Some databases upgraded by the old 3→4 migration have an attachment FK
            // that the version-4 entity never declared. Normalize only that variant, retaining every row.
            val hasAttachmentForeignKey = db.query("PRAGMA foreign_key_list(attachments)").use { it.moveToFirst() }
            if (hasAttachmentForeignKey) {
                val oldSequence = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'attachments'").use {
                    if (it.moveToFirst()) it.getLong(0) else 0L
                }
                db.execSQL("CREATE TABLE attachments_v5 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entryId INTEGER NOT NULL, path TEXT NOT NULL, mimeType TEXT NOT NULL)")
                db.execSQL("INSERT INTO attachments_v5 SELECT id, entryId, path, mimeType FROM attachments")
                db.execSQL("DROP TABLE attachments")
                db.execSQL("ALTER TABLE attachments_v5 RENAME TO attachments")
                val currentSequence = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'attachments'").use {
                    if (it.moveToFirst()) it.getLong(0) else 0L
                }
                db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'attachments'")
                db.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES ('attachments', ?)",
                    arrayOf(maxOf(oldSequence, currentSequence)))
            }
        }
    }
}

object MoneyDatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) { MoneyMigrations.seedSpaces(db) }
}
