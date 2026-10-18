package com.trackermaster.core.data.unlock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.domain.unlock.UnlockLaunchRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class UnlockLaunchStoreTest {
    @Test fun realDataStorePreservesLockPeriodAndOnlyAcknowledgedAutomaticDestinationsRotate()=runTest {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val clock=object: ProgressClock(context) {
            var elapsed=1000L;var boot=1
            override fun elapsedMs()=elapsed
            override fun bootCount()=boot
        }
        val store=UnlockLaunchStore(context,clock)
        store.configure(true,30,"es")
        val first=store.settings.first().rotation
        store.screenOff();clock.elapsed+=20*60_000
        store.screenOff() // Lock-screen glance must not reset the first SCREEN_OFF.
        clock.elapsed+=10*60_000
        val delivery=store.unlocked()!!
        assertEquals(UnlockLaunchRules.destinations[first%6],delivery.destination)
        assertNull(store.unlocked()) // no accumulation of multiple launches for one lock
        assertTrue(store.pending(delivery.token))
        assertEquals(first,store.settings.first().rotation)
        store.screenOff();clock.elapsed+=30*60_000
        val eligible=store.unlocked()!!
        assertFalse(store.acknowledge(eligible.token,"wrong-route"))
        store.visible(eligible.token);assertTrue(store.wasVisible(eligible.token))
        assertTrue(store.acknowledge(eligible.token,eligible.destination))
        assertFalse(store.acknowledge(eligible.token,eligible.destination))
        assertEquals((first+1)%6,store.settings.first().rotation)
        val recovered=UnlockLaunchStore(context,clock)
        assertEquals("es",recovered.settings.first().localeTag)
        assertEquals((first+1)%6,recovered.settings.first().rotation)
        repeat(5) {
            recovered.screenOff();clock.elapsed+=30*60_000
            val next=recovered.unlocked()!!
            assertTrue(recovered.acknowledge(next.token,next.destination))
        }
        assertEquals(first%6,recovered.settings.first().rotation)
        recovered.screenOff();clock.elapsed+=29*60_000
        assertNull(recovered.unlocked()) // short unlock ends that period
        clock.elapsed+=30*60_000;assertNull(recovered.unlocked()) // using the phone never opens it
        recovered.screenOff();clock.boot++;clock.elapsed=500
        assertNull(recovered.unlocked())
        assertTrue(recovered.settings.first().enabled)
        recovered.screenOff();clock.elapsed+=180*60_000
        val once=recovered.unlocked()!! // six intervals still produce one request
        assertEquals(first%6,recovered.settings.first().rotation)
        clock.elapsed+=120_001
        assertFalse(recovered.acknowledge(once.token,once.destination))
        recovered.configure(false,30,"es");recovered.screenOff();clock.elapsed+=30*60_000
        assertNull(recovered.unlocked())
    }
}
