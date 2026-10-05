package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReceiptMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TrackermasterDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun versionSixAddsEmptyReceiptInboxWithValidatedSchema() {
        helper.createDatabase("receipt-empty",6).close()
        val db=helper.runMigrationsAndValidate("receipt-empty",7,true,ReceiptMigration.MIGRATION_6_7)
        db.query("SELECT COUNT(*) FROM money_receipts").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
        db.query("SELECT COUNT(*) FROM transactions").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
        db.execSQL("INSERT INTO money_receipts VALUES ('draft','money/receipts/a.pdf','application/pdf','hash','a.pdf','','','READING','',123)")
        db.query("SELECT stage FROM money_receipts WHERE id='draft'").use{it.moveToFirst();assertEquals("READING",it.getString(0))}
        db.close()
    }
    @Test fun fullVersionFourChainKeepsOriginalLedgerAndAttachment() {
        helper.createDatabase("receipt-chain",4).apply {
            execSQL("INSERT INTO accounts VALUES (1, 'Nequi', 'CHECKING', 'COP', 1000.25)")
            execSQL("INSERT INTO expense_categories VALUES (1, 'Personalizada', 0, 123)")
            execSQL("INSERT INTO transactions VALUES (1, 1, 10.105, 'EXPENSE', 1, 1780000000000, 'Conservar', 'old.jpg')")
            execSQL("INSERT INTO budgets VALUES (1, 1, 500.25, 'MONTHLY', 80)");close()
        }
        val db=helper.runMigrationsAndValidate("receipt-chain",7,true,MoneyMigrations.MIGRATION_4_5,MoneyMigrations.MIGRATION_5_6,ReceiptMigration.MIGRATION_6_7)
        db.query("SELECT amount,amountMinor,dateEpoch,note,imageUri FROM transactions WHERE id=1").use {
            it.moveToFirst();assertEquals(10.105,it.getDouble(0),0.0);assertEquals(1010L,it.getLong(1))
            assertEquals(1780000000000,it.getLong(2));assertEquals("Conservar",it.getString(3));assertEquals("old.jpg",it.getString(4))
        }
        db.query("SELECT name FROM expense_categories WHERE id=1").use{it.moveToFirst();assertEquals("Personalizada",it.getString(0))}
        db.query("SELECT limitAmount FROM budgets WHERE id=1").use{it.moveToFirst();assertEquals(500.25,it.getDouble(0),0.0)}
        db.query("SELECT COUNT(*) FROM money_receipts").use{it.moveToFirst();assertEquals(0,it.getInt(0))};db.close()
    }
}
