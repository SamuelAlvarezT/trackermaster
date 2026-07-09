package com.trackermaster.core.data.repository

import com.trackermaster.core.data.mapper.*
import com.trackermaster.core.database.dao.*
import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val achievementDao: AchievementDao,
) {
    fun observeActiveHabits(): Flow<List<Habit>> =
        habitDao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeArchivedHabits(): Flow<List<Habit>> =
        habitDao.observeArchived().map { list -> list.map { it.toDomain() } }

    fun observeTodayHabits(date: LocalDate = LocalDate.now()): Flow<List<Pair<Habit, HabitLog?>>> =
        combine(observeActiveHabits(), habitLogDao.observeByDate(date.toEpochMilli())) { habits, logs ->
            habits.filter { HabitScheduleLogic.isDueOnDate(it, date) }.map { h ->
                h to logs.find { it.habitId == h.id }?.toDomain()
            }
        }

    fun observeActiveHabitsWithLogs(since: LocalDate): Flow<List<Pair<Habit, List<HabitLog>>>> =
        combine(observeActiveHabits(), habitLogDao.observeLogsSince(since.toEpochMilli())) { habits, logs ->
            habits.map { habit ->
                val habitLogs = logs.filter { it.habitId == habit.id }.map { it.toDomain() }
                habit to habitLogs
            }
        }

    fun observeArchivedHabitsWithLogs(since: LocalDate): Flow<List<Pair<Habit, List<HabitLog>>>> =
        combine(observeArchivedHabits(), habitLogDao.observeLogsSince(since.toEpochMilli())) { habits, logs ->
            habits.map { habit ->
                val habitLogs = logs.filter { it.habitId == habit.id }.map { it.toDomain() }
                habit to habitLogs
            }
        }

    suspend fun updateHabitsOrder(habits: List<Habit>) {
        habits.forEachIndexed { index, habit ->
            habitDao.update(habit.copy(sortOrder = index).toEntity())
        }
    }

    suspend fun archiveHabit(habitId: Long, archive: Boolean) {
        habitDao.getById(habitId)?.let { entity ->
            habitDao.update(entity.copy(archived = archive))
        }
    }

    suspend fun deleteHabit(habit: Habit) {
        habitDao.delete(habit.toEntity())
    }

    fun observeHabitLogs(habitId: Long): Flow<List<HabitLog>> =
        habitLogDao.observeByHabit(habitId).map { logs ->
            logs.map { it.toDomain() }
        }

    fun observeCompletionsCount(habitId: Long): Flow<Int> =
        habitLogDao.observeByHabit(habitId).map { logs ->
            logs.count { it.completed }
        }

    suspend fun saveHabit(habit: Habit): Long {
        val id = if (habit.id == 0L) habitDao.insert(habit.toEntity()) else {
            habitDao.update(habit.toEntity()); habit.id
        }
        return id
    }

    suspend fun logHabit(habit: Habit, log: HabitLog) {
        val existing = habitLogDao.getForDate(habit.id, log.date.toEpochMilli())
        if (existing != null) {
            habitLogDao.update(log.copy(id = existing.id).toEntity())
        } else {
            habitLogDao.insert(log.copy(habitId = habit.id).toEntity())
        }
        checkAchievements(habit.id)
    }

    suspend fun toggleCheckbox(habit: Habit, date: LocalDate = LocalDate.now()) {
        val existing = habitLogDao.getForDate(habit.id, date.toEpochMilli())
        val completed = existing?.completed != true
        logHabit(habit, HabitLog(
            id = existing?.id ?: 0,
            habitId = habit.id,
            date = date,
            completed = completed,
            value = if (completed) 1 else 0,
        ))
    }

    suspend fun toggleHabitDayState(habit: Habit, date: LocalDate) {
        val existing = habitLogDao.getForDate(habit.id, date.toEpochMilli())?.toDomain()
        val nextLog = when {
            existing == null || (!existing.completed && existing.value >= 0) -> {
                HabitLog(
                    id = existing?.id ?: 0,
                    habitId = habit.id,
                    date = date,
                    completed = true,
                    value = 1
                )
            }
            existing.completed -> {
                HabitLog(
                    id = existing.id,
                    habitId = habit.id,
                    date = date,
                    completed = false,
                    value = -1
                )
            }
            else -> {
                HabitLog(
                    id = existing.id,
                    habitId = habit.id,
                    date = date,
                    completed = false,
                    value = 0
                )
            }
        }
        logHabit(habit, nextLog)
    }

    fun streakFor(habitId: Long): Flow<StreakInfo> =
        habitLogDao.observeByHabit(habitId).map { logs ->
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@map StreakInfo(0, 0)
            HabitScheduleLogic.calculateStreak(habit, logs.map { it.toDomain() })
        }

    suspend fun getHeatmapData(habitId: Long, year: Int): Map<LocalDate, Int> {
        val logs = habitLogDao.getAllForHabit(habitId).map { it.toDomain() }
        return logs.filter { it.date.year == year }.associate { it.date to if (HabitScheduleLogic.isLogComplete(it)) 1 else 0 }
    }

    private suspend fun checkAchievements(habitId: Long) {
        val habit = habitDao.getById(habitId)?.toDomain() ?: return
        val logs = habitLogDao.getAllForHabit(habitId).map { it.toDomain() }
        val streak = HabitScheduleLogic.calculateStreak(habit, logs)
        listOf(7 to "streak_7", 30 to "streak_30", 100 to "streak_100").forEach { (days, type) ->
            if (streak.best >= days && achievementDao.countByType(type, habitId) == 0) {
                achievementDao.insert(
                    com.trackermaster.core.database.entity.AchievementEntity(
                        type = type,
                        title = "$days day streak!",
                        unlockedAtEpoch = LocalDateTime.now().toEpochMilli(),
                        relatedId = habitId,
                    )
                )
            }
        }
    }

    fun observeAchievements(): Flow<List<Achievement>> =
        achievementDao.observeAll().map { it.map { e -> e.toDomain() } }
}

