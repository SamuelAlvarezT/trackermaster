package com.trackermaster.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "money_debts", indices = [Index("spaceId")])
data class MoneyDebtEntity(@PrimaryKey val id: String, val person: String, val kind: String,
    val principalMinor: Long, val currency: String, val spaceId: Long, val dueEpochDay: Long?,
    val note: String, val archived: Boolean, val version: Long)

@Entity(tableName = "money_debt_payments", indices = [Index("debtId"), Index(value = ["movementId"], unique = true)])
data class DebtPaymentEntity(@PrimaryKey val id: String, val debtId: String, val movementId: Long,
    val event: String, val createdMovement: Boolean)

@Entity(tableName = "money_savings_goals", indices = [Index(value = ["pocketId"], unique = true)])
data class SavingsGoalEntity(@PrimaryKey val id: String, val name: String, val targetMinor: Long,
    val currency: String, val pocketId: Long, val dueEpochDay: Long?, val archived: Boolean, val version: Long)
