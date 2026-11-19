package com.trackermaster.core.data.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FocusAlarmStoreTest {
    @Test fun selectionAndAlarmIdentitySurviveRecreationAndDismissal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = FocusAlarmStore(context)
        first.saveSelection("content://example/audio", false)
        first.beginStart(81720)
        assertEquals(81720L, first.state.first().pendingStartAfter)
        first.arm(81721)
        assertNull(first.state.first().pendingStartAfter)
        assertTrue(first.activate(81721))
        val recreated = FocusAlarmStore(context)
        assertEquals("content://example/audio", recreated.state.first().soundUri)
        assertFalse(recreated.state.first().vibrate)
        assertEquals(81721L, recreated.state.first().activeSessionId)
        recreated.dismiss(81721)
        recreated.arm(81721)
        assertFalse(recreated.activate(81721))
        recreated.arm(81722)
        assertTrue(recreated.activate(81722))
        recreated.dismiss(81722)
        recreated.beginStart(81722)
        recreated.dismiss(81723)
        assertNull(recreated.state.first().pendingStartAfter)
        assertFalse(recreated.activate(81723))
    }
}
