package com.trackermaster.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trackermaster.core.database.dao.*
import com.trackermaster.core.database.entity.*

@Database(
    entities = [
        AssistantCommandReceipt::class,
        CloudRecordVersion::class,
        CloudSyncOperation::class,
        CloudSyncState::class,
        HabitEntity::class,
        HabitLogEntity::class,
        MoodEntryEntity::class,
        MoodTagEntity::class,
        AccountEntity::class,
        ExpenseCategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        ExchangeRateEntity::class,
        FocusSessionEntity::class,
        JournalEntryEntity::class,
        AttachmentEntity::class,
        AchievementEntity::class,
        TaskEntity::class,
        MoneySpaceEntity::class,
        MoneyPocketEntity::class,
        MoneyTransferEntity::class,
        MoneyReceiptEntity::class,
        MoneyPlanEntity::class,
        MoneyPlanOccurrenceEntity::class,
        MoneyPlanningAlertEntity::class,
        MoneyDebtEntity::class,
        DebtPaymentEntity::class,
        SavingsGoalEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class TrackermasterDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun moodDao(): MoodDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun focusDao(): FocusDao
    abstract fun journalDao(): JournalDao
    abstract fun achievementDao(): AchievementDao
    abstract fun taskDao(): TaskDao
    abstract fun moneyDao(): MoneyDao
    abstract fun moneyReceiptDao(): MoneyReceiptDao
    abstract fun moneyPlanningDao(): MoneyPlanningDao
    abstract fun moneyCommitmentDao(): MoneyCommitmentDao
}
