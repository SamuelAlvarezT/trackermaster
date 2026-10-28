package com.trackermaster.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.feature.backup.BackupManager
import com.trackermaster.core.domain.model.BackupFrequency
import com.trackermaster.core.domain.model.DefumblrInterval
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.ui.theme.AccentColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.trackermaster.core.ui.R as P8R

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val backupManager: BackupManager,
    val cloudSync: com.trackermaster.core.data.sync.CloudSyncManager,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.trackermaster.core.domain.model.AppSettings())
    fun cloudSignIn(context: android.content.Context) = viewModelScope.launch { cloudSync.signIn(context) }
    fun cloudSignOut(context: android.content.Context) = viewModelScope.launch { cloudSync.signOut(context) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setTheme(mode) }
    fun setAccent(i: Int) = viewModelScope.launch { repo.setAccent(i) }
    fun setLocale(tag: String) = viewModelScope.launch {
        repo.setLocale(tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    fun setBiometric(enabled: Boolean) = viewModelScope.launch { repo.setBiometric(enabled) }
    fun setDefumblrEnabled(enabled: Boolean) = viewModelScope.launch { repo.setDefumblrEnabled(enabled) }
    fun setDefumblrInterval(interval: DefumblrInterval) = viewModelScope.launch { repo.setDefumblrInterval(interval) }
    fun setInitialSection(section: String) = viewModelScope.launch { repo.setInitialSection(section) }
    fun setLockscreenWidgetsEnabled(enabled: Boolean) = viewModelScope.launch { repo.setLockscreenWidgetsEnabled(enabled) }
    fun setNewInterfaceEnabled(enabled: Boolean) = viewModelScope.launch { repo.setNewInterfaceEnabled(enabled) }
    fun setBackupFrequency(frequency: BackupFrequency) = viewModelScope.launch { repo.setBackupFrequency(frequency) }
    private val _backupMsg = MutableStateFlow<String?>(null)
    val backupMsg: StateFlow<String?> = _backupMsg
    val restorePreview=backupManager.restorePreview
    private val _backupBusy=MutableStateFlow(false)
    val backupBusy=_backupBusy.asStateFlow()
    init { viewModelScope.launch { _backupMsg.value=backupManager.lastRestoreMessage() } }
    fun exportBackup() = viewModelScope.launch {
        if(_backupBusy.value) return@launch
        _backupBusy.value=true
        try { _backupMsg.value=backupManager.createInternal().getOrElse { backupManager.failureMessage() } }
        finally { _backupBusy.value=false }
    }
    fun exportBackupTo(uri: Uri) = viewModelScope.launch {
        if(_backupBusy.value) return@launch
        _backupBusy.value=true
        try { _backupMsg.value=backupManager.exportToUri(uri).getOrElse { backupManager.failureMessage() } }
        finally { _backupBusy.value=false }
    }
    fun importBackupFrom(uri: Uri) = viewModelScope.launch {
        if(_backupBusy.value) return@launch
        _backupBusy.value=true
        try { _backupMsg.value=backupManager.importFromUri(uri).getOrElse { backupManager.failureMessage() } }
        finally { _backupBusy.value=false }
    }
    fun cancelRestore()=viewModelScope.launch { backupManager.cancelRestore() }
    fun confirmRestore(onReady: ()->Unit)=viewModelScope.launch {
        if(_backupBusy.value) return@launch
        _backupBusy.value=true
        try {
            backupManager.confirmRestore().fold(onSuccess={ onReady() },onFailure={ _backupMsg.value=backupManager.failureMessage() })
        } finally { _backupBusy.value=false }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cloud by vm.cloudSync.status.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val backupMsg by vm.backupMsg.collectAsStateWithLifecycle()
    val preview by vm.restorePreview.collectAsStateWithLifecycle()
    val backupBusy by vm.backupBusy.collectAsStateWithLifecycle()
    preview?.let { restored ->
        AlertDialog(onDismissRequest={ if(!backupBusy) vm.cancelRestore() },
            title={ Text(stringResource(P8R.string.p8_backup_review)) },
            text={ Column {
                Text(stringResource(P8R.string.p8_backup_details,restored.tables,restored.files))
                if(restored.legacy) Text(stringResource(P8R.string.p8_backup_legacy))
            } },
            confirmButton={ TextButton(enabled=!backupBusy,onClick={ vm.confirmRestore {
                var current=context
                while(current is android.content.ContextWrapper && current !is android.app.Activity) current=current.baseContext
                (current as? android.app.Activity)?.finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            } }) { Text(stringResource(P8R.string.p8_backup_confirm)) } },
            dismissButton={ TextButton(enabled=!backupBusy,onClick={ vm.cancelRestore() }) { Text(stringResource(P8R.string.p8_cancel)) } })
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackupTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importBackupFrom(uri)
    }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { UnlockSettingsSection() }
        item { MoneyWidgetSettingsSection() }
        item { Text("TrackMaster en PC", style = MaterialTheme.typography.titleMedium) }
        item {
            Text(when {
                !cloud.configured -> "Sincronización sin configurar. Sigue la guía de Firebase del proyecto."
                cloud.syncing -> "Sincronizando… (${cloud.pending} pendientes)"
                cloud.error != null -> "Error: ${cloud.error}"
                !cloud.signedIn -> "Inicia sesión para enviar tus datos a la web."
                cloud.pending > 0 -> "Pendiente: ${cloud.pending} cambios"
                else -> "Sincronizado"
            })
            if (cloud.lastCompletedAt > 0) Text("Último envío completo: " +
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(cloud.lastCompletedAt)))
            if (cloud.configured) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!cloud.signedIn) Button(onClick = { vm.cloudSignIn(context) }) { Text("Conectar con Google") }
                else {
                    OutlinedButton(onClick = { vm.cloudSync.retry() }) { Text("Reintentar") }
                    TextButton(onClick = { vm.cloudSignOut(context) }) { Text("Desconectar") }
                }
            }
            Text("Fotos y archivos disponibles solo en el móvil.", style = MaterialTheme.typography.bodySmall)
        }
        item { Text("Appearance", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("New dashboard interface")
                Switch(s.newInterfaceEnabled, { vm.setNewInterfaceEnabled(it) })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(s.themeMode == mode, { vm.setTheme(mode) }, { Text(mode.name) })
                }
            }
        }
        item { Text("Accent color") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AccentColors.take(10).forEachIndexed { i, _ ->
                    FilterChip(s.accentIndex == i, { vm.setAccent(i) }, { Text(" ") }, modifier = Modifier.width(40.dp))
                }
            }
        }
        item { Text("Language", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en" to "English", "es" to "Español", "zh" to "中文").forEach { (tag, label) ->
                    FilterChip(s.localeTag == tag, { vm.setLocale(tag) }, { Text(label) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Biometric lock")
                Switch(s.biometricEnabled, { vm.setBiometric(it) })
            }
        }
        item { Text("Lock Screen", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Defumblr Lockscreen Bubbles")
                Switch(s.defumblrEnabled, { vm.setDefumblrEnabled(it) })
            }
        }
        if (s.defumblrEnabled) {
            item { Text("Defumblr interval") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefumblrInterval.entries.forEach { interval ->
                        FilterChip(
                            selected = s.defumblrInterval == interval,
                            onClick = { vm.setDefumblrInterval(interval) },
                            label = { Text(interval.label()) },
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Lockscreen Widgets")
                Switch(s.lockscreenWidgetsEnabled, { vm.setLockscreenWidgetsEnabled(it) })
            }
        }
        item { Text("Initial section", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("home" to "Home", "habits" to "Habits", "tasks" to "Tasks", "expense" to "Money", "journal" to "Journal", "focus" to "Focus")
                    .forEach { (route, label) ->
                        FilterChip(s.initialSection == route, { vm.setInitialSection(route) }, { Text(label) })
                    }
            }
        }
        item { Text("Pomodoro (minutes)", style = MaterialTheme.typography.titleMedium) }
        item { Text("Work: ${s.pomodoroWorkMinutes} · Break: ${s.pomodoroBreakMinutes}") }
        item { Text(stringResource(P8R.string.p8_backup_title), style = MaterialTheme.typography.titleMedium) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled=!backupBusy,onClick={ exportLauncher.launch("trackermaster_backup.zip") }) { Text(stringResource(P8R.string.p8_backup_export)) }
                OutlinedButton(enabled=!backupBusy,onClick={ importLauncher.launch("application/zip") }) { Text(stringResource(P8R.string.p8_backup_import)) }
            }
        }
        item { Text("Auto backup") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupFrequency.entries.forEach { frequency ->
                    FilterChip(
                        selected = s.backupFrequency == frequency,
                        onClick = { vm.setBackupFrequency(frequency) },
                        label = { Text(frequency.label()) },
                    )
                }
            }
        }
        item { Button(enabled=!backupBusy,onClick={ vm.exportBackup() }) { Text(stringResource(P8R.string.p8_backup_internal)) } }
        if(backupBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(P8R.string.p8_backup_working)) }
        if (backupMsg != null) item { Text(backupMsg!!, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun BackupFrequency.label(): String = when (this) {
    BackupFrequency.APP_START -> "App start"
    BackupFrequency.DAILY -> "Daily"
    BackupFrequency.WEEKLY -> "Weekly"
    BackupFrequency.MONTHLY -> "Monthly"
}

private fun DefumblrInterval.label(): String = when (this) {
    DefumblrInterval.THIRTY_MINUTES -> "30 min"
    DefumblrInterval.ONE_HOUR -> "1 hour"
    DefumblrInterval.TWO_HOURS -> "2 hours"
    DefumblrInterval.THREE_HOURS -> "3 hours"
}
