package com.trackermaster.core.data.money

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyPreferencesTest {
    @Test fun displayPrivacyAndLastFundingPersistTogetherAndNotifyOnlyThisApp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val first = MoneyPreferencesRepository(context)
        first.setAppearance(true, true)
        first.rememberFunding(7, 8)
        first.setPrivacy(true)
        val recreated = MoneyPreferencesRepository(context).preferences.first()
        assertTrue(recreated.detailed); assertTrue(recreated.sections); assertTrue(recreated.hideAmounts)
        assertEquals(7L, recreated.lastAccountId); assertEquals(8L, recreated.lastPocketId)
        val broadcast = shadowOf(context).broadcastIntents.last { it.action == MoneyPreferencesRepository.ACTION_CHANGED }
        assertEquals(context.packageName, broadcast.`package`)
        assertTrue(broadcast.getBooleanExtra(MoneyPreferencesRepository.EXTRA_HIDE, false))
        first.rememberFunding(7, null)
        assertNull(first.preferences.first().lastPocketId)
        assertTrue(first.preferences.first().hideAmounts)
    }
}
