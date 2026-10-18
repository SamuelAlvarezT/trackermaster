package com.trackermaster.app.focus

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.trackermaster.app.MainActivity
import com.trackermaster.core.data.progress.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.focus.FocusRunStatus
import com.trackermaster.core.domain.model.FocusCategoryType
import com.trackermaster.core.ui.R as P8R
import com.trackermaster.core.widgets.WidgetRefresh
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class FocusTimerService : Service() {
    @Inject lateinit var repository: FocusProgressRepository
    @Inject lateinit var settings: SettingsRepository
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val commands=Mutex()
    private var ticker: Job?=null
    private var locale=java.util.Locale.getDefault()
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,getString(P8R.string.p8_focus_notification),NotificationManager.IMPORTANCE_LOW))
        startForeground(FOCUS_NOTIFICATION_ID,notification(0))
        FocusTimerSignals.running.value=true
        scope.launch {
            combine(repository.observeRuntime(),settings.settings.map { it.localeTag }.distinctUntilChanged()) { state,tag -> state to tag }
                .collect { (state,tag) ->
                locale=java.util.Locale.forLanguageTag(tag)
                if(android.os.Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(FOCUS_NOTIFICATION_ID,notification(state.remainingSeconds))
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        scope.launch {
            commands.withLock {
                try {
                    ticker?.cancelAndJoin()
                    when(intent?.action) {
                        FocusTimerController.START -> if(!repository.checkpoint().active) repository.start(
                            FocusCategoryType.valueOf(intent.getStringExtra("category") ?: FocusCategoryType.WORK.name),
                            intent.getIntExtra("minutes",25),intent.takeIf { it.hasExtra("habit_id") }?.getLongExtra("habit_id",0))
                        FocusTimerController.PAUSE -> repository.pause()
                        FocusTimerController.RESUME -> repository.resume()
                        FocusTimerController.STOP -> repository.stop()
                        else -> repository.checkpoint()
                    }
                    FocusTimerSignals.error.value=false
                    val state=repository.checkpoint()
                    if(state.status==FocusRunStatus.RUNNING && state.sessionId>0) ticker=scope.launch {
                        var ticks=0
                        while(isActive) {
                            val current=repository.checkpoint()
                            if(ticks++%12==0) runCatching { WidgetRefresh.refreshAll(this@FocusTimerService) }
                            if(current.status!=FocusRunStatus.RUNNING) { stopSelf();break }
                            delay(5000)
                        }
                    } else { runCatching { WidgetRefresh.refreshAll(this@FocusTimerService) };stopSelf() }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { FocusTimerSignals.error.value=true;stopSelf() }
            }
        }
        return START_STICKY
    }
    private fun notification(seconds: Int): Notification {
        val localized=createConfigurationContext(android.content.res.Configuration(resources.configuration).apply { setLocale(this@FocusTimerService.locale) })
        val open=PendingIntent.getActivity(this,51,Intent(this,MainActivity::class.java).putExtra("tab","focus"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause=PendingIntent.getService(this,52,Intent(this,FocusTimerService::class.java).setAction(FocusTimerController.PAUSE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(localized.getString(P8R.string.p8_focus_notification))
            .setContentText(localized.getString(P8R.string.p8_focus_running,String.format(locale,"%02d:%02d",seconds/60,seconds%60)))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause,localized.getString(P8R.string.p8_focus_pause),pause).build()
    }
    override fun onDestroy() {
        FocusTimerSignals.running.value=false
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object { const val FOCUS_NOTIFICATION_ID=2001;private const val CHANNEL="focus_timer" }
}
