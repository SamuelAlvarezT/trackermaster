package com.trackermaster.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trackermaster.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeArchived(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY dateEpoch DESC")
    fun observeByHabit(habitId: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE dateEpoch = :dateEpoch")
    fun observeByDate(dateEpoch: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE dateEpoch >= :sinceEpoch")
    fun observeLogsSince(sinceEpoch: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND dateEpoch = :dateEpoch LIMIT 1")
    suspend fun getForDate(habitId: Long, dateEpoch: Long): HabitLogEntity?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId")
    suspend fun getAllForHabit(habitId: Long): List<HabitLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HabitLogEntity): Long

    @Update
    suspend fun update(log: HabitLogEntity)
}

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entries ORDER BY timestampEpoch DESC")
    fun observeEntries(): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries WHERE timestampEpoch BETWEEN :from AND :to ORDER BY timestampEpoch")
    suspend fun getBetween(from: Long, to: Long): List<MoodEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MoodEntryEntity): Long

    @Query("SELECT * FROM mood_tags ORDER BY name")
    fun observeTags(): Flow<List<MoodTagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: MoodTagEntity): Long
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(a: AccountEntity): Long

    @Query("SELECT * FROM expense_categories ORDER BY name")
    fun observeCategories(): Flow<List<ExpenseCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(c: ExpenseCategoryEntity): Long

    @Query("SELECT * FROM transactions ORDER BY dateEpoch DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(t: TransactionEntity): Long

    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(b: BudgetEntity): Long

    @Query("SELECT * FROM exchange_rates")
    fun observeRates(): Flow<List<ExchangeRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(r: ExchangeRateEntity)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY completedAtEpoch DESC")
    fun observeSessions(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY createdAtEpoch DESC")
    fun observeEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getById(id: Long): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity): Long

    @Update
    suspend fun update(entry: JournalEntryEntity)

    @Delete
    suspend fun delete(entry: JournalEntryEntity)

    @Query("SELECT * FROM attachments WHERE entryId = :entryId")
    suspend fun getAttachments(entryId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    fun observeAllAttachments(): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(a: AttachmentEntity): Long
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY unlockedAtEpoch DESC")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: AchievementEntity): Long

    @Query("SELECT COUNT(*) FROM achievements WHERE type = :type AND relatedId = :relatedId")
    suspend fun countByType(type: String, relatedId: Long): Int
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

