package com.trackermaster.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "money_plans", indices = [Index("nextDueEpochDay"), Index("accountId"), Index("categoryId")])
data class MoneyPlanEntity(@PrimaryKey val id: String, val name: String, val kind: String, val direction: String,
    val amountMinor: Long?, val currencyCode: String, val accountId: Long?, val pocketId: Long?, val categoryId: Long?,
    val anchorEpochDay: Long, val nextDueEpochDay: Long, val frequency: String, val reminderDays: Int,
    val remindOnEpochDay: Long?, val note: String, val active: Boolean, val archived: Boolean, val version: Long, val plannedSpaceId: Long? = null)

// Audit survives movement deletion and plan archival; undo can restore the same movement id.
@Entity(tableName = "money_plan_occurrences", primaryKeys = ["planId", "dueEpochDay"],
    indices = [Index(value = ["movementId"], unique = true)])
data class MoneyPlanOccurrenceEntity(val planId: String, val dueEpochDay: Long, val status: String,
    val movementId: Long?, val fingerprint: String)

@Entity(tableName = "money_planning_alerts")
data class MoneyPlanningAlertEntity(@PrimaryKey val eventKey: String, val leaseToken: String,
    val claimedAtEpoch: Long, val delivered: Boolean)
