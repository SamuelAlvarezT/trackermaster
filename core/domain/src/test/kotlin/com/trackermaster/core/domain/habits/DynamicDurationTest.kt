package com.trackermaster.core.domain.habits

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicDurationTest {
    @Test fun adjustsNoProgressPartialAndCompleteWithMinuteRounding() {
        assertEquals(23L, DynamicDuration.next(30, 0, 1, 100, true).nextMinutes)
        assertEquals(26L, DynamicDuration.next(30, 450_000, 1, 100, true).nextMinutes)
        assertEquals(29L, DynamicDuration.next(30, 1_350_000, 1, 100, true).nextMinutes)
        assertEquals(32L, DynamicDuration.next(30, 1_800_000, 1, 100, true).nextMinutes)
    }
    @Test fun changedBoundsClampOnlyTheNextScheduledTarget() {
        assertEquals(25L, DynamicDuration.next(20, 0, 25, 60, true).nextMinutes)
        assertEquals(40L, DynamicDuration.next(60, 3_600_000, 10, 40, true).nextMinutes)
        assertEquals(20L, DynamicDuration.next(20, 0, 25, 60, false).nextMinutes)
    }
    @Test fun adjustmentIntensityScalesChangeBeforeRounding() {
        assertEquals(30L, DynamicDuration.next(30, 0, 1, 100, true, 0).nextMinutes)
        assertEquals(27L, DynamicDuration.next(30, 0, 1, 100, true, 50).nextMinutes)
        assertEquals(16L, DynamicDuration.next(30, 0, 1, 100, true, 200).nextMinutes)
    }
    @Test fun respectsBoundsAndSkipsUnscheduledDays() {
        assertEquals(25L, DynamicDuration.next(30, 0, 25, 31, true).nextMinutes)
        assertEquals(31L, DynamicDuration.next(30, 3_600_000, 25, 31, true).nextMinutes)
        assertEquals(30L, DynamicDuration.next(30, 0, 25, 31, false).nextMinutes)
    }
}
