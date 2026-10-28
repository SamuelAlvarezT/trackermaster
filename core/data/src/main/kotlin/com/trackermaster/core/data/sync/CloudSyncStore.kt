package com.trackermaster.core.data.sync

import android.database.Cursor
import com.trackermaster.core.database.CloudSyncSchema
import com.trackermaster.core.database.TrackermasterDatabase
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class PendingRecord(val sequence: Long, val kind: String, val key: String)
data class SyncIdentity(val datasetId: String, val ownerUid: String, val lastCompletedAt: Long)

@Singleton
class CloudSyncStore @Inject constructor(private val room: TrackermasterDatabase) {
    private val db get() = room.openHelper.writableDatabase
    fun identity(): SyncIdentity = db.query("SELECT datasetId,ownerUid,lastCompletedAt FROM cloud_sync_state WHERE id=1").use {
        check(it.moveToFirst()); SyncIdentity(it.getString(0), it.getString(1), it.getLong(2))
    }
    fun pendingCount(): Int = db.query("SELECT COUNT(*) FROM cloud_sync_outbox").use { it.moveToFirst(); it.getInt(0) }
    fun bindOwner(uid: String) {
        val current = identity()
        check(current.ownerUid.isEmpty() || current.ownerUid == uid) { "Esta base está vinculada a otra cuenta." }
        if (current.ownerUid == uid) return
        db.execSQL("UPDATE cloud_sync_state SET ownerUid=? WHERE id=1", arrayOf(uid))
    }
    fun pending(): List<PendingRecord> = db.query("SELECT sequence,tableName,recordKey FROM cloud_sync_outbox ORDER BY sequence LIMIT 50").use { cursor ->
        buildList { while (cursor.moveToNext()) add(PendingRecord(cursor.getLong(0), cursor.getString(1), cursor.getString(2))) }
    }
    fun acknowledge(records: List<PendingRecord>) {
        // Delete precisely what was acknowledged: concurrent edits have different sequences.
        db.beginTransaction()
        try {
            records.forEach {
                db.execSQL("INSERT OR REPLACE INTO cloud_record_versions(recordId,revision) VALUES (?,?)", arrayOf(documentId(it.kind, it.key), it.sequence))
                db.execSQL("DELETE FROM cloud_sync_outbox WHERE sequence=?", arrayOf(it.sequence))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun complete(at: Long) { db.execSQL("UPDATE cloud_sync_state SET lastCompletedAt=? WHERE id=1", arrayOf(at)) }

    /** Room update and receipt commit together; retries never apply a command twice. */
    fun applyTarget(commandId: String, recordId: String, recordKey: String, localId: Long,
                    expectedRevision: Long, expectedTargetMs: Long, targetMs: Long, effectiveFrom: String): String {
        require(targetMs in 300_000L..3_600_000L)
        require(documentId("habits", recordKey) == recordId)
        val today = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota"))
        val requested = java.time.LocalDate.parse(effectiveFrom)
        db.beginTransaction()
        try {
            val receipt = db.query("SELECT state,effectiveDay FROM assistant_commands WHERE id=?", arrayOf(commandId)).use {
                if (it.moveToFirst()) it.getString(0) to java.time.LocalDate.parse(it.getString(1)) else null
            }
            if (receipt != null && receipt.first != "waiting") return receipt.first
            val effective = receipt?.second ?: if (requested < today) today.plusDays(1) else requested
            if (today < effective) {
                db.execSQL("INSERT OR IGNORE INTO assistant_commands(id,state,effectiveDay) VALUES (?,'waiting',?)", arrayOf(commandId, effective.toString()))
                db.setTransactionSuccessful()
                return "waiting"
            }
            val version = db.query("SELECT revision FROM cloud_record_versions WHERE recordId=?", arrayOf(recordId)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
            val pending = db.query("SELECT COUNT(*) FROM cloud_sync_outbox WHERE tableName='habits' AND recordKey=?", arrayOf(recordKey)).use { it.moveToFirst(); it.getInt(0) > 0 }
            val habit = db.query("SELECT targetDurationMs,archived,progressType FROM habits WHERE id=? AND ${CloudSyncSchema.keyExpression("habits") }=?", arrayOf(localId, recordKey)).use {
                if (it.moveToFirst()) Triple(it.getLong(0), it.getInt(1), it.getString(2)) else null
            }
            val state = if (pending || version != expectedRevision || habit == null || habit.first != expectedTargetMs || habit.second != 0 || habit.third !in listOf("TIMER", "STOPWATCH")) "conflict" else "applied"
            if (state == "applied") db.execSQL("UPDATE habits SET targetDurationMs=? WHERE id=?", arrayOf(targetMs, localId))
            db.execSQL("INSERT OR REPLACE INTO assistant_commands(id,state,effectiveDay) VALUES (?,?,?)", arrayOf(commandId, state, effective.toString()))
            db.setTransactionSuccessful()
            return state
        } finally { db.endTransaction() }
    }

    fun payload(record: PendingRecord): Map<String, Any?>? {
        check(record.kind in CloudSyncSchema.tables)
        return db.query("SELECT * FROM `${record.kind}` WHERE ${CloudSyncSchema.keyExpression(record.kind)}=?", arrayOf(record.key)).use { cursor ->
            if (!cursor.moveToFirst()) null else buildMap {
                cursor.columnNames.forEachIndexed { index, name ->
                    // Integer strings preserve Long ids and money exactly in JavaScript.
                    put(name, when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                        else -> error("Unsupported cloud field: $name")
                    })
                }
            }.let { SyncProjection.sanitize(record.kind, it) }
        }
    }

    companion object {
        fun documentId(kind: String, key: String): String = MessageDigest.getInstance("SHA-256")
            .digest("$kind:$key".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

object SyncProjection {
    fun sanitize(kind: String, input: Map<String, Any?>): Map<String, Any?> = input.toMutableMap().apply {
        // Paths/URIs cannot be used on a PC and must not leak local filesystem information.
        listOf("path", "previewPath", "imageUri").forEach { field ->
            if (containsKey(field)) { put("${field}LocalOnly", !get(field)?.toString().isNullOrBlank()); remove(field) }
        }
        if (kind == "money_receipts") remove("draftJson") // OCR drafts may contain local file paths.
    }
    fun eventDay(kind: String, data: Map<String, Any?>): Long {
        fun value(key: String) = data[key]?.toString()?.toLongOrNull()
        val days = when (kind) {
            "transactions" -> value("dateEpochDay")
            "money_plans" -> value("nextDueEpochDay")
            "money_plan_occurrences" -> value("dueEpochDay")
            else -> null
        }
        if (days != null) return days
        val millis = listOf("dateEpoch", "timestampEpoch", "startedAtEpoch", "completedAtEpoch", "createdAtEpoch", "createdAt", "unlockedAtEpoch", "updatedAtEpoch")
            .firstNotNullOfOrNull { value(it) }
        return millis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() } ?: 0L
    }
}
