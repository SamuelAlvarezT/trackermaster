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
class MoneyInvestmentViewModelTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val stores=mutableListOf<ViewModelStore>()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){stores.forEach{it.clear()};Dispatchers.resetMain()}
    private fun vm(g:FakeInvestmentGateway,s:SavedStateHandle=SavedStateHandle()):MoneyInvestmentViewModel {
        val store=ViewModelStore().also{stores+=it}
        return ViewModelProvider(store,object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=MoneyInvestmentViewModel(g,s,dispatcher) as T})[MoneyInvestmentViewModel::class.java]
    }
    private fun operation(v:MoneyInvestmentViewModel,kind:InvestmentEventKind=InvestmentEventKind.CONTRIBUTION,amount:String="5000"){
        v.onAction(InvestmentAction.Operation(v.state.value.book.positions.first(),kind))
        v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(amount=amount)))
    }
    @Test fun oneSaveWhileWritingAndStableRequestAfterFailure()=runTest(dispatcher){val g=FakeInvestmentGateway();val v=vm(g);operation(v);val request=v.state.value.draft!!.request;g.hold=CompletableDeferred();g.fail=true;v.onAction(InvestmentAction.Save);v.onAction(InvestmentAction.Save);assertEquals(1,g.saves.size);g.hold!!.complete(Unit);advanceUntilIdle();assertTrue(v.state.value.error);assertEquals(request,v.state.value.draft!!.request);g.hold=null;g.fail=false;v.onAction(InvestmentAction.Save);advanceUntilIdle();assertEquals(2,g.saves.size);assertEquals(g.saves[0].request,g.saves[1].request)}
    @Test fun invalidAmountsAndDatesKeepObservationAlive()=runTest(dispatcher){val v=vm(FakeInvestmentGateway());operation(v,amount="-");assertFalse(v.state.value.canSave);v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(amount="5000",date="bad")));assertFalse(v.state.value.canSave);v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(date=java.time.LocalDate.now().toString())));assertTrue(v.state.value.canSave);assertFalse(v.state.value.loadError)}
    @Test fun contributionMustFitAvailableFunding()=runTest(dispatcher){val v=vm(FakeInvestmentGateway());operation(v,amount="999999999");assertFalse(v.state.value.canSave)}
    @Test fun valuationNeedsNoAccountAndAcceptsZero()=runTest(dispatcher){val v=vm(FakeInvestmentGateway());operation(v,InvestmentEventKind.VALUATION,"0");v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(account=null)));assertTrue(v.state.value.canSave)}
    @Test fun withdrawalCannotRecoverMoreCostThanHeld()=runTest(dispatcher){val v=vm(FakeInvestmentGateway());operation(v,InvestmentEventKind.WITHDRAWAL,"1000");v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(cost="999999")));assertFalse(v.state.value.canSave)}
    @Test fun draftSurvivesRecreationWithSameRequestAndFunding()=runTest(dispatcher){val g=FakeInvestmentGateway();val saved=SavedStateHandle();val first=vm(g,saved);operation(first);val draft=first.state.value.draft!!;val next=vm(g,SavedStateHandle(mapOf("investment_draft" to saved.get<String>("investment_draft"))));assertEquals(draft,next.state.value.draft)}
    @Test fun linkingReceiptCopiesOriginalMetadataAndAvoidsPlannedMovements()=runTest(dispatcher){val g=FakeInvestmentGateway();val v=vm(g);val m=g.workspace.value.ledger.movements.first{it.id==1L};operation(v,amount=m.amount.decimal.toPlainString());assertTrue(v.state.value.candidates.any{it.id==1L});g.occupied.value=setOf(1L);assertTrue(v.state.value.candidates.isEmpty());g.occupied.value=emptySet();v.onAction(InvestmentAction.Link(m));assertEquals(1L,v.state.value.draft!!.linked);assertEquals(m.note,v.state.value.draft!!.note);v.onAction(InvestmentAction.Link(null));assertNull(v.state.value.draft!!.linked)}
    @Test fun hiddenAmountsFollowExternalPreferenceChanges()=runTest(dispatcher){val g=FakeInvestmentGateway();val v=vm(g);v.onAction(InvestmentAction.Privacy);assertTrue(v.state.value.preferences.hideAmounts);g.preferences.value=g.preferences.value.copy(hideAmounts=false);assertFalse(v.state.value.preferences.hideAmounts)}
    @Test fun loadFailureCanRetryWithoutErasingDraft()=runTest(dispatcher){val g=FakeInvestmentGateway();g.failInitialize=true;val v=vm(g);assertTrue(v.state.value.loadError);g.failInitialize=false;v.onAction(InvestmentAction.Retry);assertFalse(v.state.value.loadError);assertFalse(v.state.value.loading)}
    @Test fun existingInvestmentCreationKeepsInitialValuesSeparateFromCashActions()=runTest(dispatcher){val g=FakeInvestmentGateway();val v=vm(g);v.onAction(InvestmentAction.New);v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(name="Existing",cost="3000",amount="3500")));assertTrue(v.state.value.canSave);v.onAction(InvestmentAction.Save);assertNull(g.saves.single().kind);assertNull(g.saves.single().account)}
    @Test fun openingCorrectionIsValidatedAndSavedAsMetadata()=runTest(dispatcher){val g=FakeInvestmentGateway();val v=vm(g);v.onAction(InvestmentAction.Edit(g.book.value.positions.single()));v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(cost="-1")));assertFalse(v.state.value.canSave);v.onAction(InvestmentAction.Draft(v.state.value.draft!!.copy(cost="50000",amount="53000")));assertTrue(v.state.value.canSave);v.onAction(InvestmentAction.Save);val saved=g.saves.single();assertTrue(saved.editing);assertNull(saved.kind);assertEquals("50000",saved.cost);assertEquals("53000",saved.amount)}
}
