package com.trackermaster.core.domain

import com.trackermaster.core.domain.focus.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class FocusTimingRulesTest {
    @Test fun pauseAndRebootNeverAddTime() {
        assertEquals(0L,FocusTimingRules.delta(0,60_000,0,50_000,true,FocusRunStatus.PAUSED))
        assertEquals(0L,FocusTimingRules.delta(0,60_000,0,50_000,false,FocusRunStatus.RUNNING))
    }
    @Test fun runningTimeUsesMonotonicClockAndCapsAtSessionEnd() {
        assertEquals(10_000L,FocusTimingRules.delta(50_000,60_000,100,80_000,true,FocusRunStatus.RUNNING))
        assertEquals(0L,FocusTimingRules.delta(0,60_000,500,100,true,FocusRunStatus.RUNNING))
    }
    @Test fun millisecondsAreNotRoundedOrDuplicated() {
        assertEquals(1234L,FocusTimingRules.delta(0,60_000,100,1334,true,FocusRunStatus.RUNNING))
        assertEquals(0L,FocusTimingRules.delta(1234,60_000,1334,1334,true,FocusRunStatus.RUNNING))
    }
    @Test fun midnightIsSplitInTheActualLocalZone() {
        val zone=ZoneId.of("America/Bogota");val day=LocalDate.of(2026,10,18)
        val end=day.atTime(0,1).atZone(zone).toInstant().toEpochMilli()
        assertEquals(mapOf(day.minusDays(1) to 60_000L,day to 60_000L),FocusTimingRules.splitDays(end,120_000,zone))
    }
    @Test fun daylightSavingDayDoesNotInventAnHour() {
        val zone=ZoneId.of("America/New_York");val day=LocalDate.of(2026,3,8)
        val end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val split=FocusTimingRules.splitDays(end,86_400_000,zone)
        assertEquals(82_800_000L,split[day]);assertEquals(3_600_000L,split[day.minusDays(1)])
        assertEquals(86_400_000L,split.values.sum())
    }
}
