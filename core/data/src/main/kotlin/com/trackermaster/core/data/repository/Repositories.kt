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
import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.data.money.MoneyRepository
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.domain.habits.*
import com.trackermaster.core.data.progress.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val achievementDao: AchievementDao,
    private val database: TrackermasterDatabase,
    private val clock: ProgressClock,
) {
    private val progressDao get() = database.progressDao()
    fun observeDay() = clock.days()
    fun observePendingHabits(): Flow<List<PendingHabit>> = combine(habitDao.observeAll(),
        progressDao.observeAllLogs(),progressDao.observeHistory(),clock.days()) { habits,logs,history,today ->
        HabitProgressRules.pending(habits.map { it.toDomain() },logs.map { it.toDomain() },history.map { it.toDomain() },today)
    }.flowOn(Dispatchers.Default)
    suspend fun getHabit(id: Long): Habit? = habitDao.getById(id)?.toDomain()
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
            habitDao.updateSortOrder(habit.id,index)
        }
    }

    suspend fun archiveHabit(habitId: Long, archive: Boolean) = database.withTransaction {
        habitDao.getById(habitId)?.let { entity ->
            val changed=entity.copy(archived=archive)
            habitDao.update(changed)
            progressDao.saveHistory(HabitProgressRules.version(changed.toDomain(),clock.today()).toEntity())
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

    suspend fun saveHabit(habit: Habit): Long = database.withTransaction {
        val old=habitDao.getById(habit.id)
        val changed=if (old==null) habit.copy(createdEpochDay=clock.today().toEpochDay(),creationDateKnown=true,
            sortOrder=habitDao.observeAll().first().size) else habit.copy(createdEpochDay=old.createdEpochDay,
            creationDateKnown=old.creationDateKnown,sortOrder=old.sortOrder,archived=old.archived)
        val id=if (old==null) habitDao.insert(changed.toEntity()) else { habitDao.update(changed.toEntity());habit.id }
        val version=HabitProgressRules.version(changed.copy(id=id),clock.today())
        val last=progressDao.history(id).lastOrNull()?.toDomain()
        if (last==null || last.copy(effectiveFrom=version.effectiveFrom)!=version) progressDao.saveHistory(version.toEntity())
        id
    }

    suspend fun logHabit(habit: Habit, log: HabitLog) {
        database.withTransaction {
            val existing=habitLogDao.getForDate(habit.id,log.date.toEpochMilli())
            val current=habitDao.getById(habit.id)?.toDomain() ?: return@withTransaction
            val history=progressDao.history(habit.id).map { it.toDomain() }
            val version=HabitProgressRules.versionOn(current,log.date,history)
            val targetKnown=log.date==clock.today() || history.any { it.effectiveFrom<=log.date }
            val timed=version.progressType==ProgressType.TIMER || version.progressType==ProgressType.STOPWATCH
            val stamped=log.copy(habitId=habit.id,
                targetDurationMsAtLog=existing?.targetDurationMsAtLog ?: log.targetDurationMsAtLog ?: version.targetDurationMs.takeIf { timed && targetKnown },
                targetCountAtLog=existing?.targetCountAtLog ?: log.targetCountAtLog ?: version.targetCount.takeIf { targetKnown },
                progressTypeAtLog=existing?.progressTypeAtLog?.let { ProgressType.valueOf(it) } ?: log.progressTypeAtLog ?: version.progressType.takeIf { targetKnown },
                checklistSizeAtLog=existing?.checklistSizeAtLog ?: log.checklistSizeAtLog ?: version.checklistSize.takeIf { targetKnown },
                completionSource=if (log.completed && (!timed || log.durationMs<version.targetDurationMs)) "manual" else "measured")
            if (existing==null) habitLogDao.insert(stamped.toEntity()) else habitLogDao.update(stamped.copy(id=existing.id).toEntity())
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
            existing == null || (!existing.completed && existing.value == 0) -> {
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
                    value = 1
                )
            }
            existing.value > 0 -> {
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
    private val moneyRepository: MoneyRepository,
    private val database: TrackermasterDatabase,
    @ApplicationContext private val context: Context,
) {
    fun observeAccounts(): Flow<List<Account>> = expenseDao.observeAccounts().map { it.map { a -> a.toDomain() } }
    fun observeCategories(): Flow<List<ExpenseCategory>> = expenseDao.observeCategories().map { it.map { it.toDomain() } }
    fun observeTransactions(): Flow<List<Transaction>> = moneyRepository.observeMovements().map { rows ->
        rows.filter { it.transferId == null }.map {
            Transaction(it.id, it.accountId, it.amount.decimal.toDouble(), TransactionType.valueOf(it.direction.name),
                it.categoryId ?: 0, it.date, it.note, it.attachmentPath)
        }
    }
    fun observeBudgets(): Flow<List<Budget>> = expenseDao.observeBudgets().map { it.map { it.toDomain() } }

    fun balancesByCurrency(): Flow<List<Money>> = moneyRepository.observeSnapshot().map { it.currencies }

    /** Compatibility for the unchanged lock-screen UI: a single COP balance, never mixed currencies. */
    fun totalBalance(currencyCode: String = "COP"): Flow<Double> = balancesByCurrency().map { amounts ->
        amounts.firstOrNull { it.currencyCode == currencyCode }?.decimal?.toDouble() ?: 0.0
    }

    suspend fun addAccount(account: Account): Long {
        val initial = Money.fromDecimal(java.math.BigDecimal.valueOf(account.initialBalance), account.currencyCode)
        return if (account.id == 0L) moneyRepository.createAccount(account.name, account.type, initial, 2) else {
            moneyRepository.updateAccountDetails(account.id, account.name, account.type, initial)
            account.id
        }
    }
    suspend fun addTransaction(tx: Transaction): Long = database.withTransaction {
        val account = requireNotNull(database.moneyDao().account(tx.accountId))
        moneyRepository.record(MoneyMovement(id = tx.id, accountId = tx.accountId,
            amount = Money.fromDecimal(java.math.BigDecimal.valueOf(tx.amount), account.currencyCode),
            direction = MoneyDirection.valueOf(tx.type.name), date = tx.date, spaceId = account.spaceId,
            categoryId = tx.categoryId, note = tx.note, attachmentPath = tx.imageUri))
    }

    suspend fun seedDefaultCategories() = database.withTransaction {
        if (expenseDao.observeCategories().first().isEmpty()) {
            listOf(com.trackermaster.core.data.R.string.money_category_food,
                com.trackermaster.core.data.R.string.money_category_transport,
                com.trackermaster.core.data.R.string.money_category_shopping,
                com.trackermaster.core.data.R.string.money_category_bills,
                com.trackermaster.core.data.R.string.money_category_salary).forEachIndexed { i, resource ->
                expenseDao.insertCategory(
                    com.trackermaster.core.database.entity.ExpenseCategoryEntity(
                        name = context.getString(resource),
                        isIncome = i == 4,
                        colorArgb = listOf(0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFF4DB6AC)[i].toInt(),
                    )
                )
            }
        }
        if (expenseDao.observeAccounts().first().isEmpty()) {
            moneyRepository.createAccount(context.getString(com.trackermaster.core.data.R.string.money_default_account),
                AccountType.CHECKING, Money(0, "COP"), 2)
        }
    }
}

private fun ExpenseCategory.toEntity() = com.trackermaster.core.database.entity.ExpenseCategoryEntity(
    id, name, isIncome, colorArgb
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
                startedAtEpoch = System.currentTimeMillis(),
            )
        )

    suspend fun completeSession(id: Long, actualSeconds: Int) {
        val sessions = focusDao.observeSessions().first()
        val s = sessions.find { it.id == id } ?: return
        focusDao.update(
            s.copy(
                actualSeconds = actualSeconds,
                actualDurationMs = actualSeconds.coerceAtLeast(0)*1000L,
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
                actualDurationMs = actualSeconds.coerceAtLeast(0)*1000L,
                completed = completed,
                completedAtEpoch = LocalDateTime.now().toEpochMilli(),
            )
        )
    }

    fun weeklyFocusScore(): Flow<Int> = observeSessions().map { sessions ->
        val weekAgo = LocalDateTime.now().minusDays(7)
        HabitScheduleLogic.calculateFocusScore(
            sessions.filter { (it.completedAt ?: it.startedAt)?.isAfter(weekAgo) == true }
        )
    }
}

@Singleton
class DashboardRepository @Inject constructor(
    private val habitRepository: HabitRepository,
    private val moodRepository: MoodRepository,
    private val expenseRepository: ExpenseRepository,
    private val focusRepository: FocusRepository,
    private val journalRepository: JournalRepository,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeSummary(): Flow<DashboardSummary> = habitRepository.observeDay().flatMapLatest { today ->
        combine(
            habitRepository.observeTodayHabits(today),
            moodRepository.observeEntries(),
            expenseRepository.balancesByCurrency(),
            focusRepository.weeklyFocusScore(),
            journalRepository.writingStreak(),
        ) { todayHabits, moods, balance, focusScore, journalStreak ->
            DashboardSummary(
                habitsDueToday = todayHabits.size,
                habitsCompletedToday = todayHabits.count { (_, log) -> log != null && HabitScheduleLogic.isLogComplete(log) },
                latestMoodLevel = moods.firstOrNull()?.level,
                totalBalance = balance.firstOrNull { it.currencyCode == "COP" }?.decimal?.toDouble() ?: 0.0,
                balancesByCurrency = balance,
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
        taskDao.update(task.copy(completed = !task.completed,
            completedAtEpoch = if (!task.completed) System.currentTimeMillis() else null).toEntity())

    suspend fun updateTask(task: Task) =
        taskDao.update(task.toEntity())

    suspend fun deleteTask(task: Task) =
        taskDao.delete(task.toEntity())
}

