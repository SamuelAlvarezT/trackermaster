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
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.ui.theme.AccentColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val backupManager: BackupManager,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.trackermaster.core.domain.model.AppSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setTheme(mode) }
    fun setAccent(i: Int) = viewModelScope.launch { repo.setAccent(i) }
    fun setLocale(tag: String) = viewModelScope.launch {
        repo.setLocale(tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    fun setBiometric(enabled: Boolean) = viewModelScope.launch { repo.setBiometric(enabled) }
    fun setDefumblrEnabled(enabled: Boolean) = viewModelScope.launch { repo.setDefumblrEnabled(enabled) }
    fun setLockscreenWidgetsEnabled(enabled: Boolean) = viewModelScope.launch { repo.setLockscreenWidgetsEnabled(enabled) }
    fun setNewInterfaceEnabled(enabled: Boolean) = viewModelScope.launch { repo.setNewInterfaceEnabled(enabled) }
    fun setBackupFrequency(frequency: BackupFrequency) = viewModelScope.launch { repo.setBackupFrequency(frequency) }
    private val _backupMsg = MutableStateFlow<String?>(null)
    val backupMsg: StateFlow<String?> = _backupMsg
    fun exportBackup() = viewModelScope.launch {
        val result = backupManager.uploadToGoogleDrive("local")
        _backupMsg.value = result.getOrNull()
    }
    fun exportBackupTo(uri: Uri) = viewModelScope.launch {
        val result = backupManager.exportToUri(uri)
        _backupMsg.value = result.getOrElse { it.message ?: "Export failed" }
    }
    fun importBackupFrom(uri: Uri) = viewModelScope.launch {
        val result = backupManager.importFromUri(uri)
        _backupMsg.value = result.getOrElse { it.message ?: "Import failed" }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val backupMsg by vm.backupMsg.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackupTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importBackupFrom(uri)
    }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Lockscreen Widgets")
                Switch(s.lockscreenWidgetsEnabled, { vm.setLockscreenWidgetsEnabled(it) })
            }
        }
        item { Text("Pomodoro (minutes)", style = MaterialTheme.typography.titleMedium) }
        item { Text("Work: ${s.pomodoroWorkMinutes} · Break: ${s.pomodoroBreakMinutes}") }
        item { Text("Backup", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ exportLauncher.launch("trackermaster_backup.zip") }) { Text("Export archive") }
                OutlinedButton({ importLauncher.launch("application/zip") }) { Text("Import archive") }
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
        item { Button({ vm.exportBackup() }) { Text("Make internal backup") } }
        if (backupMsg != null) item { Text(backupMsg!!, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun BackupFrequency.label(): String = when (this) {
    BackupFrequency.APP_START -> "App start"
    BackupFrequency.DAILY -> "Daily"
    BackupFrequency.WEEKLY -> "Weekly"
    BackupFrequency.MONTHLY -> "Monthly"
}
