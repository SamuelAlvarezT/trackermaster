package com.trackermaster.core.data.unlock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.domain.unlock.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.unlockSettings by preferencesDataStore("unlock_launch_settings")
private val Context.unlockRuntime by preferencesDataStore("unlock_launch_runtime")

data class UnlockLaunchSettings(val enabled: Boolean=false,val intervalMinutes: Int=30,val rotation: Int=0,
    val localeTag: String="en",val lastOutcome: String="NONE") {
    val destination get()=UnlockLaunchRules.destinations[Math.floorMod(rotation,6)]
}
data class UnlockDelivery(val token: String,val destination: String,val issuedElapsedMs: Long)

object UnlockMonitorSignals {
    val running=MutableStateFlow(false)
    val error=MutableStateFlow(false)
}

@Singleton
class UnlockLaunchStore @Inject constructor(@ApplicationContext private val context: Context,private val clock: ProgressClock) {
    private object Key {
        val enabled=booleanPreferencesKey("enabled");val interval=intPreferencesKey("interval_minutes")
        val rotation=intPreferencesKey("rotation");val locale=stringPreferencesKey("locale")
        val acknowledged=stringPreferencesKey("last_acknowledged");val outcome=stringPreferencesKey("last_outcome")
        val cycle=stringPreferencesKey("cycle");val off=longPreferencesKey("screen_off_elapsed")
        val boot=intPreferencesKey("boot_count");val token=stringPreferencesKey("delivery_token")
        val destination=stringPreferencesKey("destination");val issued=longPreferencesKey("issued_elapsed")
        val visible=booleanPreferencesKey("delivery_visible")
    }
    val settings=context.unlockSettings.data.map { p -> UnlockLaunchSettings(p[Key.enabled] ?: false,
        (p[Key.interval] ?: 30).takeIf { it in UnlockLaunchRules.intervalsMinutes } ?: 30,p[Key.rotation] ?: 0,
        p[Key.locale] ?: "en",p[Key.outcome] ?: "NONE") }

    suspend fun configure(enabled: Boolean,interval: Int,locale: String): Unit = lock.withLock {
        require(interval in UnlockLaunchRules.intervalsMinutes)
        context.unlockSettings.edit { it[Key.enabled]=enabled;it[Key.interval]=interval;it[Key.locale]=locale;it[Key.outcome]="NONE" }
        context.unlockRuntime.edit { it.clear() }
    }
    suspend fun clearRuntime() = lock.withLock { context.unlockRuntime.edit { it.clear() };Unit }
    suspend fun screenOff(eventElapsedMs: Long=clock.elapsedMs()): Unit = lock.withLock {
        if(!settings.first().enabled) return@withLock
        context.unlockRuntime.edit { p ->
            // A lock-screen glance can cause another SCREEN_OFF. It keeps the original period.
            if(p[Key.cycle]==null || p[Key.boot]!=clock.bootCount()) {
                p.clear();p[Key.cycle]=UUID.randomUUID().toString();p[Key.off]=eventElapsedMs;p[Key.boot]=clock.bootCount()
            }
        }
    }
    suspend fun unlocked(eventElapsedMs: Long=clock.elapsedMs()): UnlockDelivery? = lock.withLock {
        val config=settings.first()
        var delivery: UnlockDelivery?=null
        context.unlockRuntime.edit { p ->
            val cycle=p[Key.cycle]?.let { UnlockCycle(it,p[Key.off] ?: eventElapsedMs,p[Key.boot] ?: -1) }
            if(cycle==null) return@edit // Duplicate USER_PRESENT must not invalidate a delivery awaiting acknowledgement.
            val decision=UnlockLaunchRules.decide(cycle,eventElapsedMs,clock.bootCount(),config.intervalMinutes,config.rotation)
            p.clear() // Every unlock ends this period, even if it was shorter than the interval.
            if(config.enabled && decision.eligible) {
                val token=UUID.randomUUID().toString()
                p[Key.token]=token;p[Key.destination]=decision.destination;p[Key.issued]=eventElapsedMs
                p[Key.boot]=clock.bootCount();p[Key.visible]=false
                delivery=UnlockDelivery(token,decision.destination,eventElapsedMs)
            }
        }
        delivery
    }
    private fun valid(p: Preferences,token: String): Boolean = p[Key.token]==token && p[Key.boot]==clock.bootCount() &&
        clock.elapsedMs()-(p[Key.issued] ?: Long.MIN_VALUE) in 0..120_000

    suspend fun visible(token: String) = lock.withLock {
        context.unlockRuntime.edit { if(valid(it,token)) it[Key.visible]=true };Unit
    }
    suspend fun wasVisible(token: String): Boolean = context.unlockSettings.data.first()[Key.acknowledged]==token ||
        context.unlockRuntime.data.first().let { valid(it,token) && it[Key.visible]==true }
    suspend fun pending(token: String): Boolean = context.unlockRuntime.data.first().let { valid(it,token) }
    suspend fun acknowledge(token: String,destination: String): Boolean = lock.withLock {
        val pending=context.unlockRuntime.data.first()
        if(!valid(pending,token) || pending[Key.destination]!=destination || !settings.first().enabled) return@withLock false
        context.unlockSettings.edit { p ->
            if(p[Key.acknowledged]!=token) {
                p[Key.rotation]=UnlockLaunchRules.nextRotation(p[Key.rotation] ?: 0)
                p[Key.acknowledged]=token;p[Key.outcome]="OPENED"
            }
        }
        context.unlockRuntime.edit { it.clear() }
        true
    }
    suspend fun outcome(value: String) { context.unlockSettings.edit { it[Key.outcome]=value } }
    suspend fun locale(tag: String) { context.unlockSettings.edit { it[Key.locale]=tag } }
    companion object {
        private val lock=Mutex()
        const val EXTRA_TOKEN="unlock_delivery_token"
    }
}

class UnlockMonitorController @Inject constructor(@ApplicationContext private val context: Context) {
    fun start() {
        // The existing lock-screen panel must not wake/show over the lock for this feature.
        context.stopService(Intent().setComponent(ComponentName(context.packageName,"com.trackermaster.app.lockscreen.DefumblrLockScreenService")))
        try {
            context.startForegroundService(Intent().setComponent(ComponentName(context.packageName,"com.trackermaster.app.unlock.UnlockMonitorService")))
            UnlockMonitorSignals.error.value=false
        } catch(_: Exception) { UnlockMonitorSignals.error.value=true }
    }
    fun clearFallback() { context.getSystemService(android.app.NotificationManager::class.java).cancel(OPEN_NOTIFICATION_ID) }
    fun stop() {
        context.stopService(Intent().setComponent(ComponentName(context.packageName,"com.trackermaster.app.unlock.UnlockMonitorService")))
        clearFallback()
    }
    companion object { const val OPEN_NOTIFICATION_ID=2081 }
}
