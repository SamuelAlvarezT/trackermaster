package com.trackermaster.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "money_investments", indices = [Index("spaceId")])
data class MoneyInvestmentEntity(@PrimaryKey val id: String, val name: String, val currency: String,
    val spaceId: Long, val openedEpochDay: Long, val initialCostMinor: Long, val initialValueMinor: Long,
    val note: String, val archived: Boolean, val version: Long)

@Entity(tableName = "money_investment_events", indices = [Index("investmentId"), Index(value = ["requestId"], unique = true), Index(value = ["movementId"], unique = true)])
data class MoneyInvestmentEventEntity(@PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val requestId: String, val investmentId: String, val kind: String, val amountMinor: Long,
    val costMinor: Long, val dateEpochDay: Long, val movementId: Long?, val createdMovement: Boolean, val note: String)
