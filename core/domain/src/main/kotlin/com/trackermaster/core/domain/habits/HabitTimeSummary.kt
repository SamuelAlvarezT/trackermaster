package com.trackermaster.core.domain.habits

import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.HabitLog
import com.trackermaster.core.domain.model.ProgressType

data class HabitTimeSummary(val plannedMs: Long = 0, val remainingMs: Long = 0)

/** Inputs are the habits scheduled on the requested day and their existing daily logs. */
fun habitTimeSummary(rows: List<Pair<Habit, HabitLog?>>): HabitTimeSummary {
    var planned = 0L
    var remaining = 0L
    for ((habit, log) in rows) {
        if (habit.progressType !in listOf(ProgressType.TIMER, ProgressType.STOPWATCH) || habit.targetDurationMs <= 0) continue
        val target = habit.targetDurationMs
        planned = Math.addExact(planned, target)
        val manual = log?.completed == true && log.completionSource != "measured"
        val deficit = if (manual) 0L else (target - (log?.durationMs ?: 0L).coerceAtLeast(0L)).coerceAtLeast(0L)
        remaining = Math.addExact(remaining, deficit)
    }
    return HabitTimeSummary(planned, remaining)
}

fun formatHabitMinutes(milliseconds: Long): String {
    val minutes = (milliseconds.coerceAtLeast(0) + 59_999) / 60_000
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
