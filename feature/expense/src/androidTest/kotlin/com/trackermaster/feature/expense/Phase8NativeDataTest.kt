package com.trackermaster.feature.expense

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.data.progress.*
import com.trackermaster.core.data.backup.*
import com.trackermaster.core.data.money.MoneyPreferencesRepository
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.data.unlock.UnlockLaunchStore
import com.trackermaster.core.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.ZoneId
import java.util.UUID

/** Runs only in the standalone library-test package, never against the user's app/data. */
class Phase8NativeDataTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun database()=Room.databaseBuilder(context,TrackermasterDatabase::class.java,"phase8-${UUID.randomUUID()}.db")
        .addCallback(com.trackermaster.core.database.MoneyDatabaseCallback)
        .addCallback(com.trackermaster.core.database.ProgressHistorySchema.callback).build()
    @Test fun androidMonotonicClockExcludesPausesAndRecoversWithoutDuplicatingTime(): Unit=runBlocking {
        val db=database()
        try {
            val clock=ProgressClock(context);var repo=FocusProgressRepository(db,clock)
            val habit=db.habitDao().insert(HabitEntity(name="Native synthetic habit",progressType="TIMER",scheduleType="DAILY",targetDurationMs=1_800_000,colorArgb=0,createdEpochDay=clock.today().toEpochDay(),creationDateKnown=true))
            repo.start(FocusCategoryType.STUDY,30,habit);delay(1200);repo.pause()
            val first=repo.checkpoint().effectiveMs
            assertTrue(first>=1000);delay(600)
            repo=FocusProgressRepository(db,clock);assertEquals(first,repo.checkpoint().effectiveMs)
            repo.resume();delay(1100);val final=repo.stop().effectiveMs
            assertTrue(final>=first+1000)
            repeat(3) { assertEquals(final,repo.checkpoint().effectiveMs) }
            val day=clock.today().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val log=db.habitLogDao().getForDate(habit,day)!!
            assertEquals(final,log.durationMs);assertFalse(log.completed)
            assertEquals(final/1000,repo.observeTodaySeconds().first())
        } finally { db.close() }
    }
    @Test fun nativeArchiveRestoresDatabaseReceiptAndAllPreferenceFiles(): Unit=runBlocking {
        val db=database()
        val settings=SettingsRepository(context);val prefs=MoneyPreferencesRepository(context)
        settings.setTheme(ThemeMode.OLED);settings.setLocale("es");prefs.setPrivacy(true)
        settings.setOnboardingComplete();settings.setDefumblrEnabled(false)
        settings.setBackupFrequency(BackupFrequency.DAILY);settings.setLastBackupAtMillis(System.currentTimeMillis())
        val habit=db.habitDao().insert(HabitEntity(name="Backup synthetic habit",progressType="TIMER",scheduleType="DAILY",targetDurationMs=1_800_000,colorArgb=0))
        val file=File(context.filesDir,"money/receipts/phase8-synthetic.txt").apply { parentFile!!.mkdirs();writeText("SYNTHETIC RECEIPT") }
        val sql=db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO accounts(id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES(1,'Synthetic cash','CASH','COP',100,10000,2,0)")
        sql.execSQL("INSERT INTO transactions(id,accountId,amount,type,categoryId,dateEpoch,note,imageUri,amountMinor,dateEpochDay,spaceId,principal,investmentCapital) VALUES(1,1,10,'EXPENSE',0,0,'Synthetic',?,1000,0,2,0,0)",arrayOf(file.path))
        val backup=BackupCoordinator(context,db,settings,prefs)
        try {
            val archive=backup.export()
            sql.execSQL("UPDATE habits SET name='Current value' WHERE id=?",arrayOf(habit))
            backup.prepare(Uri.fromFile(archive));assertFalse(RestoreBootstrap.hasPending(context))
            assertEquals("Current value",db.habitDao().getById(habit)!!.name)
            backup.confirm();db.close();RestoreBootstrap.applyPending(context)
            assertEquals("APPLIED",File(context.noBackupFilesDir,"restore-last-result").readText())
            SQLiteDatabase.openDatabase(context.getDatabasePath("trackermaster.db").path,null,SQLiteDatabase.OPEN_READONLY).use { restored ->
                restored.rawQuery("SELECT name FROM habits WHERE id=?",arrayOf(habit.toString())).use { assertTrue(it.moveToFirst());assertEquals("Backup synthetic habit",it.getString(0)) }
                restored.rawQuery("SELECT amountMinor,imageUri FROM transactions",null).use { assertTrue(it.moveToFirst());assertEquals(1000L,it.getLong(0));assertEquals(file.path,it.getString(1)) }
                assertEquals(13,restored.version)
            }
            assertEquals("SYNTHETIC RECEIPT",file.readText());assertTrue(archive.exists())
            suspend fun preferences(name: String): androidx.datastore.preferences.core.Preferences {
                val copy=File(context.cacheDir,"${UUID.randomUUID()}.preferences_pb")
                File(context.filesDir,"datastore/$name.preferences_pb").copyTo(copy)
                val job=SupervisorJob()
                return try { PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={copy}).data.first() }
                finally { job.cancelAndJoin();copy.delete() }
            }
            assertEquals("OLED",preferences("trackermaster_settings")[stringPreferencesKey("theme")])
            assertEquals(true,preferences("money_settings")[booleanPreferencesKey("privacy")])
            File(context.filesDir,"native-phase8").mkdirs()
            File(context.filesDir,"datastore/trackermaster_settings.preferences_pb").copyTo(File(context.filesDir,"native-phase8/settings.preferences_pb"),overwrite=true)
            RestoreBootstrap.cleanup(context)
        } finally { db.close() }
    }
    @Test fun nativeDataStoreKeepsGlancesAndRotationAcrossStoreRecreation(): Unit=runBlocking {
        val clock=object: ProgressClock(context) { var now=1000L;override fun elapsedMs()=now;override fun bootCount()=1 }
        var store=UnlockLaunchStore(context,clock);store.configure(true,30,"es")
        val rotation=store.settings.first().rotation
        store.screenOff();clock.now+=20*60_000;store.screenOff();clock.now+=10*60_000
        val delivery=store.unlocked()!!;assertNull(store.unlocked());assertTrue(store.pending(delivery.token))
        assertEquals(rotation,store.settings.first().rotation)
        assertTrue(store.acknowledge(delivery.token,delivery.destination))
        store=UnlockLaunchStore(context,clock)
        assertEquals((rotation+1)%6,store.settings.first().rotation)
        File(context.filesDir,"native-phase8").mkdirs()
        File(context.filesDir,"datastore/unlock_launch_settings.preferences_pb").copyTo(File(context.filesDir,"native-phase8/unlock.preferences_pb"),overwrite=true)
        store.configure(false,30,"es")
    }
}
