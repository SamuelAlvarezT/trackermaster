package com.trackermaster.core.database.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val progressType: String,
    val scheduleType: String,
    val scheduleDaysBitmask: Int = 0,
    val targetCount: Int = 1,
    val targetDurationMs: Long = 0,
    val checklistItemsJson: String = "[]",
    val colorArgb: Int,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val archived: Boolean = false,
    val description: String = "",
    val sortOrder: Int = 0,
)

@Entity(tableName = "habit_logs", indices = [Index("habitId"), Index("dateEpoch")])
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val dateEpoch: Long,
    val value: Int = 0,
    val durationMs: Long = 0,
    val checklistStateJson: String = "{}",
    val completed: Boolean = false,
    val targetDurationMsAtLog: Long? = null,
    val completionSource: String? = null,
)

@Entity(tableName = "mood_entries", indices = [Index("timestampEpoch")])
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpoch: Long,
    val level: Int,
    val note: String = "",
    val tagIdsJson: String = "[]",
)

@Entity(tableName = "mood_tags")
data class MoodTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val currencyCode: String,
    val initialBalance: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val initialBalanceMinor: Long = 0,
    @ColumnInfo(defaultValue = "1") val spaceId: Long = 1,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
)

@Entity(tableName = "expense_categories", indices = [Index(value = ["builtinKey"], unique = true)])
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isIncome: Boolean = false,
    val colorArgb: Int,
    val builtinKey: String? = null,
)

@Entity(tableName = "transactions", indices = [Index("accountId"), Index("dateEpoch"),
    Index("pocketId"), Index("dateEpochDay"), Index(value = ["spaceId", "dateEpochDay"]),
    Index(value = ["transferId", "type"], unique = true), Index(value = ["entryRequestId"], unique = true)], foreignKeys = [
    ForeignKey(entity = MoneyPocketEntity::class, parentColumns = ["id"], childColumns = ["pocketId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = MoneyTransferEntity::class, parentColumns = ["id"], childColumns = ["transferId"], onDelete = ForeignKey.RESTRICT),
])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Double,
    val type: String,
    val categoryId: Long,
    val dateEpoch: Long,
    val note: String = "",
    val imageUri: String? = null,
    @ColumnInfo(defaultValue = "0") val amountMinor: Long = 0,
    @ColumnInfo(defaultValue = "0") val dateEpochDay: Long = 0,
    @ColumnInfo(defaultValue = "1") val spaceId: Long = 1,
    val pocketId: Long? = null,
    val transferId: String? = null,
    val entryRequestId: String? = null,
    @ColumnInfo(defaultValue = "0") val principal: Boolean = false,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val limitAmount: Double,
    val period: String = "MONTHLY",
    val alertThresholdPercent: Int = 80,
    val limitAmountMinor: Long? = null,
    val currencyCode: String? = null,
    val spaceId: Long? = null,
    val startDateEpochDay: Long? = null,
    val endDateEpochDay: Long? = null,
)

@Entity(tableName = "exchange_rates", primaryKeys = ["fromCurrency", "toCurrency"])
data class ExchangeRateEntity(
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val updatedAtEpoch: Long,
)

@Entity(tableName = "focus_sessions", indices = [Index("completedAtEpoch")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val plannedMinutes: Int,
    val actualSeconds: Int = 0,
    val completedAtEpoch: Long? = null,
    val completed: Boolean = false,
    val startedAtEpoch: Long? = null,
)

@Entity(tableName = "journal_entries", indices = [Index("createdAtEpoch")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val richTextHtml: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long,
    val moodLevel: Int? = null,
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val path: String,
    val mimeType: String,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val unlockedAtEpoch: Long,
    val relatedId: Long? = null,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val completed: Boolean = false,
    val createdAtEpoch: Long = System.currentTimeMillis(),
    val subtasksJson: String = "",
    val plannedDate: String? = null,
    val completedAtEpoch: Long? = null,
    val cancelledAtEpoch: Long? = null,
)