@Singleton
class MoodRepository @Inject constructor(
    private val moodDao: MoodDao,
    private val habitLogDao: HabitLogDao,
    private val habitDao: HabitDao,
) {
    fun observeEntries(): Flow<List<MoodEntry>> =
        moodDao.observeEntries().map { it.map { e -> e.toDomain() } }

    suspend fun logMood(level: Int, note: String = "", tagIds: List<Long> = emptyList()) {
        moodDao.insertEntry(
            com.trackermaster.core.database.entity.MoodEntryEntity(
                timestampEpoch = LocalDateTime.now().toEpochMilli(),
                level = level,
                note = note,
                tagIdsJson = tagIds.joinToString(","),
            )
        )
    }

    fun calendarForMonth(month: YearMonth): Flow<Map<LocalDate, Int>> =
        observeEntries().map { entries ->
            entries.filter {
                YearMonth.from(it.timestamp) == month
            }.groupBy { it.timestamp.toLocalDate() }
                .mapValues { (_, list) -> list.maxByOrNull { it.timestamp }!!.level }
        }

    suspend fun moodHabitCorrelation(): List<MoodHabitCorrelation> {
        val habits = habitDao.observeActive().first()
        val from = LocalDate.now().minusMonths(3).toEpochMilli()
        val to = LocalDate.now().plusDays(1).toEpochMilli()
        val moods = moodDao.getBetween(from, to).map { it.toDomain() }
        return habits.map { habit ->
            val habitLogs = habitLogDao.getAllForHabit(habit.id).map { it.toDomain() }
            val completedDates = habitLogs.filter { HabitScheduleLogic.isLogComplete(it) }.map { it.date }.toSet()
            val withHabit = moods.filter { m ->
                val d = m.timestamp.toLocalDate()
                d in completedDates || d.minusDays(1) in completedDates || d.plusDays(1) in completedDates
            }
            val without = moods.filter { m ->
                val d = m.timestamp.toLocalDate()
                d !in completedDates && d.minusDays(1) !in completedDates && d.plusDays(1) !in completedDates
            }
            MoodHabitCorrelation(
                habitId = habit.id,
                habitName = habit.name,
                avgMoodWithHabit = if (withHabit.isEmpty()) 0.0 else withHabit.map { it.level }.average(),
                avgMoodWithoutHabit = if (without.isEmpty()) 0.0 else without.map { it.level }.average(),
            )
        }
    }
}

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
) {
    fun observeAccounts(): Flow<List<Account>> = expenseDao.observeAccounts().map { it.map { a -> a.toDomain() } }
    fun observeCategories(): Flow<List<ExpenseCategory>> = expenseDao.observeCategories().map { it.map { it.toDomain() } }
    fun observeTransactions(): Flow<List<Transaction>> = expenseDao.observeTransactions().map { it.map { it.toDomain() } }
    fun observeBudgets(): Flow<List<Budget>> = expenseDao.observeBudgets().map { it.map { it.toDomain() } }

    fun totalBalance(): Flow<Double> = combine(observeAccounts(), observeTransactions()) { accounts, txs ->
        accounts.sumOf { acc ->
            val accTx = txs.filter { it.accountId == acc.id }
            acc.initialBalance + accTx.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
        }
    }

    suspend fun addAccount(account: Account) = expenseDao.insertAccount(account.toEntity())
    suspend fun addTransaction(tx: Transaction) = expenseDao.insertTransaction(tx.toEntity())

    suspend fun seedDefaultCategories() {
        if (expenseDao.observeCategories().first().isEmpty()) {
            listOf("Food", "Transport", "Shopping", "Bills", "Salary").forEachIndexed { i, name ->
                expenseDao.insertCategory(
                    com.trackermaster.core.database.entity.ExpenseCategoryEntity(
                        name = name,
                        isIncome = name == "Salary",
                        colorArgb = listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFF4DB6AC)[i].toInt(),
                    )
                )
            }
        }
        if (expenseDao.observeAccounts().first().isEmpty()) {
            expenseDao.insertAccount(
                com.trackermaster.core.database.entity.AccountEntity(
                    name = "Main account",
                    type = AccountType.CHECKING.name,
                    currencyCode = "USD",
                )
            )
        }
    }
}

