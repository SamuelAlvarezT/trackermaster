package com.trackermaster.core.database.entity

import androidx.room.Entity
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
)

@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isIncome: Boolean = false,
    val colorArgb: Int,
)

@Entity(tableName = "transactions", indices = [Index("accountId"), Index("dateEpoch")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Double,
    val type: String,
    val categoryId: Long,
    val dateEpoch: Long,
    val note: String = "",
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val limitAmount: Double,
    val period: String = "MONTHLY",
    val alertThresholdPercent: Int = 80,
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
)

@Entity(tableName = "journal_entries", indices = [Index("createdAtEpoch")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val richTextHtml: String,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long,
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
