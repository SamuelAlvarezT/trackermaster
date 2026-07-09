package com.trackermaster.core.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class ProgressType { CHECKBOX, COUNTER, TIMER, STOPWATCH, CHECKLIST }

enum class ScheduleType { DAILY, WEEKLY, MONTHLY, CUSTOM, SPECIFIC_DAYS, YEARLY }

enum class AccountType { CHECKING, SAVINGS, CARD, CASH }

enum class TransactionType { INCOME, EXPENSE }

enum class ThemeMode { LIGHT, DARK, OLED }

enum class BackupFrequency { APP_START, DAILY, WEEKLY, MONTHLY }

enum class FocusCategoryType {
    WORK, STUDY, CREATIVE, CODING, READING, WRITING, EXERCISE, OTHER
}

data class Habit(
    val id: Long = 0,
    val name: String,
    val progressType: ProgressType,
    val scheduleType: ScheduleType,
    val scheduleDaysBitmask: Int = 0,
    val targetCount: Int = 1,
    val targetDurationMs: Long = 0,
    val checklistItems: List<String> = emptyList(),
    val colorArgb: Int = 0xFF6750A4.toInt(),
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val archived: Boolean = false,
    val description: String = "",
    val sortOrder: Int = 0,
)

data class HabitLog(
    val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val value: Int = 0,
    val durationMs: Long = 0,
    val checklistState: Map<String, Boolean> = emptyMap(),
    val completed: Boolean = false,
)

data class MoodLevel(
    val level: Int,
    val label: String,
    val emoji: String,
    val colorArgb: Int,
)

data class MoodEntry(
    val id: Long = 0,
    val timestamp: LocalDateTime,
    val level: Int,
    val note: String = "",
    val tagIds: List<Long> = emptyList(),
)

data class MoodTag(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int = 0xFF7C4DFF.toInt(),
)

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val initialBalance: Double = 0.0,
)

data class ExpenseCategory(
    val id: Long = 0,
    val name: String,
    val isIncome: Boolean = false,
    val colorArgb: Int = 0xFF00BFA5.toInt(),
)

data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val date: LocalDate,
    val note: String = "",
    val imageUri: String? = null,
)

data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val limitAmount: Double,
    val period: String = "MONTHLY",
    val alertThresholdPercent: Int = 80,
)

data class FocusSession(
    val id: Long = 0,
    val category: FocusCategoryType,
    val plannedMinutes: Int,
    val actualSeconds: Int = 0,
    val completedAt: LocalDateTime? = null,
    val completed: Boolean = false,
)

data class JournalEntry(
    val id: Long = 0,
    val title: String = "",
    val richTextHtml: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val moodLevel: Int? = null,
    val attachments: List<Attachment> = emptyList(),
)

data class Attachment(
    val id: Long = 0,
    val entryId: Long = 0,
    val path: String,
    val mimeType: String,
)

data class Achievement(
    val id: Long = 0,
    val type: String,
    val title: String,
    val unlockedAt: LocalDateTime,
    val relatedId: Long? = null,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentIndex: Int = 0,
    val localeTag: String = "en",
    val biometricEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val defumblrEnabled: Boolean = true,
    val lockscreenWidgetsEnabled: Boolean = true,
    val newInterfaceEnabled: Boolean = false,
    val backupFrequency: BackupFrequency = BackupFrequency.APP_START,
    val lastBackupAtMillis: Long = 0L,
)

data class Task(
    val id: Long = 0,
    val title: String,
    val completed: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val subtasks: List<SubTask> = emptyList(),
)

data class SubTask(
    val title: String,
    val completed: Boolean = false,
)

data class StreakInfo(
    val current: Int,
    val best: Int,
)

data class MoodHabitCorrelation(
    val habitId: Long,
    val habitName: String,
    val avgMoodWithHabit: Double,
    val avgMoodWithoutHabit: Double,
)

data class DashboardSummary(
    val habitsDueToday: Int,
    val habitsCompletedToday: Int,
    val latestMoodLevel: Int?,
    val totalBalance: Double,
    val focusScoreWeek: Int,
    val journalStreak: Int,
)
