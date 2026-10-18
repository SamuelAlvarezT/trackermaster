package com.trackermaster.app.unlock

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trackermaster.app.MainActivity
import com.trackermaster.core.data.unlock.*
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.ui.R as P8R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@AndroidEntryPoint
class UnlockMonitorService: Service() {
    @Inject lateinit var store: UnlockLaunchStore
    @Inject lateinit var clock: ProgressClock
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val keyguard get()=getSystemService(KeyguardManager::class.java)
    private val power get()=getSystemService(PowerManager::class.java)
    private val receiver=object: BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            val eventElapsed=SystemClock.elapsedRealtime()
            val pending=goAsync()
            scope.launch {
                try {
                    when(intent.action) {
                        Intent.ACTION_SCREEN_OFF -> store.screenOff(eventElapsed)
                        Intent.ACTION_USER_PRESENT -> {
                            // USER_PRESENT ends the lock period even if keyguard reports a stale state.
                            val delivery=store.unlocked(eventElapsed)
                            repeat(5) { if(keyguard.isKeyguardLocked) delay(50) }
                            delivery?.let { deliver(it) }
                        }
                    }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { UnlockMonitorSignals.error.value=true }
                finally { pending.finish() }
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,getString(P8R.string.p8_unlock_notice),NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,80,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(MONITOR_ID,NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(P8R.string.p8_unlock_notice)).setContentText(getString(P8R.string.p8_unlock_notice_body))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build())
        UnlockMonitorSignals.running.value=true
        val filter=IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_USER_PRESENT) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,RECEIVER_NOT_EXPORTED)
        else { @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(receiver,filter) }
        scope.launch {
            // A restarted monitor must never interrupt an already unlocked phone.
            if(power.isInteractive && !keyguard.isKeyguardLocked) store.clearRuntime()
            store.settings.collect { config ->
                if(!config.enabled) stopSelf() else {
                    val localized=createConfigurationContext(android.content.res.Configuration(resources.configuration).apply {
                        setLocale(java.util.Locale.forLanguageTag(config.localeTag))
                    })
                    manager.createNotificationChannel(NotificationChannel(CHANNEL,localized.getString(P8R.string.p8_unlock_notice),NotificationManager.IMPORTANCE_LOW))
                    if(Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(MONITOR_ID,NotificationCompat.Builder(this@UnlockMonitorService,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle(localized.getString(P8R.string.p8_unlock_notice)).setContentText(localized.getString(P8R.string.p8_unlock_notice_body))
                        .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build())
                    }
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int)=START_STICKY
    private suspend fun deliver(value: UnlockDelivery) {
        if(!store.pending(value.token)) return
        val eligibleNow=power.isInteractive && !keyguard.isKeyguardLocked && clock.elapsedMs()-value.issuedElapsedMs in 0..2000
        val automatic=Build.VERSION.SDK_INT<29 || Settings.canDrawOverlays(this)
        if(!eligibleNow) {
            if(power.isInteractive && !keyguard.isKeyguardLocked) notifyToOpen(value,"BLOCKED") else store.outcome("BLOCKED")
            return
        }
        if(!automatic) { notifyToOpen(value,"FALLBACK");return }
        try {
            startActivity(Intent(this,MainActivity::class.java).apply {
                putExtra("tab",value.destination);putExtra(UnlockLaunchStore.EXTRA_TOKEN,value.token)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        } catch(_: Exception) { notifyToOpen(value,"BLOCKED");return }
        // Android can silently reject a launch. Only a resumed activity acknowledges visibility.
        scope.launch {
            delay(3000)
            if(store.pending(value.token) && !store.wasVisible(value.token) && power.isInteractive && !keyguard.isKeyguardLocked)
                notifyToOpen(value,"BLOCKED")
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun notifyToOpen(value: UnlockDelivery,outcome: String) {
        val config=store.settings.first()
        val localized=createConfigurationContext(android.content.res.Configuration(resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(config.localeTag))
        })
        if(!unlockCapabilities(this).notificationsAllowed) { store.outcome("NOTIFICATIONS_BLOCKED");return }
        val title=localized.getString(sectionString(value.destination))
        // A notification tap is manual and carries no automatic-delivery token.
        val open=PendingIntent.getActivity(this,81,Intent(this,MainActivity::class.java).putExtra("tab",value.destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(UnlockMonitorController.OPEN_NOTIFICATION_ID,NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle(localized.getString(P8R.string.p8_unlock_open,title))
            .setContentText(localized.getString(P8R.string.p8_unlock_fallback)).setContentIntent(open)
            .setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true).build())
        store.outcome(outcome)
    }
    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        UnlockMonitorSignals.running.value=false
        scope.cancel();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
    companion object { private const val CHANNEL="unlock_monitor";private const val MONITOR_ID=2080 }
}

private fun sectionString(route: String): Int=when(route) {
    "habits" -> P8R.string.p8_section_habits
    "tasks" -> P8R.string.p8_section_tasks
    "expense" -> P8R.string.p8_section_money
    "focus" -> P8R.string.p8_section_focus
    "journal" -> P8R.string.p8_section_journal
    else -> P8R.string.p8_section_home
}
