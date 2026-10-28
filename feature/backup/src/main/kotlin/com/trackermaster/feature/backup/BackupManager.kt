package com.trackermaster.feature.backup

import android.content.Context
import android.net.Uri
import com.trackermaster.core.data.backup.BackupCoordinator
import com.trackermaster.core.ui.R as P8R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: BackupCoordinator,
    private val cloudSync: com.trackermaster.core.data.sync.CloudSyncManager,
    private val settings: com.trackermaster.core.data.settings.SettingsRepository,
) {
    suspend fun failureMessage()=message(P8R.string.p8_backup_error)
    @android.annotation.SuppressLint("AppBundleLocaleChanges") // All languages are bundled offline.
    private suspend fun message(id: Int): String {
        val tag=settings.settings.first().localeTag
        return context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(tag))
        }).getString(id)
    }
    val restorePreview=coordinator.preview
    suspend fun exportLocalZip(): File=coordinator.export()
    suspend fun exportToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val zip=exportLocalZip()
            context.contentResolver.openOutputStream(uri)?.use { out -> zip.inputStream().use { it.copyTo(out) } }
                ?: error("Cannot open export file")
            message(P8R.string.p8_backup_exported)
        }
    }
    suspend fun createInternal(): Result<String> = runCatching {
        exportLocalZip();message(P8R.string.p8_backup_ready)
    }
    suspend fun importFromUri(uri: Uri): Result<String> = runCatching {
        coordinator.prepare(uri);message(P8R.string.p8_backup_validated)
    }
    suspend fun confirmRestore(): Result<Unit> = runCatching {
        cloudSync.prepareRestore()
        coordinator.confirm()
    }
    suspend fun cancelRestore()=coordinator.cancel()
    suspend fun lastRestoreMessage(): String? = withContext(Dispatchers.IO) {
        when(File(context.noBackupFilesDir,"restore-last-result").takeIf { it.isFile }?.readText()) {
            "APPLIED"->message(P8R.string.p8_backup_applied)
            "ROLLED_BACK"->message(P8R.string.p8_backup_rolled_back)
            else->null
        }
    }
}
