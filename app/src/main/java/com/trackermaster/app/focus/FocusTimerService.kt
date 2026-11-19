package com.trackermaster.app.focus

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.PowerManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.trackermaster.app.MainActivity
import com.trackermaster.core.data.progress.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.data.repository.HabitRepository
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
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var alarmStore: FocusAlarmStore
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val commands=Mutex()
    private val alarmMutex=Mutex()
    private var ticker: Job?=null
    @Volatile private var destroying = false
    private var player: MediaPlayer? = null
    private var soundingSessionId = 0L
    private val timerWakeLock by lazy { (getSystemService(POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Trackermaster:FocusTimer") }
    @Synchronized
    private fun keepTimerAwake(awake: Boolean) {
        if (awake && !destroying && !timerWakeLock.isHeld) timerWakeLock.acquire()
        if (!awake && timerWakeLock.isHeld) timerWakeLock.release()
    }
    private var locale=java.util.Locale.getDefault()
    private var notificationHabitId: Long? = null
    private var notificationHabitName: String? = null
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,getString(P8R.string.p8_focus_notification),NotificationManager.IMPORTANCE_LOW))
        startForeground(FOCUS_NOTIFICATION_ID,notification(FocusRuntimeState()))
        FocusTimerSignals.running.value=true
        scope.launch {
            combine(repository.observeRuntime(),settings.settings.map { it.localeTag }.distinctUntilChanged(), alarmStore.state) { state,tag,alarm -> Triple(state,tag,alarm) }
                .collect { (state,tag,alarm) ->
                locale=java.util.Locale.forLanguageTag(tag)
                if (notificationHabitId != state.habitId) {
                    notificationHabitId = state.habitId
                    notificationHabitName = state.habitId?.let { habits.getHabit(it)?.name }
                }
                if(android.os.Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(FOCUS_NOTIFICATION_ID,notification(state, alarm.activeSessionId == state.sessionId && state.sessionId > 0))
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
                        FocusTimerController.START -> {
                            val alarm = alarmStore.state.first()
                            check(alarm.activeSessionId == 0L) { "Alarm is still active" }
                            val previous = repository.checkpoint()
                            if (previous.status == FocusRunStatus.COMPLETED && alarm.armedSessionId == previous.sessionId) {
                                startAlarm(previous)
                                return@withLock
                            }
                            if (!previous.active) {
                                alarmStore.beginStart(previous.sessionId)
                                val id = repository.start(FocusCategoryType.valueOf(intent.getStringExtra("category") ?: FocusCategoryType.WORK.name),
                                    intent.getIntExtra("minutes",25),intent.takeIf { it.hasExtra("habit_id") }?.getLongExtra("habit_id",0))
                                alarmStore.arm(id)
                            }
                        }
                        FocusTimerController.PAUSE -> repository.pause()
                        FocusTimerController.RESUME -> repository.resume()
                        FocusTimerController.STOP -> {
                            val result = repository.stop()
                            if (result.status == FocusRunStatus.STOPPED) alarmStore.dismiss(result.sessionId)
                        }
                        FocusTimerController.DISMISS_ALARM -> {
                            val current = repository.checkpoint()
                            alarmMutex.withLock { alarmStore.dismiss(current.sessionId); stopAlarm() }
                            stopSelf()
                            return@withLock
                        }
                        else -> repository.checkpoint()
                    }
                    FocusTimerSignals.error.value=false
                    val state=repository.checkpoint()
                    val pendingStart = alarmStore.state.first().pendingStartAfter
                    if (pendingStart != null && state.sessionId > pendingStart) alarmStore.arm(state.sessionId)
                    keepTimerAwake(state.status == FocusRunStatus.RUNNING)
                    when (state.status) {
                        FocusRunStatus.RUNNING -> {
                            if (alarmStore.state.first().armedSessionId != state.sessionId) alarmStore.arm(state.sessionId)
                            ticker=scope.launch {
                                var ticks=0
                                while(isActive) {
                                    val current=repository.checkpoint()
                                    if(ticks++%12==0) runCatching { WidgetRefresh.refreshAll(this@FocusTimerService) }
                                    if(current.status==FocusRunStatus.COMPLETED) { startAlarm(current); keepTimerAwake(false); break }
                                    if(current.status!=FocusRunStatus.RUNNING) { keepTimerAwake(false); if(current.status!=FocusRunStatus.PAUSED) stopSelf(); break }
                                    delay(1000)
                                }
                            }
                        }
                        FocusRunStatus.COMPLETED -> if (!startAlarm(state)) stopSelf()
                        FocusRunStatus.PAUSED -> runCatching { WidgetRefresh.refreshAll(this@FocusTimerService) }
                        else -> { runCatching { WidgetRefresh.refreshAll(this@FocusTimerService) }; stopSelf() }
                    }
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { FocusTimerSignals.error.value=true;stopSelf() }
            }
        }
        return START_STICKY
    }
    private suspend fun startAlarm(state: FocusRuntimeState): Boolean = alarmMutex.withLock {
        if (!alarmStore.activate(state.sessionId)) return@withLock false
        if (soundingSessionId == state.sessionId) return@withLock true
        stopAlarm()
        val alarm = alarmStore.state.first()
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        val selected = alarm.soundUri?.let(Uri::parse) ?: defaultUri
        fun create(uri: Uri): MediaPlayer = MediaPlayer().apply {
            setWakeMode(this@FocusTimerService, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            try { setDataSource(this@FocusTimerService, uri); isLooping = true; prepare(); start() }
            catch (error: Exception) { release(); throw error }
        }
        player = try { create(selected) } catch (_: Exception) {
            alarmStore.recordFallback(state.sessionId)
            runCatching { create(defaultUri) }.getOrElse {
                runCatching { create(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI) }.getOrNull()
            }
        }
        if (destroying) { stopAlarm(); return@withLock false }
        if (alarm.vibrate) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        }
        if (destroying) { stopAlarm(); return@withLock false }
        soundingSessionId = state.sessionId
        true
    }
    private fun stopAlarm() {
        runCatching { player?.stop() }; player?.release(); player = null
        (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel()
        soundingSessionId = 0L
    }
    private fun notification(state: FocusRuntimeState, alarmActive: Boolean = false): Notification {
        val seconds = state.remainingSeconds
        val paused = state.status == FocusRunStatus.PAUSED
        val localized=createConfigurationContext(android.content.res.Configuration(resources.configuration).apply { setLocale(this@FocusTimerService.locale) })
        val open=PendingIntent.getActivity(this,51,Intent(this,MainActivity::class.java).putExtra("tab","focus"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val confirmStop=PendingIntent.getActivity(this,54,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("tab","focus").putExtra("focus_confirm_stop",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause=PendingIntent.getService(this,52,Intent(this,FocusTimerService::class.java).setAction(FocusTimerController.PAUSE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss=PendingIntent.getService(this,55,Intent(this,FocusTimerService::class.java).setAction(FocusTimerController.DISMISS_ALARM),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val resume=PendingIntent.getService(this,53,Intent(this,FocusTimerService::class.java).setAction(FocusTimerController.RESUME),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(notificationHabitName ?: localized.getString(P8R.string.p8_focus_notification))
            .setContentText(if (alarmActive) localized.getString(P8R.string.p8_focus_alarm_completed) else if (paused) "${localized.getString(P8R.string.p8_focus_paused)} · ${String.format(locale,"%02d:%02d",seconds/60,seconds%60)}" else localized.getString(P8R.string.p8_focus_running,String.format(locale,"%02d:%02d",seconds/60,seconds%60)))
            .setProgress(1000, if (state.plannedMs > 0) ((state.effectiveMs.coerceIn(0, state.plannedMs) * 1000) / state.plannedMs).toInt() else 0, false)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (alarmActive) builder.addAction(android.R.drawable.ic_lock_idle_alarm, localized.getString(P8R.string.p8_focus_alarm_off), dismiss)
        else if (state.status == FocusRunStatus.RUNNING || paused) builder.addAction(if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause, localized.getString(if (paused) P8R.string.p8_focus_resume else P8R.string.p8_focus_pause), if (paused) resume else pause)
        if (paused && !alarmActive) builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, localized.getString(P8R.string.p8_focus_stop), confirmStop)
        return builder.build()
    }
    override fun onDestroy() {
        FocusTimerSignals.running.value=false
        destroying = true
        scope.cancel()
        stopAlarm()
        keepTimerAwake(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object { const val FOCUS_NOTIFICATION_ID=2001;private const val CHANNEL="focus_timer" }
}
