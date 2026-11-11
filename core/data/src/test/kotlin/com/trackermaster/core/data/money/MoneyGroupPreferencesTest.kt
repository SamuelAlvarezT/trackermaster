package com.trackermaster.core.data.money

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyGroupPreferencesTest {
    @Test fun rulesPersistIndependently()=runTest {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repository=MoneyPreferencesRepository(context)
        try {
            repository.setGroupRules(true,null)
            repository.setGroupRules(null,true)
            val restored=MoneyPreferencesRepository(context).preferences.first()
            assertTrue(restored.groupSameDate);assertTrue(restored.groupSameAccount)
            repository.setGroupRules(false,null)
            val updated=repository.preferences.first()
            assertFalse(updated.groupSameDate);assertTrue(updated.groupSameAccount)
        } finally { repository.setGroupRules(false,false) }
    }
}
