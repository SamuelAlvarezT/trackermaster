package com.trackermaster.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Entity(tableName = "cloud_sync_outbox")
data class CloudSyncOperation(@PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val tableName: String, val recordKey: String)

@Entity(tableName = "cloud_sync_state")
data class CloudSyncState(@PrimaryKey val id: Int = 1, val datasetId: String,
    val ownerUid: String = "", val lastCompletedAt: Long = 0)

/** SQLite triggers capture every writer, including widgets, inside the originating transaction. */
object CloudSyncSchema {
    val tables = linkedMapOf(
        "habits" to listOf("id"), "habit_logs" to listOf("id"),
        "mood_entries" to listOf("id"), "mood_tags" to listOf("id"),
        "accounts" to listOf("id"), "expense_categories" to listOf("id"),
        "transactions" to listOf("id"), "budgets" to listOf("id"),
        "exchange_rates" to listOf("fromCurrency", "toCurrency"),
        "focus_sessions" to listOf("id"), "journal_entries" to listOf("id"),
        "attachments" to listOf("id"), "achievements" to listOf("id"),
        "tasks" to listOf("id"), "money_spaces" to listOf("id"),
        "money_pockets" to listOf("id"), "money_transfers" to listOf("id"),
        "money_receipts" to listOf("id"), "money_plans" to listOf("id"),
        "money_plan_occurrences" to listOf("planId", "dueEpochDay"),
    )

    fun keyExpression(table: String, prefix: String = ""): String =
        tables.getValue(table).joinToString(" || ':' || ") { "hex(CAST($prefix`$it` AS BLOB))" }

    fun install(db: SupportSQLiteDatabase) {
        tables.forEach { (table, _) ->
            listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                val prefix = if (operation == "DELETE") "OLD." else "NEW."
                val oldKeyChange = if (operation == "UPDATE") """
                    INSERT INTO cloud_sync_outbox(tableName,recordKey)
                    SELECT '$table',${keyExpression(table, "OLD.")}
                    WHERE ${keyExpression(table, "OLD.")} != ${keyExpression(table, "NEW.")};
                """ else ""
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS cloud_${table}_${operation.lowercase()}
                    AFTER $operation ON `$table` BEGIN
                    $oldKeyChange
                    INSERT INTO cloud_sync_outbox(tableName,recordKey)
                    VALUES ('$table',${keyExpression(table, prefix)}); END""")
            }
        }
        db.execSQL("INSERT OR IGNORE INTO cloud_sync_state(id,datasetId,ownerUid,lastCompletedAt) VALUES (1,?,'',0)", arrayOf(UUID.randomUUID().toString()))
    }

    fun enqueueSnapshot(db: SupportSQLiteDatabase) {
        tables.keys.forEach { table ->
            db.execSQL("INSERT INTO cloud_sync_outbox(tableName,recordKey) SELECT '$table',${keyExpression(table)} FROM `$table`")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preliminary v8 snapshots predate the nullable planned-space field.
            val hasPlannedSpace = db.query("PRAGMA table_info(money_plans)").use { cursor ->
                var found = false
                while (cursor.moveToNext()) if (cursor.getString(1) == "plannedSpaceId") found = true
                found
            }
            if (!hasPlannedSpace) db.execSQL("ALTER TABLE money_plans ADD COLUMN plannedSpaceId INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS cloud_sync_outbox (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tableName TEXT NOT NULL, recordKey TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS cloud_sync_state (id INTEGER NOT NULL PRIMARY KEY, datasetId TEXT NOT NULL, ownerUid TEXT NOT NULL, lastCompletedAt INTEGER NOT NULL)")
            install(db)
            enqueueSnapshot(db)
        }
    }

    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) { install(db); enqueueSnapshot(db) }
        override fun onOpen(db: SupportSQLiteDatabase) { install(db) }
    }
}
