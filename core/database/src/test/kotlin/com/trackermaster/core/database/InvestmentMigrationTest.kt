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
class InvestmentMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TrackermasterDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun populatedElevenRetainsCashLoanClassificationAndGoals(){helper.createDatabase("investment-eleven",11).apply{
        execSQL("INSERT INTO accounts (id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES (1,'Cash','CHECKING','COP',100.25,10025,2,0)")
        execSQL("INSERT INTO transactions (id,accountId,amount,type,categoryId,dateEpoch,note,amountMinor,dateEpochDay,spaceId,principal) VALUES (1,1,12.34,'EXPENSE',0,0,'Loan',1234,20000,2,1)")
        execSQL("INSERT INTO money_debts VALUES ('debt','Sister','PAYABLE',50000,'COP',2,NULL,'Keep',0,3)")
        execSQL("INSERT INTO money_debt_payments VALUES ('payment','debt',1,'REPAYMENT',1)")
        execSQL("INSERT INTO money_pockets VALUES (1,1,4,'Goal')")
        execSQL("INSERT INTO money_savings_goals VALUES ('goal','Trip',80000,'COP',1,NULL,0,2)")
        close()
    };helper.runMigrationsAndValidate("investment-eleven",12,true,InvestmentMigration.MIGRATION_11_12).use{db->
        db.query("SELECT amountMinor,principal,investmentId,investmentCapital,note FROM transactions WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals(1234L,it.getLong(0));assertEquals(1,it.getInt(1));assertTrue(it.isNull(2));assertEquals(0,it.getInt(3));assertEquals("Loan",it.getString(4))}
        db.query("SELECT principalMinor,version FROM money_debts WHERE id='debt'").use{it.moveToFirst();assertEquals(50000L,it.getLong(0));assertEquals(3L,it.getLong(1))}
        db.query("SELECT movementId FROM money_debt_payments WHERE id='payment'").use{it.moveToFirst();assertEquals(1L,it.getLong(0))}
        db.query("SELECT targetMinor,pocketId FROM money_savings_goals WHERE id='goal'").use{it.moveToFirst();assertEquals(80000L,it.getLong(0));assertEquals(1L,it.getLong(1))}
        db.query("SELECT COUNT(*) FROM money_investments").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
    }}
    @Test fun originalFourMigratesWithoutDestroyingLegacyAmounts(){helper.createDatabase("investment-four",4).apply{
        execSQL("INSERT INTO accounts VALUES (1,'Savings','SAVINGS','USD',100.25)")
        execSQL("INSERT INTO expense_categories VALUES (1,'Custom',0,123)")
        execSQL("INSERT INTO transactions VALUES (1,1,10.105,'EXPENSE',1,1780000000000,'Original','old.jpg')");close()
    };helper.runMigrationsAndValidate("investment-four",12,true,MoneyMigrations.MIGRATION_4_5,MoneyMigrations.MIGRATION_5_6,ReceiptMigration.MIGRATION_6_7,PlanningMigration.MIGRATION_7_8,CloudSyncSchema.MIGRATION_8_9,AssistantMigration.MIGRATION_9_10,CommitmentMigration.MIGRATION_10_11,InvestmentMigration.MIGRATION_11_12).use{db->
        db.query("SELECT amount,amountMinor,imageUri,investmentId,investmentCapital FROM transactions WHERE id=1").use{it.moveToFirst();assertEquals(10.105,it.getDouble(0),0.0);assertEquals(1010L,it.getLong(1));assertEquals("old.jpg",it.getString(2));assertTrue(it.isNull(3));assertEquals(0,it.getInt(4))}
    }}
}
