package com.trackermaster.core.data.mapper

import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private fun encodeList(items: List<String>) = items.joinToString("\u001E")
private fun decodeList(raw: String): List<String> = if (raw.isBlank()) emptyList() else raw.split("\u001E")
private fun encodeLongList(items: List<Long>) = items.joinToString(",")
private fun decodeLongList(raw: String): List<Long> = if (raw.isBlank()) emptyList() else raw.split(",").mapNotNull { it.toLongOrNull() }
private fun encodeMap(map: Map<String, Boolean>) = map.entries.joinToString("\u001E") { "${it.key}=${it.value}" }
private fun decodeMap(raw: String): Map<String, Boolean> = if (raw.isBlank()) emptyMap() else raw.split("\u001E").mapNotNull {
    val p = it.split("="); if (p.size == 2) p[0] to p[1].toBoolean() else null
}.toMap()

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.toEpochMilli(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun LocalDateTime.toEpochMilli(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    progressType = ProgressType.valueOf(progressType),
    scheduleType = ScheduleType.valueOf(scheduleType),
    scheduleDaysBitmask = scheduleDaysBitmask,
    targetCount = targetCount,
    targetDurationMs = targetDurationMs,
    checklistItems = decodeList(checklistItemsJson),
    colorArgb = colorArgb,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    archived = archived,
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    progressType = progressType.name,
    scheduleType = scheduleType.name,
    scheduleDaysBitmask = scheduleDaysBitmask,
    targetCount = targetCount,
    targetDurationMs = targetDurationMs,
    checklistItemsJson = encodeList(checklistItems),
    colorArgb = colorArgb,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    archived = archived,
)

fun HabitLogEntity.toDomain(): HabitLog = HabitLog(
    id = id,
    habitId = habitId,
    date = dateEpoch.toLocalDate(),
    value = value,
    durationMs = durationMs,
    checklistState = decodeMap(checklistStateJson),
    completed = completed,
)

fun HabitLog.toEntity(): HabitLogEntity = HabitLogEntity(
    id = id,
    habitId = habitId,
    dateEpoch = date.toEpochMilli(),
    value = value,
    durationMs = durationMs,
    checklistStateJson = encodeMap(checklistState),
    completed = completed,
)

fun MoodEntryEntity.toDomain(): MoodEntry = MoodEntry(
    id = id,
    timestamp = timestampEpoch.toLocalDateTime(),
    level = level,
    note = note,
    tagIds = decodeLongList(tagIdsJson),
)

fun MoodTagEntity.toDomain(): MoodTag = MoodTag(id, name, colorArgb)

fun AccountEntity.toDomain(): Account = Account(id, name, AccountType.valueOf(type), currencyCode, initialBalance)

fun ExpenseCategoryEntity.toDomain(): ExpenseCategory = ExpenseCategory(id, name, isIncome, colorArgb)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id, accountId, amount, TransactionType.valueOf(type), categoryId, dateEpoch.toLocalDate(), note
)

fun BudgetEntity.toDomain(): Budget = Budget(id, categoryId, limitAmount, period, alertThresholdPercent)

fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
    id, FocusCategoryType.valueOf(category), plannedMinutes, actualSeconds,
    completedAtEpoch?.toLocalDateTime(), completed
)

fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    id, title, richTextHtml, createdAtEpoch.toLocalDateTime(), updatedAtEpoch.toLocalDateTime()
)

fun AchievementEntity.toDomain(): Achievement = Achievement(
    id, type, title, unlockedAtEpoch.toLocalDateTime(), relatedId
)
