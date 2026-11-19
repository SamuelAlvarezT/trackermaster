package com.trackermaster.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusSuggestionTest {
    @Test fun suggestsOnlyRemainingTimeAndRoundsUp() {
        assertEquals(20, suggestedFocusMinutes(30 * 60_000L, 10 * 60_000L))
        assertEquals(1, suggestedFocusMinutes(60_000L, 59_999L))
        assertEquals(30, suggestedFocusMinutes(30 * 60_000L, 0))
    }
    @Test fun completedOrMissingGoalDoesNotOverrideTheDraft() {
        assertNull(suggestedFocusMinutes(30 * 60_000L, 30 * 60_000L))
        assertNull(suggestedFocusMinutes(30 * 60_000L, 40 * 60_000L))
        assertNull(suggestedFocusMinutes(0, 0))
    }
}
