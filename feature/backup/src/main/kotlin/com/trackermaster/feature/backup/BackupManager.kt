package com.trackermaster.feature.backup

import android.content.Context
import com.trackermaster.core.database.TrackermasterDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TrackermasterDatabase,
) {
    suspend fun exportLocalZip(): File = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("trackermaster.db")
        val outDir = File(context.filesDir, "backups").apply { mkdirs() }
        val zipFile = File(outDir, "trackermaster_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            if (dbFile.exists()) {
                zos.putNextEntry(ZipEntry("trackermaster.db"))
                dbFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            context.filesDir.listFiles()?.filter { it.extension in listOf("jpg", "png", "pdf") }?.forEach { f ->
                zos.putNextEntry(ZipEntry("attachments/${f.name}"))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        zipFile
    }

    /** Placeholder for Google Drive REST upload — requires OAuth in production */
    suspend fun uploadToGoogleDrive(accountEmail: String): Result<String> = withContext(Dispatchers.IO) {
        val zip = exportLocalZip()
        Result.success("Backup ready at ${zip.absolutePath}. Connect Google Drive API with OAuth for cloud sync.")
    }
}
