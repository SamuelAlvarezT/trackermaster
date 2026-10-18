package com.trackermaster.core.domain

import com.trackermaster.core.domain.unlock.*
import org.junit.Assert.*
import org.junit.Test

class UnlockLaunchRulesTest {
    private val cycle=UnlockCycle("one-lock",1000,4)
    @Test fun everySupportedIntervalHasAnExactBoundary() {
        for(minutes in UnlockLaunchRules.intervalsMinutes) {
            assertFalse(UnlockLaunchRules.decide(cycle,1000+minutes*60_000L-1,4,minutes,0).eligible)
            assertTrue(UnlockLaunchRules.decide(cycle,1000+minutes*60_000L,4,minutes,0).eligible)
        }
    }
    @Test fun manyIntervalsStillProduceJustOneDestination() {
        assertEquals("home",UnlockLaunchRules.decide(cycle,1000+24*60*60_000L,4,30,0).destination)
    }
    @Test fun consumedCycleCannotOpenAgain() { assertFalse(UnlockLaunchRules.decide(null,99_000_000,4,30,0).eligible) }
    @Test fun aRebootDoesNotInterpretTheOldMonotonicClockAsElapsedTime() {
        assertFalse(UnlockLaunchRules.decide(cycle,99_000_000,5,30,0).eligible)
    }
    @Test fun timeBeforeScreenOffNeverQualifies() { assertFalse(UnlockLaunchRules.decide(cycle,0,4,30,0).eligible) }
    @Test fun rotationIsPersistentOrderedAndWraps() {
        assertEquals(listOf("home","habits","tasks","expense","focus","journal"),UnlockLaunchRules.destinations)
        assertEquals(0,UnlockLaunchRules.nextRotation(5));assertEquals(3,UnlockLaunchRules.nextRotation(2))
    }
}
