package com.trackermaster.feature.settings

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.data.unlock.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.unlock.UnlockLaunchRules
import com.trackermaster.core.ui.R as P8R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockSettingsViewModel @Inject constructor(private val store: UnlockLaunchStore,
    private val controller: UnlockMonitorController,private val appSettings: SettingsRepository): ViewModel() {
    val config=store.settings.map<UnlockLaunchSettings,UnlockLaunchSettings?> { it }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val running=UnlockMonitorSignals.running
    val error=MutableStateFlow(false)
    fun configure(enabled: Boolean,minutes: Int) = viewModelScope.launch {
        try {
            store.configure(enabled,minutes,appSettings.settings.first().localeTag)
            if(enabled) controller.start() else controller.stop()
            error.value=false
        } catch(_: Exception) { error.value=true }
    }
    fun restart() { controller.start() }
    init { viewModelScope.launch { appSettings.settings.map { it.localeTag }.distinctUntilChanged().collect { store.locale(it) } } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnlockSettingsSection(vm: UnlockSettingsViewModel=hiltViewModel()) {
    val context=LocalContext.current
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val config by vm.config.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val monitorError by UnlockMonitorSignals.error.collectAsStateWithLifecycle()
    var capabilities by remember { mutableStateOf(unlockCapabilities(context)) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { capabilities=unlockCapabilities(context) }
    DisposableEffect(owner,context) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) capabilities=unlockCapabilities(context) }
        owner.lifecycle.addObserver(observer);onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) { try { context.startActivity(intent) } catch(_: Exception) { settingsUnavailable=true } }
    val current=config
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(P8R.string.p8_unlock_title),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(P8R.string.p8_unlock_help),style=MaterialTheme.typography.bodySmall)
            if(current==null) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(stringResource(if(current.enabled)P8R.string.p8_unlock_enabled else P8R.string.p8_unlock_off),modifier=Modifier.weight(1f))
                    Switch(current.enabled,{ vm.configure(it,current.intervalMinutes) })
                }
                if(current.enabled) {
                    Text(stringResource(P8R.string.p8_unlock_legacy),style=MaterialTheme.typography.bodySmall)
                    Text(stringResource(P8R.string.p8_unlock_interval),style=MaterialTheme.typography.labelLarge)
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        UnlockLaunchRules.intervalsMinutes.forEach { minutes -> FilterChip(current.intervalMinutes==minutes,
                            onClick={vm.configure(true,minutes)},label={Text(stringResource(if(minutes==30)P8R.string.p8_unlock_minutes else P8R.string.p8_unlock_hours,if(minutes==30)minutes else minutes/60))}) }
                    }
                    Text(stringResource(P8R.string.p8_unlock_rotation,stringResource(unlockSectionName(current.destination))))
                    Text(stringResource(if(running)P8R.string.p8_unlock_running else P8R.string.p8_unlock_stopped))
                    if(!running) OutlinedButton(onClick=vm::restart) { Text(stringResource(P8R.string.p8_unlock_restart)) }
                    if(!capabilities.automaticAllowed) {
                        Text(stringResource(P8R.string.p8_unlock_fallback))
                        Text(stringResource(P8R.string.p8_unlock_overlay_help),style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,"package:${context.packageName}".toUri()))}) { Text(stringResource(P8R.string.p8_unlock_overlay)) }
                    }
                    if(current.lastOutcome=="BLOCKED") Text(stringResource(P8R.string.p8_unlock_blocked),color=MaterialTheme.colorScheme.error)
                    if(!capabilities.notificationsAllowed) {
                        Text(stringResource(P8R.string.p8_unlock_notifications_missing))
                        OutlinedButton(onClick={
                            if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
                        }) { Text(stringResource(P8R.string.p8_unlock_notifications)) }
                        TextButton(onClick={open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) { Text(stringResource(P8R.string.p8_unlock_notification_settings)) }
                    }
                    Text(stringResource(if(capabilities.batteryExempt)P8R.string.p8_unlock_battery_exempt else P8R.string.p8_unlock_battery_active),style=MaterialTheme.typography.bodySmall)
                    Text(stringResource(P8R.string.p8_unlock_battery_help),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}) { Text(stringResource(P8R.string.p8_unlock_battery)) }
                    Text(stringResource(P8R.string.p8_unlock_reboot),style=MaterialTheme.typography.bodySmall)
                }
            }
            if(settingsUnavailable) Text(stringResource(P8R.string.p8_unlock_unavailable),color=MaterialTheme.colorScheme.error)
            if(error || monitorError) Text(stringResource(P8R.string.p8_unlock_error),color=MaterialTheme.colorScheme.error)
        }
    }
}

private fun unlockSectionName(route: String)=when(route) {
    "habits" -> P8R.string.p8_section_habits
    "tasks" -> P8R.string.p8_section_tasks
    "expense" -> P8R.string.p8_section_money
    "focus" -> P8R.string.p8_section_focus
    "journal" -> P8R.string.p8_section_journal
    else -> P8R.string.p8_section_home
}
