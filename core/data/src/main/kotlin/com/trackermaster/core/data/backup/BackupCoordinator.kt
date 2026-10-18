package com.trackermaster.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import com.trackermaster.core.data.di.DataModule
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.data.money.MoneyPreferencesRepository
import com.trackermaster.core.database.TrackermasterDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCoordinator @Inject constructor(@ApplicationContext private val context: Context,
    private val db: TrackermasterDatabase,private val settings: SettingsRepository,
    private val moneyPreferences: MoneyPreferencesRepository) {
    data class RestorePreview(val tables: Int,val files: Int,val legacy: Boolean)
    private var preparedFolder: File?=null
    private val _preview=MutableStateFlow<RestorePreview?>(null)
    val preview=_preview.asStateFlow()
    suspend fun export(): File = withContext(Dispatchers.IO) { lock.withLock {
        settings.settings.first();moneyPreferences.preferences.first()
        val folder=File(context.cacheDir,"backup-export-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val payload=File(folder,"files").apply { mkdirs() }
        val image=File(folder,"trackermaster.db")
        val output=File(context.filesDir,"backups/trackermaster_${System.currentTimeMillis()}_${UUID.randomUUID()}.zip").apply { parentFile!!.mkdirs() }
        val temporary=File(output.path+".tmp")
        try {
            val triggers=mutableListOf<String>()
            val counts=db.withTransaction {
                SqliteSnapshot.copy(db.openHelper.writableDatabase,image,triggers)
            }
            // Immutable attachment files are copied after releasing the ledger write lock.
            // Every referenced path is checked below; a missing file aborts the export safely.
            var payloadBytes=image.length();var payloadCount=1
            context.filesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative=file.relativeTo(context.filesDir).invariantSeparatorsPath
                if(BackupFiles.included(relative)) {
                    require(file.canonicalPath.startsWith(context.filesDir.canonicalPath+File.separator))
                    require(++payloadCount<=BackupFiles.MAX_FILES && file.length()<=BackupFiles.MAX_TOTAL-payloadBytes)
                    val copy=BackupFiles.target(payload,relative).apply { parentFile!!.mkdirs() }
                    file.inputStream().use { input -> copy.outputStream().use { out ->
                        val buffer=ByteArray(64*1024)
                        while(true) {
                            val n=input.read(buffer);if(n<0)break
                            payloadBytes+=n;require(payloadBytes<=BackupFiles.MAX_TOTAL)
                            out.write(buffer,0,n)
                        }
                    } }
                }
            }
            BackupAttachments.prepare(context,image,payload,context.filesDir.canonicalPath,true)
            SQLiteDatabase.openDatabase(image.path,null,SQLiteDatabase.OPEN_READWRITE).use { out -> triggers.forEach { out.execSQL(it) } }
            val manifest=JSONObject().put("format",2).put("schema",13).put("created_at",System.currentTimeMillis())
                .put("files_root",context.filesDir.canonicalPath).put("tables",JSONObject(counts))
            val entries=JSONObject();var total=0L;var count=0
            ZipOutputStream(temporary.outputStream()).use { zip ->
                fun entry(name: String,file: File) {
                    check(++count<=BackupFiles.MAX_FILES)
                    zip.putNextEntry(ZipEntry(name))
                    val digest=MessageDigest.getInstance("SHA-256");var bytes=0L
                    file.inputStream().use { input -> val buffer=ByteArray(64*1024);while(true) {
                        val size=input.read(buffer);if(size<0)break
                        bytes+=size;total+=size;require(total<=BackupFiles.MAX_TOTAL)
                        zip.write(buffer,0,size);digest.update(buffer,0,size)
                    } }
                    zip.closeEntry()
                    entries.put(name,JSONObject().put("bytes",bytes).put("sha256",digest.digest().joinToString("") { "%02x".format(it) }))
                }
                entry("database/trackermaster.db",image)
                payload.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file -> entry("files/"+file.relativeTo(payload).invariantSeparatorsPath,file) }
                manifest.put("entries",entries)
                zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()
            }
            check(temporary.renameTo(output))
            output
        } finally { folder.deleteRecursively();temporary.delete() }
    } }

    /** Validate in isolation and stage the complete replacement; apply before Room opens after restart. */
    suspend fun prepare(uri: Uri): RestorePreview = withContext(Dispatchers.IO) { lock.withLock {
        val root=File(context.noBackupFilesDir,"restore-staging").apply { mkdirs() }
        check(!RestoreBootstrap.hasPending(context)) { "A restore is already pending" }
        preparedFolder?.let { removeStaging(it) };preparedFolder=null;_preview.value=null
        val folder=File(root,UUID.randomUUID().toString())
        var retained=false
        try {
            val original=context.contentResolver.openInputStream(uri)?.use { BackupFiles.extract(it,folder) }
                ?: if(File(folder,"database/trackermaster.db").isFile) null else error("Cannot read backup")
            val image=File(folder,"database/trackermaster.db")
            SQLiteDatabase.openDatabase(image.path,null,SQLiteDatabase.OPEN_READWRITE).use { source ->
                require(source.version in 3..13) { "Unsupported database version" }
                SqliteSnapshot.validate(source)
                source.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null).use { while(it.moveToNext()) Unit }
            }
            migrateAndValidate(image)
            val payload=File(folder,"files").apply { mkdirs() }
            val triggers=SQLiteDatabase.openDatabase(image.path,null,SQLiteDatabase.OPEN_READWRITE).use { SqliteSnapshot.detachTriggers(it) }
            BackupAttachments.prepare(context,image,payload,original?.optString("files_root")?.takeIf { it.isNotBlank() },false)
            SQLiteDatabase.openDatabase(image.path,null,SQLiteDatabase.OPEN_READWRITE).use { source ->
                source.execSQL("UPDATE focus_runtime SET status='PAUSED',lastElapsedMs=0,bootCount=-1 WHERE status='RUNNING'")
                source.execSQL("DELETE FROM money_planning_alerts") // Notifications are rebuilt for the restored ledger.
                triggers.forEach { source.execSQL(it) }
                SqliteSnapshot.validate(source)
            }
            File(image.path+"-wal").delete();File(image.path+"-shm").delete()
            File(payload,"datastore/unlock_launch_runtime.preferences_pb").delete()
            validatePreferences(payload)
            val manifest=JSONObject().put("format",2).put("schema",13)
            val entries=JSONObject()
            folder.walkTopDown().filter { it.isFile && (it==image || it.path.startsWith(payload.path+File.separator)) }.forEach { file ->
                val relative=file.relativeTo(folder).invariantSeparatorsPath
                entries.put(relative,JSONObject().put("bytes",file.length()).put("sha256",BackupFiles.sha(file)))
            }
            manifest.put("entries",entries)
            File(folder,"prepared.json").writeText(manifest.toString())
            val preview=RestorePreview(SQLiteDatabase.openDatabase(image.path,null,SQLiteDatabase.OPEN_READONLY).use { source ->
                source.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",null).use { it.moveToFirst();it.getInt(0) }
            },payload.walkTopDown().count { it.isFile },original==null)
            preparedFolder=folder;retained=true;_preview.value=preview
            preview
        } finally { if(!retained) removeStaging(folder) }
    } }

    suspend fun confirm() = withContext(Dispatchers.IO) { lock.withLock {
        val folder=checkNotNull(preparedFolder)
        RestoreBootstrap.schedule(context,folder)
        preparedFolder=null;_preview.value=null
    } }
    suspend fun cancel() = withContext(Dispatchers.IO) { lock.withLock {
        preparedFolder?.let { removeStaging(it) };preparedFolder=null;_preview.value=null
    } }
    private fun removeStaging(folder: File) {
        val root=File(context.noBackupFilesDir,"restore-staging").canonicalFile
        require(folder.canonicalPath.startsWith(root.path+File.separator))
        folder.deleteRecursively()
    }
    private suspend fun validatePreferences(payload: File) {
        File(payload,"datastore").listFiles()?.filter { it.isFile && it.name.endsWith(".preferences_pb") }?.forEach { file ->
            require(file.length()<=4*1024*1024)
            val job=SupervisorJob()
            try {
                val values=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={ file }).data.first()
                BackupValidation.preferences(file.name,values)
            }
            finally { job.cancelAndJoin() }
        }
    }

    private suspend fun migrateAndValidate(image: File) {
        val name="restore-validation-${UUID.randomUUID()}.db"
        val candidate=Room.databaseBuilder(context,TrackermasterDatabase::class.java,name)
            .createFromFile(image).addMigrations(*DataModule.restoreMigrations()).build()
        try {
            val database=candidate.openHelper.writableDatabase
            database.query("PRAGMA quick_check").use { require(it.moveToFirst() && it.getString(0)=="ok") }
            BackupValidation.enums(database)
            val money=com.trackermaster.core.data.money.MoneyRepository(candidate)
            money.observeWorkspace().first()
            com.trackermaster.core.data.money.MoneyInvestmentRepository(candidate,money).data.first()
            database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { while(it.moveToNext()) Unit }
            candidate.close()
            context.getDatabasePath(name).copyTo(image,overwrite=true)
        } finally { candidate.close();context.deleteDatabase(name) }
    }
    companion object { private val lock=Mutex() }
}
