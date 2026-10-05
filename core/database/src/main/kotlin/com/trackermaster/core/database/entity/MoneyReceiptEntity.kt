package com.trackermaster.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Audit source outlives a deleted movement; active links use the ledger's stable request/transfer id. */
@Entity(tableName = "money_receipts", indices = [Index("sha256"), Index("stage")])
data class MoneyReceiptEntity(@PrimaryKey val id: String, val path: String, val mimeType: String,
    val sha256: String, val name: String, val text: String = "", val previewPath: String? = null,
    val stage: String = "READING", val draftJson: String = "", val createdAt: Long)
