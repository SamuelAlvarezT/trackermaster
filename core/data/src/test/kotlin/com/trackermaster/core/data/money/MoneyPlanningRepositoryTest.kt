package com.trackermaster.core.data.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.MoneyDatabaseCallback
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.BudgetEntity
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyPlanningRepositoryTest {
    private lateinit var db:TrackermasterDatabase
    private lateinit var money:MoneyRepository
    private lateinit var repo:MoneyPlanningRepository
    private val due=LocalDate.of(2026,10,6)
    @Before fun before(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),TrackermasterDatabase::class.java)
        .addCallback(MoneyDatabaseCallback).allowMainThreadQueries().build();money=MoneyRepository(db);repo=MoneyPlanningRepository(db,money)}
    @After fun after(){db.close()}
    private suspend fun account(code:String="COP")=money.createAccount("Nequi",AccountType.CHECKING,Money(100000,code),2)
    private suspend fun plan(account:Long?=null,kind:MoneyPlanKind=MoneyPlanKind.PAYMENT):MoneyPlan {
        val p=MoneyPlan("p", "Data",kind,MoneyDirection.EXPENSE,Money(1000,"COP"),"COP",account,null,money.createCategory("Data",false),due,due,MoneyFrequency.MONTHLY)
        repo.savePlan(p);return repo.planSnapshot().single()
    }
    private fun movement(p:MoneyPlan,account:Long)=MoneyMovement(accountId=account,amount=Money(1000,"COP"),direction=MoneyDirection.EXPENSE,date=due,spaceId=2,categoryId=p.categoryId,note="Confirmed")
    private suspend fun failure(action:suspend()->Unit):IllegalArgumentException=try{action();error("Expected rejection")}catch(e:IllegalArgumentException){e}
    @Test fun legacyBudgetIsPreservedUntilExplicitConfigurationAndCrudDoesNotTouchBalance()=runTest {
        account();val id=db.moneyPlanningDao().insertBudget(BudgetEntity(categoryId=0,limitAmount=50.25))
        assertNull(repo.budgetSnapshot().single().limit)
        repo.saveBudget(MoneyBudget(id,0,50.25,Money(5025,"USD"),null,"CUSTOM",75,due,due.plusDays(10)))
        val b=repo.budgetSnapshot().single();assertEquals("USD",b.limit!!.currencyCode);assertEquals(75,b.alertThreshold);assertEquals(due,b.startDate)
        assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits)
        repo.deleteBudget(id);assertTrue(repo.budgetSnapshot().isEmpty())
    }
    @Test fun budgetProgressRecomputesAfterLedgerDeleteAndUndo()=runTest {
        val a=account();val p=plan(a)
        val id=repo.saveBudget(MoneyBudget(0,0,10.0,Money(1000,"COP"),2,"MONTHLY"))
        val tx=money.record(movement(p,a));val budget=repo.budgetSnapshot().first{it.id==id}
        assertEquals(BudgetStatus.LIMIT,BudgetRules.progress(budget,money.observeMovements().let {db.moneyDao().readLedger().moneyMovements()},due,Locale.US)!!.status)
        val undo=money.deleteEntry(tx)
        assertEquals(0L,BudgetRules.progress(budget,db.moneyDao().readLedger().moneyMovements(),due,Locale.US)!!.spent.minorUnits)
        money.restore(undo);assertEquals(1000L,BudgetRules.progress(budget,db.moneyDao().readLedger().moneyMovements(),due,Locale.US)!!.spent.minorUnits)
    }
    @Test fun planAndEstimateNeverCreateTransactionsBeforeConfirmation()=runTest {
        val a=account();val p=plan(a,MoneyPlanKind.ESTIMATE)
        repo.snooze(p.id,p.version,due.plusDays(1));val snoozed=repo.planSnapshot().single()
        assertEquals(due,snoozed.nextDue);assertEquals(due.plusDays(1),snoozed.remindOn)
        repo.status(snoozed.id,snoozed.version,false)
        assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits);assertTrue(db.moneyDao().readLedger().single().movements.isEmpty())
    }
    @Test fun confirmationIsAtomicIdempotentAndAdvancesOneOccurrence()=runTest {
        val a=account();val p=plan(a);val row=movement(p,a)
        val id=repo.confirm(p.id,p.nextDue,p.version,row)
        assertEquals(id,repo.confirm(p.id,p.nextDue,p.version,row))
        assertEquals(99000L,money.snapshot().accounts.single().total.minorUnits)
        assertEquals(due.plusMonths(1),repo.planSnapshot().single().nextDue)
        assertEquals(PlanningFailure.CONFLICT,(failure{repo.confirm(p.id,p.nextDue,p.version,row.copy(amount=Money(1200,"COP")))} as PlanningException).reason)
    }
    @Test fun linkingImportedMovementDoesNotRecordItTwice()=runTest {
        val a=account();val p=plan(a);val row=movement(p,a)
        val existing=money.record(row.copy(attachmentPath="money/receipts/proof.png",entryRequestId="receipt-id"))
        repo.confirm(p.id,p.nextDue,p.version,row,existing)
        assertEquals(99000L,money.snapshot().accounts.single().total.minorUnits)
        assertEquals(1,db.moneyDao().readLedger().single().movements.size)
        assertEquals(existing,db.moneyPlanningDao().occurrence(p.id,p.nextDue.toEpochDay())!!.movementId)
    }
    @Test fun oneMovementCannotSettleTwoPlans()=runTest {
        val a=account();val p=plan(a);val row=movement(p,a);val existing=money.record(row)
        repo.confirm(p.id,p.nextDue,p.version,row,existing)
        repo.savePlan(p.copy(id="other",version=0))
        val second=repo.planSnapshot().first{it.id=="other"}
        assertEquals(PlanningFailure.ALREADY_LINKED,(failure{repo.confirm(second.id,second.nextDue,second.version,row,existing)} as PlanningException).reason)
        assertEquals(due,repo.planSnapshot().first{it.id=="other"}.nextDue)
    }
    @Test fun stalePlanOrInvalidCurrencyRollsBackAdvanceAndOccurrence()=runTest {
        val a=account();val p=plan(a)
        repo.savePlan(p.copy(name="Edited"))
        assertEquals(PlanningFailure.STALE,(failure{repo.confirm(p.id,p.nextDue,p.version,movement(p,a))} as PlanningException).reason)
        val current=repo.planSnapshot().single()
        failure{repo.confirm(current.id,current.nextDue,current.version,movement(current,a).copy(amount=Money(1000,"USD")))}
        assertNull(db.moneyPlanningDao().occurrence(current.id,due.toEpochDay()));assertEquals(due,repo.planSnapshot().single().nextDue)
        assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits)
    }
    @Test fun invalidCategoryCurrencyPocketAndBudgetRangesCannotWrite()=runTest {
        val a=account();val p=plan(a);val income=money.createCategory("Income",true)
        failure{repo.saveBudget(MoneyBudget(0,income,1.0,Money(100,"COP"),2,"MONTHLY"))}
        failure{repo.savePlan(p.copy(currency="USD",amount=Money(1000,"USD")))}
        failure{repo.confirm(p.id,due,p.version,movement(p,a).copy(categoryId=income))}
        failure{repo.confirm(p.id,due,p.version,movement(p,a).copy(pocketId=999))}
        assertTrue(repo.budgetSnapshot().isEmpty());assertNull(db.moneyPlanningDao().occurrence(p.id,due.toEpochDay()))
    }
    @Test fun skipAndArchiveKeepLedgerAndAdvanceOnlyOnce()=runTest {
        val a=account();val p=plan(a)
        repo.skip(p.id,p.nextDue,p.version);repo.skip(p.id,p.nextDue,p.version)
        val next=repo.planSnapshot().single();assertEquals(due.plusMonths(1),next.nextDue)
        repo.status(next.id,next.version,false,true)
        assertTrue(repo.planSnapshot().isEmpty());assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits)
    }
    @Test fun onceFinishesAndDeletedPaymentIsNotSilentlyRecreated()=runTest {
        val a=account();val original=plan(a)
        repo.savePlan(original.copy(frequency=MoneyFrequency.ONCE));val p=repo.planSnapshot().single()
        val id=repo.confirm(p.id,p.nextDue,p.version,movement(p,a));assertFalse(repo.planSnapshot().single().active)
        val token=money.deleteEntry(id)
        assertEquals(PlanningFailure.STALE,(failure{repo.confirm(p.id,p.nextDue,p.version,movement(p,a))} as PlanningException).reason)
        money.restore(token);assertEquals(id,repo.confirm(p.id,p.nextDue,p.version,movement(p,a)))
    }
    @Test fun alertLeasePreventsDuplicatesAndRecoversInterruptedNotification()=runTest {
        val token=repo.claimAlert("event",1_000_000)!!
        assertNull(repo.claimAlert("event",1_000_100));repo.releaseAlert("event","not-owner")
        assertNull(repo.claimAlert("event",1_000_100))
        val recovered=repo.claimAlert("event",1_600_001)!!;repo.alertDelivered("event",token)
        assertFalse(db.moneyPlanningDao().alert("event")!!.delivered)
        repo.alertDelivered("event",recovered);assertNull(repo.claimAlert("event",2_900_000))
    }
    @Test fun plansRetainTheirPurposeAndCannotBePaidFromAnotherSpace()=runTest {
        val a=account();val p=plan(a)
        assertEquals(2L,p.spaceId)
        val business=money.createAccount("Business",AccountType.CHECKING,Money(100000,"COP"),3)
        failure{repo.confirm(p.id,p.nextDue,p.version,movement(p,business).copy(spaceId=3))}
        assertNull(db.moneyPlanningDao().occurrence(p.id,p.nextDue.toEpochDay()))
        assertEquals(200000L,money.snapshot().currencies.single().minorUnits)
    }
}
