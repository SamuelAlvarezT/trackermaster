package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ProgressMigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),TrackermasterDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun twelvePreservesMoneyAndHabitRecordsAndInitializesProgressWithoutInventingCompletion() {
        val day=LocalDate.now().minusDays(4)
        val epoch=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        helper.createDatabase("progress-twelve",12).apply {
            execSQL("INSERT INTO habits(id,name,progressType,scheduleType,scheduleDaysBitmask,targetCount,targetDurationMs,checklistItemsJson,colorArgb,archived,description,sortOrder) VALUES (1,'Meditate','TIMER','DAILY',0,1,1800000,'[]',0,0,'Keep',2)")
            execSQL("INSERT INTO habit_logs(id,habitId,dateEpoch,value,durationMs,checklistStateJson,completed,targetDurationMsAtLog,completionSource) VALUES (1,1,$epoch,0,600000,'{}',0,2400000,'manual')")
            execSQL("INSERT INTO focus_sessions(id,category,plannedMinutes,actualSeconds,completed,completedAtEpoch,startedAtEpoch) VALUES(1,'WORK',10,600,1,$epoch,$epoch)")
            execSQL("INSERT INTO accounts (id,name,type,currencyCode,initialBalance,initialBalanceMinor,spaceId,archived) VALUES (1,'Savings','SAVINGS','USD',100.25,10025,5,0)")
            execSQL("INSERT INTO transactions (id,accountId,amount,type,categoryId,dateEpoch,note,amountMinor,dateEpochDay,spaceId,principal,investmentCapital) VALUES (1,1,10.105,'EXPENSE',0,$epoch,'Keep',1010,${day.toEpochDay()},5,0,0)")
            execSQL("INSERT INTO money_debts VALUES ('debt','Sister','PAYABLE',50000,'USD',5,NULL,'Keep',0,3)")
            close()
        }
        helper.runMigrationsAndValidate("progress-twelve",13,true,ProgressMigration.MIGRATION_12_13).use { db ->
            db.query("SELECT createdEpochDay,creationDateKnown,description,sortOrder FROM habits").use {
                assertTrue(it.moveToFirst());assertEquals(day.toEpochDay(),it.getLong(0));assertEquals(0,it.getInt(1));assertEquals("Keep",it.getString(2));assertEquals(2,it.getInt(3))
            }
            db.query("SELECT durationMs,completed,targetDurationMsAtLog,completionSource FROM habit_logs").use {
                it.moveToFirst();assertEquals(600_000L,it.getLong(0));assertEquals(0,it.getInt(1));assertEquals(2_400_000L,it.getLong(2));assertEquals("manual",it.getString(3))
            }
            db.query("SELECT actualDurationMs,habitId FROM focus_sessions").use { it.moveToFirst();assertEquals(600_000L,it.getLong(0));assertTrue(it.isNull(1)) }
            db.query("SELECT amount,amountMinor,note FROM transactions").use { it.moveToFirst();assertEquals(10.105,it.getDouble(0),0.0);assertEquals(1010L,it.getLong(1));assertEquals("Keep",it.getString(2)) }
            db.query("SELECT principalMinor,version FROM money_debts").use { it.moveToFirst();assertEquals(50000L,it.getLong(0));assertEquals(3L,it.getLong(1)) }
            db.query("SELECT effectiveFromEpochDay,targetDurationMs FROM habit_schedule_history").use { it.moveToFirst();assertEquals(day.toEpochDay(),it.getLong(0));assertEquals(1_800_000L,it.getLong(1)) }
        }
    }
    @Test fun fourMigratesThroughAllVersionsWithoutDestroyingLegacyMoney() {
        helper.createDatabase("progress-four",4).apply {
            execSQL("INSERT INTO accounts VALUES (1,'Savings','SAVINGS','USD',100.25)")
            execSQL("INSERT INTO expense_categories VALUES (1,'Custom',0,123)")
            execSQL("INSERT INTO transactions VALUES (1,1,10.105,'EXPENSE',1,1780000000000,'Original','old.jpg')");close()
        }
        helper.runMigrationsAndValidate("progress-four",13,true,MoneyMigrations.MIGRATION_4_5,MoneyMigrations.MIGRATION_5_6,
            ReceiptMigration.MIGRATION_6_7,PlanningMigration.MIGRATION_7_8,CloudSyncSchema.MIGRATION_8_9,
            AssistantMigration.MIGRATION_9_10,CommitmentMigration.MIGRATION_10_11,InvestmentMigration.MIGRATION_11_12,ProgressMigration.MIGRATION_12_13).use { db ->
            db.query("SELECT amount,amountMinor,imageUri FROM transactions").use { it.moveToFirst();assertEquals(10.105,it.getDouble(0),0.0);assertEquals(1010L,it.getLong(1));assertEquals("old.jpg",it.getString(2)) }
        }
    }
    @Test fun scheduleChangesFromSqlWritersAreCapturedWithoutRewritingEarlierDates() {
        helper.createDatabase("progress-history",13).use { db ->
            ProgressHistorySchema.install(db)
            db.execSQL("INSERT INTO habits(id,name,progressType,scheduleType,scheduleDaysBitmask,targetCount,targetDurationMs,checklistItemsJson,colorArgb,archived,description,sortOrder,createdEpochDay,creationDateKnown) VALUES (1,'Walk','COUNTER','DAILY',0,10,0,'[]',0,0,'',0,1,1)")
            db.execSQL("INSERT INTO habit_schedule_history VALUES(1,1,'COUNTER','DAILY',0,10,0,0,0)")
            db.execSQL("UPDATE habits SET targetCount=20,scheduleType='SPECIFIC_DAYS',scheduleDaysBitmask=3 WHERE id=1")
            db.query("SELECT targetCount FROM habit_schedule_history WHERE effectiveFromEpochDay=1").use { it.moveToFirst();assertEquals(10,it.getInt(0)) }
            db.query("SELECT targetCount,scheduleDaysBitmask FROM habit_schedule_history WHERE effectiveFromEpochDay=${LocalDate.now().toEpochDay()}").use { it.moveToFirst();assertEquals(20,it.getInt(0));assertEquals(3,it.getInt(1)) }
        }
    }
}
