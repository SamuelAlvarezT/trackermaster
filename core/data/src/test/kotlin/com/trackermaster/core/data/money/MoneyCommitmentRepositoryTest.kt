package com.trackermaster.core.data.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.MoneyDatabaseCallback
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyCommitmentRepositoryTest {
    private lateinit var db:TrackermasterDatabase
    private lateinit var money:MoneyRepository
    private lateinit var repo:MoneyCommitmentRepository
    private val date=LocalDate.of(2026,10,15)
    @Before fun before(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),TrackermasterDatabase::class.java).addCallback(MoneyDatabaseCallback).allowMainThreadQueries().build();money=MoneyRepository(db);repo=MoneyCommitmentRepository(db,money)}
    @After fun after(){db.close()}
    private suspend fun account(code:String="COP",space:Long=2)=money.createAccount("Nequi",AccountType.CHECKING,Money(100000,code),space)
    private fun debt(kind:DebtKind=DebtKind.PAYABLE,code:String="COP",space:Long=2)=MoneyDebt("d","Person",kind,Money(10000,code),space,date.minusDays(1))
    private fun movement(a:Long,amount:Long=2500,kind:DebtKind=DebtKind.PAYABLE,space:Long=2,code:String="COP")=MoneyMovement(accountId=a,amount=Money(amount,code),direction=CommitmentRules.direction(kind,PrincipalEvent.REPAYMENT),date=date,spaceId=space,note="Payment")
    private suspend fun rejects(block:suspend()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun existingDebtDoesNotChangeCashAndCreateRetryIsIdempotent()=runTest {account();repo.createDebt(debt());repo.createDebt(debt());assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(1,repo.data.first().debts.size);assertTrue(repo.data.first().payments.isEmpty())}
    @Test fun receivingNewLoanChangesCashButNotOperatingIncomeAndRetryDoesNotDuplicate()=runTest {val a=account();val d=debt();val tx=movement(a,10000).copy(direction=MoneyDirection.INCOME);repo.createDebt(d,tx);repo.createDebt(d,tx);assertEquals(110000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(0L,MoneyLedger.periodTotals(money.observeMovements().first(),"COP",date,date).income.minorUnits);assertEquals(1,repo.data.first().payments.size)}
    @Test fun lendingAndCollectingAreCashMovementsNotIncomeOrExpense()=runTest {val a=account();val d=debt(DebtKind.RECEIVABLE);repo.createDebt(d,movement(a,10000).copy(direction=MoneyDirection.EXPENSE));repo.repay(d.id,0,"p",movement(a,2500,DebtKind.RECEIVABLE));assertEquals(92500L,money.snapshot().accounts.single().total.minorUnits);val totals=MoneyLedger.periodTotals(money.observeMovements().first(),"COP",date,date);assertEquals(0L,totals.income.minorUnits);assertEquals(0L,totals.expenses.minorUnits)}
    @Test fun repaymentIsAtomicIdempotentAndCannotOverpay()=runTest {val a=account();val d=debt();repo.createDebt(d);val m=movement(a);val id=repo.repay(d.id,0,"p",m);assertEquals(id,repo.repay(d.id,0,"p",m));rejects{repo.repay(d.id,0,"p",m.copy(amount=Money(3000,"COP")))};rejects{repo.repay(d.id,1,"q",movement(a,7501))};assertEquals(97500L,money.snapshot().accounts.single().total.minorUnits);assertEquals(1,repo.data.first().payments.size)}
    @Test fun linkingReceiptKeepsOneCashMovementAndPreservesAttachment()=runTest {val a=account();val d=debt();repo.createDebt(d);val category=money.createCategory("Loan",false);val m=movement(a);val id=money.record(m.copy(categoryId=category,attachmentPath="money/receipts/proof.pdf"));repo.repay(d.id,0,"p",m,id);assertEquals(97500L,money.snapshot().accounts.single().total.minorUnits);assertEquals(1,money.observeMovements().first().size);assertTrue(db.moneyDao().movement(id)!!.principal);assertEquals("money/receipts/proof.pdf",db.moneyDao().movement(id)!!.imageUri)}
    @Test fun sameMovementCannotRepayTwoDebtsOrAPlan()=runTest {val a=account();val d=debt();repo.createDebt(d);repo.createDebt(d.copy(id="other"));val category=money.createCategory("Loan",false);val m=movement(a);val id=money.record(m.copy(categoryId=category));repo.repay(d.id,0,"p",m,id);rejects{repo.repay("other",0,"q",m,id)};assertEquals(1,repo.data.first().payments.size)}
    @Test fun generalHistoryCannotEditOrDeletePrincipal()=runTest {val a=account();repo.createDebt(debt());val id=repo.repay("d",0,"p",movement(a));rejects{money.deleteEntry(id)};rejects{money.record(movement(a).copy(id=id,categoryId=money.createCategory("Other",false)))};assertEquals(97500L,money.snapshot().accounts.single().total.minorUnits)}
    @Test fun reversingCreatedPaymentRestoresCashAndOutstanding()=runTest {val a=account();repo.createDebt(debt());repo.repay("d",0,"p",movement(a));repo.reversePayment("p",1);assertTrue(repo.data.first().payments.isEmpty());assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(2L,repo.data.first().debts.single().version)}
    @Test fun reversingLinkedPaymentKeepsImportedMovement()=runTest {val a=account();repo.createDebt(debt());val category=money.createCategory("Loan",false);val id=money.record(movement(a).copy(categoryId=category));repo.repay("d",0,"p",movement(a),id);repo.reversePayment("p",1);assertFalse(db.moneyDao().movement(id)!!.principal);assertEquals(97500L,money.snapshot().accounts.single().total.minorUnits)}
    @Test fun staleAndWrongPurposePaymentsRollBack()=runTest {val a=account();repo.createDebt(debt());repo.updateDebt(debt().copy(person="Edited"));rejects{repo.repay("d",0,"p",movement(a))};rejects{repo.repay("d",1,"p",movement(a,space=3))};assertTrue(repo.data.first().payments.isEmpty());assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits)}
    @Test fun dueRemindersExcludeFullyPaidAndArchivedDebts()=runTest {val a=account();repo.createDebt(debt());assertEquals(1,repo.dueDebts(date).size);repo.repay("d",0,"p",movement(a,10000));assertTrue(repo.dueDebts(date).isEmpty());repo.createDebt(debt().copy(id="archived"));rejects{repo.updateDebt(debt().copy(id="archived",archived=true))};repo.repay("archived",0,"second",movement(a,10000));repo.updateDebt(debt().copy(id="archived",version=1,archived=true));assertTrue(repo.dueDebts(date).isEmpty())}
    @Test fun goalCreatesEmptyPocketWithoutChangingCashAndRetryCreatesNoSecondPocket()=runTest {val a=account();val g=SavingsGoal("g","Travel",Money(50000,"COP"),0);repo.createGoalWithPocket(g,a,4);repo.createGoalWithPocket(g,a,4);assertEquals(1,money.snapshot().pockets.size);assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(0L,money.snapshot().pockets.single().balance.minorUnits)}
    @Test fun contributionsAndWithdrawalsUseRealPairedTransfersAndAreIdempotent()=runTest {val a=account();repo.createGoalWithPocket(SavingsGoal("g","Travel",Money(50000,"COP"),0),a,4);repo.allocate("g",0,Money(30000,"COP"),a,false,date,"add");repo.allocate("g",0,Money(30000,"COP"),a,false,date,"add");assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(70000L,money.snapshot().accounts.single().unallocated.minorUnits);repo.allocate("g",0,Money(10000,"COP"),a,true,date,"take");assertEquals(20000L,money.snapshot().pockets.single().balance.minorUnits);assertEquals(0L,MoneyLedger.periodTotals(money.observeMovements().first(),"COP",date,date).expenses.minorUnits)}
    @Test fun insufficientContributionOrWithdrawalRollsBackBothLegs()=runTest {val a=account();repo.createGoalWithPocket(SavingsGoal("g","Travel",Money(50000,"COP"),0),a,4);rejects{repo.allocate("g",0,Money(100001,"COP"),a,false,date,"add")};rejects{repo.allocate("g",0,Money(1,"COP"),a,true,date,"take")};assertTrue(money.observeMovements().first().isEmpty())}
    @Test fun onePocketCannotBeCountedForTwoGoalsAndCrossCurrencyIsRejected()=runTest {val a=account();val p=money.createPocket(a,4,"Travel");repo.saveGoal(SavingsGoal("g","Travel",Money(50000,"COP"),p));try{repo.saveGoal(SavingsGoal("second","Other",Money(10000,"COP"),p));fail("Expected unique pocket")}catch(_:android.database.sqlite.SQLiteConstraintException){};rejects{repo.saveGoal(SavingsGoal("usd","USD",Money(100,"USD"),p))};assertEquals(1,repo.data.first().goals.size)}
    @Test fun cashlessDebtCanCorrectItsPrincipalAndClassificationBeforePayments()=runTest {account();repo.createDebt(debt());repo.updateDebt(debt(DebtKind.RECEIVABLE,"USD",5).copy(principal=Money(25000,"USD")));val d=repo.data.first().debts.single();assertEquals(DebtKind.RECEIVABLE,d.kind);assertEquals(25000L,d.principal.minorUnits);assertEquals("USD",d.principal.currencyCode);assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits)}
    @Test fun correctingPrincipalUpdatesOriginalDisbursementAndCashAtomically()=runTest {val a=account();repo.createDebt(debt(),movement(a,10000).copy(direction=MoneyDirection.INCOME));repo.updateDebt(debt().copy(principal=Money(12000,"COP")));assertEquals(112000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(12000L,money.observeMovements().first().single().amount.minorUnits);assertTrue(money.observeMovements().first().single().principal)}
    @Test fun principalCannotBeCorrectedBelowRecordedRepayments()=runTest {val a=account();repo.createDebt(debt());repo.repay("d",0,"p",movement(a,2500));rejects{repo.updateDebt(debt().copy(version=1,principal=Money(2499,"COP")))};assertEquals(10000L,repo.data.first().debts.single().principal.minorUnits);assertEquals(97500L,money.snapshot().accounts.single().total.minorUnits)}
    @Test fun principalCorrectionCannotLeaveReservedMoneyUnfunded()=runTest {val a=account();repo.createDebt(debt(),movement(a,10000).copy(direction=MoneyDirection.INCOME));val p=money.createPocket(a,4,"Reserve");money.transfer(a,a,Money(110000,"COP"),targetPocketId=p,requestId="reserve");rejects{repo.updateDebt(debt().copy(principal=Money(5000,"COP")))};assertEquals(110000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(10000L,repo.data.first().debts.single().principal.minorUnits)}
    @Test fun planConfirmationAndDebtRepaymentCannotClaimTheSameMovement()=runTest {
        val a=account();repo.createDebt(debt());val category=money.createCategory("Data",false)
        val planning=MoneyPlanningRepository(db,money)
        val p=MoneyPlan("plan","Data",MoneyPlanKind.PAYMENT,MoneyDirection.EXPENSE,Money(2500,"COP"),"COP",a,null,category,date,date,MoneyFrequency.MONTHLY,spaceId=2)
        planning.savePlan(p);val m=movement(a).copy(categoryId=category)
        val plannedId=planning.confirm(p.id,date,1,m)
        rejects{repo.repay("d",0,"p",m,plannedId)};assertFalse(db.moneyDao().movement(plannedId)!!.principal)
        val principalId=repo.repay("d",0,"q",movement(a));planning.savePlan(p.copy(id="other"))
        rejects{planning.confirm("other",date,1,m,principalId)}
        assertNull(db.moneyPlanningDao().occurrence("other",date.toEpochDay()));assertEquals(1,repo.data.first().payments.size)
    }
    @Test fun goalsAcceptLegacyLowercaseAccountCurrenciesWithoutRewritingThem()=runTest {
        val a=account("USD",5);db.moneyDao().updateAccount(db.moneyDao().account(a)!!.copy(currencyCode="usd"))
        repo.createGoalWithPocket(SavingsGoal("g","Travel",Money(50000,"USD"),0),a,5)
        repo.allocate("g",0,Money(10000,"USD"),a,false,date,"contribute")
        assertEquals("usd",db.moneyDao().account(a)!!.currencyCode);assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits);assertEquals(10000L,money.snapshot().pockets.single().balance.minorUnits)
    }
}
