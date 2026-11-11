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
class MoneyGroupMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TrackermasterDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun migrationPreservesExistingAccountsAndReceipts() {
        helper.createDatabase("money-groups-fourteen",14).apply {
            execSQL("INSERT INTO accounts(id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES(1,'Keep','SAVINGS','COP',1,100,1,0)")
            execSQL("INSERT INTO money_receipts VALUES ('draft','money/receipts/a.pdf','application/pdf','hash','a.pdf','text',NULL,'REVIEW','{}',1)")
            close()
        }
        helper.runMigrationsAndValidate("money-groups-fourteen",15,true,MoneyGroupMigration.MIGRATION_14_15).use { db ->
            db.query("SELECT initialBalanceMinor FROM accounts WHERE id=1").use { assertTrue(it.moveToFirst());assertEquals(100L,it.getLong(0)) }
            db.query("SELECT stage FROM money_receipts WHERE id='draft'").use { assertTrue(it.moveToFirst());assertEquals("REVIEW",it.getString(0)) }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO money_groups VALUES ('group','Trip',1)")
            db.execSQL("INSERT INTO money_group_members VALUES ('receipt:draft','group')")
            db.execSQL("DELETE FROM money_groups WHERE id='group'")
            db.query("SELECT COUNT(*) FROM money_group_members").use {it.moveToFirst();assertEquals(0,it.getInt(0))}
            db.query("SELECT COUNT(*) FROM money_receipts").use {it.moveToFirst();assertEquals(1,it.getInt(0))}
        }
    }
}
