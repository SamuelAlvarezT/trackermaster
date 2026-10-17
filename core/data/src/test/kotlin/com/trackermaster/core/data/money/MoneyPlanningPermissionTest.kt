package com.trackermaster.core.data.money

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[33])
class MoneyPlanningPermissionTest {
    @Test fun notificationIdentityIsOpaqueStableAndSeparatedByRecordAndDate(){
        val day=java.time.LocalDate.of(2026,10,9)
        val tag=MoneyPlanningWorker.notificationTag("budget","42",day)
        assertTrue(Regex("money:[a-f0-9]{64}").matches(tag))
        assertEquals(tag,MoneyPlanningWorker.notificationTag("budget","42",day))
        assertNotEquals(tag,MoneyPlanningWorker.notificationTag("budget","43",day))
        assertNotEquals(tag,MoneyPlanningWorker.notificationTag("budget","42",day.plusDays(1)))
        assertNotEquals(tag,MoneyPlanningWorker.notificationTag("plan","42",day))
    }
    @Test fun deniedRuntimePermissionIsDetectedBeforeAnyNotificationAttempt(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(MoneyPlanningWorker.notificationsAllowed(context))
    }
    @Test fun blockedChannelIsDetectedEvenWhenRuntimePermissionIsGranted(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(MoneyPlanningWorker.CHANNEL,"Test",NotificationManager.IMPORTANCE_NONE))
        assertFalse(MoneyPlanningWorker.notificationsAllowed(context))
    }
    @Test fun grantedPermissionAndEnabledChannelAllowReminderDelivery(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager=context.getSystemService(NotificationManager::class.java)
        Shadows.shadowOf(manager).setNotificationsEnabled(true)
        manager.createNotificationChannel(NotificationChannel(MoneyPlanningWorker.CHANNEL,"Test",NotificationManager.IMPORTANCE_DEFAULT))
        assertTrue(MoneyPlanningWorker.notificationsAllowed(context))
    }
}
