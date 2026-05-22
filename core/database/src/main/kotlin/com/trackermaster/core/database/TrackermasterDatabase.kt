package com.trackermaster.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trackermaster.core.database.dao.*
import com.trackermaster.core.database.entity.*

@Database(
    entities = [
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
    ],
    version = 1,
    exportSchema = false,
)
abstract class TrackermasterDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun moodDao(): MoodDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun focusDao(): FocusDao
    abstract fun journalDao(): JournalDao
    abstract fun achievementDao(): AchievementDao
}
