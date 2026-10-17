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
class PlanningMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TrackermasterDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun populatedSevenKeepsBudgetsReceiptsAndAllFinancialRows() {
        helper.createDatabase("planning-seven",7).apply {
            execSQL("INSERT INTO accounts (id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES (1,'Nequi','CHECKING','COP',100.25,10025,2,0)")
            execSQL("INSERT INTO budgets (id,categoryId,limitAmount,period,alertThresholdPercent) VALUES (1,7,500.25,'MONTHLY',80)")
            execSQL("INSERT INTO money_receipts VALUES ('r','money/receipts/a.pdf','application/pdf','hash','a.pdf','text',NULL,'REVIEW','{}',1)")
            close()
        }
        val db=helper.runMigrationsAndValidate("planning-seven",8,true,PlanningMigration.MIGRATION_7_8)
        db.query("SELECT limitAmount,currencyCode,startDateEpochDay,endDateEpochDay FROM budgets WHERE id=1").use{
            assertTrue(it.moveToFirst());assertEquals(500.25,it.getDouble(0),0.0);assertTrue(it.isNull(1));assertTrue(it.isNull(2));assertTrue(it.isNull(3))
        }
        db.query("SELECT draftJson,path FROM money_receipts WHERE id='r'").use{assertTrue(it.moveToFirst());assertEquals("{}",it.getString(0));assertEquals("money/receipts/a.pdf",it.getString(1))}
        db.query("SELECT initialBalanceMinor FROM accounts WHERE id=1").use{it.moveToFirst();assertEquals(10025L,it.getLong(0))}
        db.query("SELECT COUNT(*) FROM money_plans").use{it.moveToFirst();assertEquals(0,it.getInt(0))};db.close()
    }
    @Test fun completeFourToEightChainPreservesLegacyMoneyAndNewSchema() {
        helper.createDatabase("planning-four",4).apply {
            execSQL("INSERT INTO accounts VALUES (1,'Savings','SAVINGS','USD',100.25)")
            execSQL("INSERT INTO expense_categories VALUES (1,'Custom',0,123)")
            execSQL("INSERT INTO transactions VALUES (1,1,10.105,'EXPENSE',1,1780000000000,'Original','old.jpg')")
            execSQL("INSERT INTO budgets VALUES (1,1,50.25,'MONTHLY',80)");close()
        }
        val db=helper.runMigrationsAndValidate("planning-four",8,true,MoneyMigrations.MIGRATION_4_5,MoneyMigrations.MIGRATION_5_6,ReceiptMigration.MIGRATION_6_7,PlanningMigration.MIGRATION_7_8)
        db.query("SELECT amount,amountMinor,note,imageUri FROM transactions WHERE id=1").use{
            it.moveToFirst();assertEquals(10.105,it.getDouble(0),0.0);assertEquals(1010L,it.getLong(1));assertEquals("Original",it.getString(2));assertEquals("old.jpg",it.getString(3))
        };db.close()
    }
}
