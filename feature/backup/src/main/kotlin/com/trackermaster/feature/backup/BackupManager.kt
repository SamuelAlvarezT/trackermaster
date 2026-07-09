package com.trackermaster.feature.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun exportLocalZip(): File = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("trackermaster.db")
        val outDir = File(context.filesDir, "backups").apply { mkdirs() }
        val zipFile = File(outDir, "trackermaster_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.zip")

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm")).forEach { file ->
                if (file.exists()) {
                    zos.putNextEntry(ZipEntry("database/${file.name}"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            context.filesDir.walkTopDown()
                .filter { it.isFile && !it.toRelativeString(context.filesDir).startsWith("backups") }
                .forEach { file ->
                    val entryName = "files/${file.toRelativeString(context.filesDir).replace('\\', '/')}"
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
        }

        zipFile
    }

    suspend fun exportToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = exportLocalZip()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                zip.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open export file")
            "Backup exported"
        }
    }

    suspend fun importFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val restoreDir = File(context.cacheDir, "restore_backup").apply {
                deleteRecursively()
                mkdirs()
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && !entry.name.contains("..")) {
                            val outFile = File(restoreDir, entry.name)
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: error("Cannot open import file")

            val dbDir = context.getDatabasePath("trackermaster.db").parentFile ?: error("Database path missing")
            File(restoreDir, "database").listFiles()?.forEach { file ->
                file.copyTo(File(dbDir, file.name), overwrite = true)
            }

            val filesDir = File(restoreDir, "files")
            if (filesDir.exists()) {
                filesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val target = File(context.filesDir, file.toRelativeString(filesDir))
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            }

            "Backup imported. Restart app to reload data."
        }
    }

    suspend fun uploadToGoogleDrive(accountEmail: String): Result<String> = withContext(Dispatchers.IO) {
        val zip = exportLocalZip()
        Result.success("Backup ready at ${zip.absolutePath}. Connect Google Drive API with OAuth for cloud sync.")
    }
}
