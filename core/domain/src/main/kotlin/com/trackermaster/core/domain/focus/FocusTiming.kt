package com.trackermaster.core.domain.focus

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class FocusRunStatus { RUNNING, PAUSED, COMPLETED, STOPPED }

object FocusTimingRules {
    fun delta(effectiveMs: Long, plannedMs: Long, lastElapsedMs: Long, nowElapsedMs: Long,
        sameBoot: Boolean, status: FocusRunStatus): Long {
        require(effectiveMs >= 0 && plannedMs > 0)
        if (!sameBoot || status != FocusRunStatus.RUNNING) return 0
        return (nowElapsedMs - lastElapsedMs).coerceAtLeast(0).coerceAtMost((plannedMs-effectiveMs).coerceAtLeast(0))
    }

    /** Attribute each effective millisecond to the app's local day, including DST boundaries. */
    fun splitDays(endWallMs: Long, effectiveDeltaMs: Long, zone: ZoneId): Map<LocalDate,Long> {
        require(effectiveDeltaMs >= 0)
        val result = linkedMapOf<LocalDate,Long>()
        var cursor = endWallMs-effectiveDeltaMs
        while (cursor < endWallMs) {
            val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val next = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(next,endWallMs)
            check(end > cursor)
            result[day] = (result[day] ?: 0L) + end-cursor
            cursor = end
        }
        return result
    }
}
