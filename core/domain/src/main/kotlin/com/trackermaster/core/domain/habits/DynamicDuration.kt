package com.trackermaster.core.domain.habits

import kotlin.math.roundToLong

data class DynamicGoal(val targetMinutes: Long, val previousTargetMinutes: Long?, val previousProgressMs: Long?, val reason: String)

/** Calculates the next scheduled day's target. A non-scheduled day leaves it unchanged. */
object DynamicDuration {
    data class Result(val nextMinutes: Long, val reason: Reason)
    enum class Reason { NOT_SCHEDULED, NO_PROGRESS, PARTIAL, COMPLETE }

    fun next(targetMinutes: Long, progressMillis: Long, minimumMinutes: Long, maximumMinutes: Long, scheduled: Boolean, adjustmentPercent: Int = 100): Result {
        require(minimumMinutes > 0 && maximumMinutes >= minimumMinutes)
        require(targetMinutes > 0)
        require(adjustmentPercent in 0..200)
        if (!scheduled) return Result(targetMinutes, Reason.NOT_SCHEDULED)
        val targetMillis = targetMinutes.toDouble() * 60_000.0
        val fraction = (progressMillis.coerceAtLeast(0).toDouble() / targetMillis).coerceIn(0.0, 1.0)
        val reason = when { fraction == 0.0 -> Reason.NO_PROGRESS; fraction < 1.0 -> Reason.PARTIAL; else -> Reason.COMPLETE }
        val factor = when (reason) {
            Reason.NO_PROGRESS -> 1.0 - 7.0 / 30.0
            Reason.PARTIAL -> 1.0 - (5.0 / 30.0) * (1.0 - fraction)
            Reason.COMPLETE -> 1.0 + 2.0 / 30.0
            Reason.NOT_SCHEDULED -> 1.0
        }
        val next = (targetMinutes * (1.0 + (factor - 1.0) * adjustmentPercent / 100.0)).roundToLong().coerceIn(minimumMinutes, maximumMinutes)
        return Result(next, reason)
    }
}
