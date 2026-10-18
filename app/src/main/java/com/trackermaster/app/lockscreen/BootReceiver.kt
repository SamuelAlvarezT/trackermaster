package com.trackermaster.app.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trackermaster.core.data.unlock.*
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.data.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED) return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val store=UnlockLaunchStore(context.applicationContext,ProgressClock(context.applicationContext))
                store.clearRuntime() // Preserve settings/rotation, start a new period after reboot.
                if(store.settings.first().enabled) UnlockMonitorController(context.applicationContext).start()
                else if(SettingsRepository(context.applicationContext).settings.first().defumblrEnabled)
                    runCatching { context.startForegroundService(Intent(context,DefumblrLockScreenService::class.java)) }
            } catch(_: Exception) { UnlockMonitorSignals.error.value=true }
            finally { pending.finish() }
        }
    }
}
