package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class LifeMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TrackermasterDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun thirteenToFourteenPreservesJournalImagesDatesAndMoney() {
        helper.createDatabase("life-thirteen",13).apply {
            execSQL("INSERT INTO journal_entries(id,title,richTextHtml,createdAtEpoch,updatedAtEpoch,moodLevel) VALUES(1,'Original','<p>Keep</p>',1680000000000,1690000000000,4)")
            execSQL("INSERT INTO attachments VALUES(1,1,'/original/image.jpg','image/jpeg')")
            execSQL("INSERT INTO accounts(id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES(1,'Keep','SAVINGS','COP',1,100,1,0)")
            close()
        }
        helper.runMigrationsAndValidate("life-thirteen",14,true,LifeMigration.MIGRATION_13_14).use { db ->
            db.query("SELECT title,richTextHtml,createdAtEpoch,updatedAtEpoch,kind,contentEpochDay,deletedAt FROM journal_entries").use {
                assertTrue(it.moveToFirst());assertEquals("Original",it.getString(0));assertEquals("<p>Keep</p>",it.getString(1));assertEquals(1680000000000L,it.getLong(2));assertEquals(1690000000000L,it.getLong(3));assertEquals("PERSONAL",it.getString(4));assertTrue(it.isNull(5));assertTrue(it.isNull(6))
            }
            db.query("SELECT path FROM attachments").use {it.moveToFirst();assertEquals("/original/image.jpg",it.getString(0))}
            db.query("SELECT initialBalanceMinor FROM accounts").use {it.moveToFirst();assertEquals(100L,it.getLong(0))}
            db.query("SELECT COUNT(*) FROM journal_versions").use {it.moveToFirst();assertEquals(0,it.getInt(0))}
        }
    }
}