private fun Account.toEntity() = com.trackermaster.core.database.entity.AccountEntity(
    id, name, type.name, currencyCode, initialBalance
)

private fun ExpenseCategory.toEntity() = com.trackermaster.core.database.entity.ExpenseCategoryEntity(
    id, name, isIncome, colorArgb
)

private fun Transaction.toEntity() = com.trackermaster.core.database.entity.TransactionEntity(
    id, accountId, amount, type.name, categoryId, date.toEpochMilli(), note, imageUri
)

private fun Budget.toEntity() = com.trackermaster.core.database.entity.BudgetEntity(
    id, categoryId, limitAmount, period, alertThresholdPercent
)

@Singleton
class FocusRepository @Inject constructor(
    private val focusDao: FocusDao,
) {
    fun observeSessions(): Flow<List<FocusSession>> =
        focusDao.observeSessions().map { it.map { s -> s.toDomain() } }

    suspend fun startSession(category: FocusCategoryType, plannedMinutes: Int): Long =
        focusDao.insert(
            com.trackermaster.core.database.entity.FocusSessionEntity(
                category = category.name,
                plannedMinutes = plannedMinutes,
            )
        )

    suspend fun completeSession(id: Long, actualSeconds: Int) {
        val sessions = focusDao.observeSessions().first()
        val s = sessions.find { it.id == id } ?: return
        focusDao.update(
            s.copy(
                actualSeconds = actualSeconds,
                completed = true,
                completedAtEpoch = LocalDateTime.now().toEpochMilli(),
            )
        )
    }

    suspend fun stopSession(id: Long, actualSeconds: Int, completed: Boolean = false) {
        val sessions = focusDao.observeSessions().first()
        val s = sessions.find { it.id == id } ?: return
        focusDao.update(
            s.copy(
                actualSeconds = actualSeconds,
                completed = completed,
                completedAtEpoch = LocalDateTime.now().toEpochMilli(),
            )
        )
    }

    fun weeklyFocusScore(): Flow<Int> = observeSessions().map { sessions ->
        val weekAgo = LocalDateTime.now().minusDays(7)
        HabitScheduleLogic.calculateFocusScore(
            sessions.filter { it.completedAt?.isAfter(weekAgo) == true }
        )
    }
}

@Singleton
class JournalRepository @Inject constructor(
    private val journalDao: JournalDao,
) {
    fun observeEntries(): Flow<List<JournalEntry>> =
        combine(journalDao.observeEntries(), journalDao.observeAllAttachments()) { entries, attachments ->
            entries.map { entry ->
                entry.toDomain().copy(
                    attachments = attachments.filter { it.entryId == entry.id }.map { it.toDomain() }
                )
            }
        }

    suspend fun save(entry: JournalEntry): Long {
        val id = if (entry.id == 0L) journalDao.insert(entry.toEntity())
        else { journalDao.update(entry.toEntity()); entry.id }
        entry.attachments.forEach { attachment ->
            journalDao.insertAttachment(attachment.copy(entryId = id).toEntity())
        }
        return id
    }

    fun writingStreak(): Flow<Int> = observeEntries().map { entries ->
        HabitScheduleLogic.journalWritingStreak(entries.map { it.createdAt.toLocalDate() })
    }
}

private fun JournalEntry.toEntity() = com.trackermaster.core.database.entity.JournalEntryEntity(
    id, title, richTextHtml, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), moodLevel
)

@Singleton
class DashboardRepository @Inject constructor(
    private val habitRepository: HabitRepository,
    private val moodRepository: MoodRepository,
    private val expenseRepository: ExpenseRepository,
    private val focusRepository: FocusRepository,
    private val journalRepository: JournalRepository,
) {
    fun observeSummary(): Flow<DashboardSummary> {
        val today = LocalDate.now()
        return combine(
            habitRepository.observeTodayHabits(today),
            moodRepository.observeEntries(),
            expenseRepository.totalBalance(),
            focusRepository.weeklyFocusScore(),
            journalRepository.writingStreak(),
        ) { todayHabits, moods, balance, focusScore, journalStreak ->
            DashboardSummary(
                habitsDueToday = todayHabits.size,
                habitsCompletedToday = todayHabits.count { (_, log) -> log != null && HabitScheduleLogic.isLogComplete(log) },
                latestMoodLevel = moods.firstOrNull()?.level,
                totalBalance = balance,
                focusScoreWeek = focusScore,
                journalStreak = journalStreak,
            )
        }
    }
}

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
) {
    fun observeTasks(): Flow<List<Task>> =
        taskDao.observeTasks().map { list -> list.map { it.toDomain() } }

    suspend fun insertTask(task: Task): Long =
        taskDao.insert(task.toEntity())

    suspend fun toggleTask(task: Task) =
        taskDao.update(task.copy(completed = !task.completed).toEntity())

    suspend fun updateTask(task: Task) =
        taskDao.update(task.toEntity())

    suspend fun deleteTask(task: Task) =
        taskDao.delete(task.toEntity())
}

