package com.trackermaster.core.data.unlock

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class UnlockCapabilities(val automaticAllowed: Boolean,val notificationsAllowed: Boolean,val batteryExempt: Boolean)

fun unlockCapabilities(context: Context): UnlockCapabilities {
    val channel=context.getSystemService(NotificationManager::class.java).getNotificationChannel("unlock_monitor")
    return UnlockCapabilities(Build.VERSION.SDK_INT<29 || Settings.canDrawOverlays(context),
        NotificationManagerCompat.from(context).areNotificationsEnabled() && channel?.importance!=NotificationManager.IMPORTANCE_NONE,
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
}
