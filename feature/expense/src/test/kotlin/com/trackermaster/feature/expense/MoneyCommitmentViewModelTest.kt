package com.trackermaster.feature.expense

import androidx.lifecycle.*
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyCommitmentViewModelTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val stores=mutableListOf<ViewModelStore>()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){stores.forEach{it.clear()};Dispatchers.resetMain()}
    private fun vm(g:FakeCommitmentGateway,saved:SavedStateHandle=SavedStateHandle()):MoneyCommitmentViewModel {
        val store=ViewModelStore().also{stores+=it}
        return ViewModelProvider(store,object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):T=MoneyCommitmentViewModel(g,saved,dispatcher) as T
        })[MoneyCommitmentViewModel::class.java]
    }
    @Test fun existingDebtRequiresPositiveAmountAndKeepsCashSeparate()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);v.onEvent(CommitmentEvent.New(CommitmentEditor.DEBT));assertFalse(v.state.value.canSave);v.onEvent(CommitmentEvent.Draft(v.state.value.controls.draft!!.copy(name="Sister",currency="USD",space=5,amount="2500")));assertTrue(v.state.value.canSave);v.onEvent(CommitmentEvent.Save);advanceUntilIdle();assertEquals("USD",g.debts.single().first.principal.currencyCode);assertNull(g.debts.single().second)}
    @Test fun newLoanRequiresAccountWithMatchingPurposeAndCurrency()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);v.onEvent(CommitmentEvent.New(CommitmentEditor.DEBT));val f=v.state.value.controls.draft!!.copy(name="Sister",currency="USD",space=5,amount="2500",disburse=true,account=1);v.onEvent(CommitmentEvent.Draft(f));assertFalse(v.state.value.canSave);v.onEvent(CommitmentEvent.Draft(f.copy(account=3)));assertTrue(v.state.value.canSave);v.onEvent(CommitmentEvent.Save);assertEquals(MoneyDirection.INCOME,g.debts.single().second!!.direction)}
    @Test fun partialAmountsDoNotStopObservationAndOverpaymentCannotSave()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);v.onEvent(CommitmentEvent.Repay(g.commitments.value.debts.first{it.id=="friend"}));val f=v.state.value.controls.draft!!;v.onEvent(CommitmentEvent.Draft(f.copy(amount=".")));assertFalse(v.state.value.canSave);v.onEvent(CommitmentEvent.Draft(f.copy(amount="180000.01")));assertFalse(v.state.value.canSave);v.onEvent(CommitmentEvent.Draft(f.copy(amount="50")));assertTrue(v.state.value.canSave)}
    @Test fun repaymentLinksMatchingReceiptWithoutASecondLedgerWrite()=runTest(dispatcher){val g=FakeCommitmentGateway();val tx=g.workspace.value.ledger.movements.first{it.id==1L};val d=g.commitments.value.debts.first{it.id=="friend"}.copy(kind=DebtKind.PAYABLE);g.commitments.value=g.commitments.value.copy(debts=listOf(d));val v=vm(g);v.onEvent(CommitmentEvent.Repay(d));v.onEvent(CommitmentEvent.Draft(v.state.value.controls.draft!!.copy(amount=tx.amount.decimal.toPlainString())));assertEquals(tx.id,v.state.value.candidates.single().id);v.onEvent(CommitmentEvent.Link(tx.id));v.onEvent(CommitmentEvent.Save);assertEquals(tx.id,g.repayments.single().first.linked);assertEquals(tx.note,g.repayments.single().second.note)}
    @Test fun plannedMovementsAreNotSuggestedForLoanRepayment()=runTest(dispatcher){val g=FakeCommitmentGateway();val tx=g.workspace.value.ledger.movements.first();val d=g.commitments.value.debts.first{it.id=="friend"}.copy(kind=DebtKind.PAYABLE);g.commitments.value=MoneyCommitments(debts=listOf(d),plannedMovementIds=setOf(tx.id));val v=vm(g);v.onEvent(CommitmentEvent.Repay(d));v.onEvent(CommitmentEvent.Draft(v.state.value.controls.draft!!.copy(amount=tx.amount.decimal.toPlainString())));assertTrue(v.state.value.candidates.isEmpty())}
    @Test fun failedSaveKeepsDraftAndRetryRequestStable()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);v.onEvent(CommitmentEvent.New(CommitmentEditor.DEBT));v.onEvent(CommitmentEvent.Draft(v.state.value.controls.draft!!.copy(name="Person",amount="100")));val id=v.state.value.controls.draft!!.id;g.failWrite=true;v.onEvent(CommitmentEvent.Save);assertEquals(id,v.state.value.controls.draft!!.id);assertEquals("SAVE",v.state.value.controls.error);g.failWrite=false;v.onEvent(CommitmentEvent.Save);assertNull(v.state.value.controls.draft);assertEquals(id,g.debts.single().first.id)}
    @Test fun repeatedSaveWhilePendingOnlyWritesOnce()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);v.onEvent(CommitmentEvent.Repay(g.commitments.value.debts.first{it.id=="friend"}));v.onEvent(CommitmentEvent.Draft(v.state.value.controls.draft!!.copy(amount="10")));g.hold=CompletableDeferred();v.onEvent(CommitmentEvent.Save);v.onEvent(CommitmentEvent.Save);runCurrent();assertEquals(1,g.repayments.size);g.hold!!.complete(Unit);advanceUntilIdle();assertNull(v.state.value.controls.draft)}
    @Test fun savedDraftPreservesStableIdsAndSelections()=runTest(dispatcher){val g=FakeCommitmentGateway();val saved=SavedStateHandle();val first=vm(g,saved);first.onEvent(CommitmentEvent.New(CommitmentEditor.GOAL));first.onEvent(CommitmentEvent.Draft(first.state.value.controls.draft!!.copy(name="Travel",amount="500")));val before=first.state.value.controls.draft;val restored=vm(g,saved);assertEquals(before,restored.state.value.controls.draft)}
    @Test fun goalContributionsCannotExceedAvailableCashAndWithdrawalsCannotExceedReserve()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);val goal=g.commitments.value.goals.single();v.onEvent(CommitmentEvent.Allocate(goal,true));val f=v.state.value.controls.draft!!;v.onEvent(CommitmentEvent.Draft(f.copy(amount="20000.01")));assertFalse(v.state.value.canSave);v.onEvent(CommitmentEvent.Draft(f.copy(amount="20000")));assertTrue(v.state.value.canSave);v.onEvent(CommitmentEvent.Save);assertEquals(2000000L,g.allocations.single().second.minorUnits)}
    @Test fun privacyTracksChangesFromOtherScreens()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);v.onEvent(CommitmentEvent.Privacy);assertTrue(v.state.value.preferences.hideAmounts);g.preferences.value=g.preferences.value.copy(hideAmounts=false);assertFalse(v.state.value.preferences.hideAmounts)}
    @Test fun loadFailureCanRetryWithoutShowingFinancialData()=runTest(dispatcher){val g=FakeCommitmentGateway();g.failLoad=true;val v=vm(g);assertNull(v.state.value.workspace);assertTrue(v.state.value.preferences.hideAmounts);g.failLoad=false;v.onEvent(CommitmentEvent.Retry);assertNotNull(v.state.value.workspace)}
    @Test fun savingADebtInAnotherCurrencyUpdatesTheVisibleScope()=runTest(dispatcher){val g=FakeCommitmentGateway();val v=vm(g);v.onEvent(CommitmentEvent.Space(2));v.onEvent(CommitmentEvent.New(CommitmentEditor.DEBT));v.onEvent(CommitmentEvent.Draft(v.state.value.controls.draft!!.copy(name="Sister",currency="USD",space=5,amount="2500")));v.onEvent(CommitmentEvent.Save);assertEquals("USD",v.state.value.controls.currency);assertEquals(5L,v.state.value.controls.space)}
}
