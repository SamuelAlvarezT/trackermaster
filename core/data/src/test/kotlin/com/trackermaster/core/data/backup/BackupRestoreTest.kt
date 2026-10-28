package com.trackermaster.core.data.backup

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.data.money.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class BackupRestoreTest {
    // sqlite4java on Windows cannot open long Robolectric method/cache paths.
    private class ShortStorageContext(base: Context): ContextWrapper(base) {
        val root=Files.createTempDirectory("b8-").toFile()
        override fun getApplicationContext(): Context=this
        override fun getFilesDir()=File(root,"files").apply { mkdirs() }
        override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
        override fun getNoBackupFilesDir()=File(root,"no-backup").apply { mkdirs() }
        override fun getDataDir()=root
        override fun getDatabasePath(name: String)=File(root,"databases/$name").apply { parentFile!!.mkdirs() }
        override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?)=
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
        override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?,handler: android.database.DatabaseErrorHandler?)=
            SQLiteDatabase.openDatabase(getDatabasePath(name).path,factory,SQLiteDatabase.CREATE_IF_NECESSARY,handler)
        override fun deleteDatabase(name: String)=SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    }
    private val context by lazy { ShortStorageContext(ApplicationProvider.getApplicationContext()) }
    @After fun cleanup() { context.root.deleteRecursively() }
    @Test fun snapshotPreservesIntegerPrecisionBlobsNullsSequencesAndEveryRoomTable()=runTest {
        val db=Room.inMemoryDatabaseBuilder(context,TrackermasterDatabase::class.java).allowMainThreadQueries().build()
        val folder=Files.createTempDirectory("sqlite-snapshot-").toFile()
        try {
            val sql=db.openHelper.writableDatabase
            sql.execSQL("CREATE TABLE snapshot_precision (id INTEGER PRIMARY KEY AUTOINCREMENT,amount INTEGER,binary BLOB,optional TEXT)")
            sql.execSQL("INSERT INTO snapshot_precision(amount,binary,optional) VALUES (?,?,NULL)",arrayOf(9_007_199_254_740_993L,byteArrayOf(0,1,-1)))
            sql.execSQL("UPDATE sqlite_sequence SET seq=100 WHERE name='snapshot_precision'")
            val image=File(folder,"snapshot.db")
            val counts=db.withTransaction { SqliteSnapshot.copy(sql,image) }
            assertTrue(counts.keys.containsAll(listOf("money_investments","money_debts","money_savings_goals","money_receipts","focus_runtime","habit_schedule_history")))
            SQLiteDatabase.openDatabase(image.path,null,SQLiteDatabase.OPEN_READWRITE).use { copied ->
                copied.rawQuery("SELECT amount,binary,optional FROM snapshot_precision",null).use {
                    assertTrue(it.moveToFirst());assertEquals(9_007_199_254_740_993L,it.getLong(0));assertArrayEquals(byteArrayOf(0,1,-1),it.getBlob(1));assertTrue(it.isNull(2))
                }
                copied.execSQL("INSERT INTO snapshot_precision(amount) VALUES(2)")
                copied.rawQuery("SELECT MAX(id) FROM snapshot_precision",null).use { it.moveToFirst();assertEquals(101L,it.getLong(0)) }
                assertEquals(14,copied.version)
            }
        } finally { db.close();folder.deleteRecursively() }
    }
    @Test fun completeArchiveIsValidatedBeforeConfirmationAndRestoresMoneyAttachmentsAndPreferences()=runTest {
        val db=Room.databaseBuilder(context,TrackermasterDatabase::class.java,"trackermaster.db").allowMainThreadQueries().build()
        val settings=SettingsRepository(context);val preferences=MoneyPreferencesRepository(context)
        settings.setTheme(ThemeMode.OLED);settings.setLocale("es");preferences.setPrivacy(true)
        val receipt=File(context.filesDir,"money/receipts/synthetic.bin").apply { parentFile!!.mkdirs();writeBytes(byteArrayOf(1,2,3)) }
        val sql=db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO accounts(id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES(1,'Nequi','CHECKING','COP',100,10000,2,0)")
        sql.execSQL("INSERT INTO transactions(id,accountId,amount,type,categoryId,dateEpoch,note,imageUri,amountMinor,dateEpochDay,spaceId,principal,investmentCapital) VALUES(1,1,10,'EXPENSE',0,0,'Synthetic',?,1000,0,2,0,0)",arrayOf(receipt.path))
        val journal=com.trackermaster.core.data.repository.JournalRepository(db.journalDao(),db,com.trackermaster.core.data.progress.ProgressClock(context))
        val now=java.time.LocalDateTime.now()
        val entry=com.trackermaster.core.domain.model.JournalEntry(title="Version image",richTextHtml="Before",createdAt=now,updatedAt=now,
            attachments=listOf(com.trackermaster.core.domain.model.Attachment(path=receipt.path,mimeType="image/png")))
        val entryId=journal.save(entry)
        journal.save(entry.copy(id=entryId,richTextHtml="After",attachments=emptyList()))
        val coordinator=BackupCoordinator(context,db,settings,preferences)
        val archive=coordinator.export()
        sql.execSQL("UPDATE accounts SET name='Current data' WHERE id=1")
        try {
            coordinator.prepare(Uri.fromFile(archive))
            assertFalse(RestoreBootstrap.hasPending(context));assertNotNull(coordinator.preview.value)
            db.moneyDao().account(1).let { assertEquals("Current data",it!!.name) }
            coordinator.cancel();assertFalse(RestoreBootstrap.hasPending(context))
            coordinator.prepare(Uri.fromFile(archive));coordinator.confirm();assertTrue(RestoreBootstrap.hasPending(context))
            db.close();RestoreBootstrap.applyPending(context)
            SQLiteDatabase.openDatabase(context.getDatabasePath("trackermaster.db").path,null,SQLiteDatabase.OPEN_READONLY).use { restored ->
                restored.rawQuery("SELECT name,initialBalanceMinor FROM accounts WHERE id=1",null).use { it.moveToFirst();assertEquals("Nequi",it.getString(0));assertEquals(10000L,it.getLong(1)) }
                restored.rawQuery("SELECT imageUri,amountMinor FROM transactions WHERE id=1",null).use { it.moveToFirst();assertEquals(receipt.path,it.getString(0));assertEquals(1000L,it.getLong(1)) }
                restored.rawQuery("SELECT payload FROM journal_versions ORDER BY id LIMIT 1",null).use {
                    assertTrue(it.moveToFirst())
                    val version=com.trackermaster.core.data.insights.LifeJson.journal(it.getString(0))
                    assertEquals(receipt.path,version.attachments.first().path)
                    assertEquals("Before",version.richTextHtml)
                }
            }
            assertArrayEquals(byteArrayOf(1,2,3),receipt.readBytes())
            assertTrue(File(context.filesDir,"datastore/trackermaster_settings.preferences_pb").isFile)
            assertTrue(File(context.filesDir,"datastore/money_settings.preferences_pb").isFile)
            assertTrue(archive.exists());assertFalse(RestoreBootstrap.hasPending(context))
            assertEquals("APPLIED",File(context.noBackupFilesDir,"restore-last-result").readText())
            RestoreBootstrap.cleanup(context)
        } finally { db.close() }
    }
    private class BootstrapContext(base: Context): ContextWrapper(base) {
        val root=File(base.cacheDir,"bootstrap-test-${UUID.randomUUID()}").apply { mkdirs() }
        override fun getFilesDir()=File(root,"files").apply { mkdirs() }
        override fun getNoBackupFilesDir()=File(root,"no-backup").apply { mkdirs() }
        override fun getDatabasePath(name: String)=File(root,"database/$name").apply { parentFile!!.mkdirs() }
    }
    @Test fun failureAfterInstallingFilesRollsBackDatabaseFilesAndBackupArchives() {
        val isolated=BootstrapContext(context)
        try {
            fun database(file: File,text: String) { file.parentFile!!.mkdirs();SQLiteDatabase.openOrCreateDatabase(file,null).use { it.execSQL("CREATE TABLE payload(value TEXT)");it.execSQL("INSERT INTO payload VALUES(?)",arrayOf(text));it.version=13 } }
            database(isolated.getDatabasePath("trackermaster.db"),"previous")
            File(isolated.filesDir,"note.txt").writeText("previous")
            File(isolated.filesDir,"backups/keep.zip").apply { parentFile!!.mkdirs();writeText("keep") }
            val stage=File(isolated.noBackupFilesDir,"restore-staging/test").apply { mkdirs() }
            val image=File(stage,"database/trackermaster.db");database(image,"replacement")
            val note=File(stage,"files/note.txt").apply { parentFile!!.mkdirs();writeText("replacement") }
            val entries=JSONObject()
            for(file in listOf(image,note)) entries.put(file.relativeTo(stage).invariantSeparatorsPath,JSONObject().put("bytes",file.length()).put("sha256",BackupFiles.sha(file)))
            File(stage,"prepared.json").writeText(JSONObject().put("schema",13).put("entries",entries).toString())
            RestoreBootstrap.schedule(isolated,stage)
            RestoreBootstrap.applyPending(isolated) { error("Synthetic interruption") }
            assertEquals("previous",File(isolated.filesDir,"note.txt").readText());assertEquals("keep",File(isolated.filesDir,"backups/keep.zip").readText())
            SQLiteDatabase.openDatabase(isolated.getDatabasePath("trackermaster.db").path,null,SQLiteDatabase.OPEN_READONLY).use { db -> db.rawQuery("SELECT value FROM payload",null).use { it.moveToFirst();assertEquals("previous",it.getString(0)) } }
            assertEquals("ROLLED_BACK",File(isolated.noBackupFilesDir,"restore-last-result").readText())
        } finally { isolated.root.deleteRecursively() }
    }
    @Test fun restoreNeverReadsAnExternalPathMissingFromArchive()=runTest {
        val db=Room.inMemoryDatabaseBuilder(context,TrackermasterDatabase::class.java).allowMainThreadQueries().build()
        val folder=Files.createTempDirectory("missing-attachment-").toFile()
        try {
            db.openHelper.writableDatabase.execSQL("INSERT INTO transactions(id,accountId,amount,type,categoryId,dateEpoch,note,imageUri,amountMinor,dateEpochDay,spaceId,principal,investmentCapital) VALUES(1,1,10,'EXPENSE',0,0,'Missing','/private/other/files/secret.txt',1000,0,2,0,0)")
            val image=File(folder,"image.db");db.withTransaction { SqliteSnapshot.copy(db.openHelper.writableDatabase,image) }
            assertTrue(runCatching { BackupAttachments.prepare(context,image,File(folder,"files").apply { mkdirs() },null,false) }.isFailure)
        } finally { db.close();folder.deleteRecursively() }
    }
}
