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
class CommitmentMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TrackermasterDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun populatedTenPreservesBalancesReceiptsPlansAndExternalTables(){helper.createDatabase("commitments-ten",10).apply{
        execSQL("INSERT INTO accounts (id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES (1,'Nequi','CHECKING','COP',100.25,10025,2,0)")
        execSQL("INSERT INTO transactions (id,accountId,amount,type,categoryId,dateEpoch,note,amountMinor,dateEpochDay,spaceId) VALUES (1,1,12.34,'EXPENSE',1,0,'Original',1234,20000,2)")
        execSQL("INSERT INTO money_receipts VALUES ('r','money/receipts/a.pdf','application/pdf','hash','a.pdf','text',NULL,'REVIEW','{}',1)")
        execSQL("INSERT INTO money_plans VALUES ('plan','Data','PAYMENT','EXPENSE',3200000,'COP',1,NULL,1,20000,20030,'MONTHLY',2,NULL,'Keep plan',1,0,3,2)")
        execSQL("INSERT INTO money_plan_occurrences VALUES ('plan',20000,'COMPLETED',1,'original-fingerprint')")
        execSQL("INSERT INTO assistant_commands VALUES ('command','DONE','2026-10-15')")
        execSQL("INSERT INTO cloud_record_versions VALUES ('record',7)")
        close()
    };val db=helper.runMigrationsAndValidate("commitments-ten",11,true,CommitmentMigration.MIGRATION_10_11)
        db.query("SELECT amountMinor,principal,note FROM transactions WHERE id=1").use{assertTrue(it.moveToFirst());assertEquals(1234L,it.getLong(0));assertEquals(0,it.getInt(1));assertEquals("Original",it.getString(2))}
        db.query("SELECT initialBalanceMinor FROM accounts WHERE id=1").use{it.moveToFirst();assertEquals(10025L,it.getLong(0))}
        db.query("SELECT path FROM money_receipts WHERE id='r'").use{it.moveToFirst();assertEquals("money/receipts/a.pdf",it.getString(0))}
        db.query("SELECT amountMinor,nextDueEpochDay,version,plannedSpaceId,note FROM money_plans WHERE id='plan'").use{it.moveToFirst();assertEquals(3200000L,it.getLong(0));assertEquals(20030L,it.getLong(1));assertEquals(3L,it.getLong(2));assertEquals(2L,it.getLong(3));assertEquals("Keep plan",it.getString(4))}
        db.query("SELECT movementId,fingerprint FROM money_plan_occurrences WHERE planId='plan'").use{it.moveToFirst();assertEquals(1L,it.getLong(0));assertEquals("original-fingerprint",it.getString(1))}
        db.query("SELECT revision FROM cloud_record_versions WHERE recordId='record'").use{it.moveToFirst();assertEquals(7L,it.getLong(0))}
        db.query("SELECT state FROM assistant_commands WHERE id='command'").use{it.moveToFirst();assertEquals("DONE",it.getString(0))}
        listOf("money_debts","money_debt_payments","money_savings_goals").forEach{t->db.query("SELECT COUNT(*) FROM $t").use{it.moveToFirst();assertEquals(0,it.getInt(0))}};db.close()
    }
    @Test fun fullFourToElevenChainPreservesOriginalAmounts(){helper.createDatabase("commitments-four",4).apply{
        execSQL("INSERT INTO accounts VALUES (1,'Savings','SAVINGS','USD',100.25)")
        execSQL("INSERT INTO expense_categories VALUES (1,'Custom',0,123)")
        execSQL("INSERT INTO transactions VALUES (1,1,10.105,'EXPENSE',1,1780000000000,'Original','old.jpg')");close()
    };val db=helper.runMigrationsAndValidate("commitments-four",11,true,MoneyMigrations.MIGRATION_4_5,MoneyMigrations.MIGRATION_5_6,ReceiptMigration.MIGRATION_6_7,PlanningMigration.MIGRATION_7_8,CloudSyncSchema.MIGRATION_8_9,AssistantMigration.MIGRATION_9_10,CommitmentMigration.MIGRATION_10_11)
        db.query("SELECT amount,amountMinor,imageUri,principal FROM transactions WHERE id=1").use{it.moveToFirst();assertEquals(10.105,it.getDouble(0),0.0);assertEquals(1010L,it.getLong(1));assertEquals("old.jpg",it.getString(2));assertEquals(0,it.getInt(3))};db.close()
    }
}
