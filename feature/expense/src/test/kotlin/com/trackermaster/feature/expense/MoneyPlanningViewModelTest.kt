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
class MoneyPlanningViewModelTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val stores=mutableListOf<ViewModelStore>()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){stores.forEach{it.clear()};Dispatchers.resetMain()}
    private fun vm(g:FakePlanningGateway,saved:SavedStateHandle=SavedStateHandle()):MoneyPlanningViewModel {
        val store=ViewModelStore().also{stores+=it}
        return ViewModelProvider(store,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel>create(modelClass:Class<T>):T=MoneyPlanningViewModel(g,saved,dispatcher) as T
        })[MoneyPlanningViewModel::class.java]
    }
    @Test fun blankAndInvalidBudgetCannotSaveButValidBudgetKeepsExplicitCurrencyAndScope()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.onEvent(PlanningEvent.OpenBudget());advanceUntilIdle()
        assertFalse(v.state.value.canSave)
        val f=v.state.value.controls.budget!!.copy(amount="100",currency="USD",space=5)
        v.onEvent(PlanningEvent.Budget(f));advanceUntilIdle();assertTrue(v.state.value.canSave)
        v.onEvent(PlanningEvent.Save);advanceUntilIdle();assertEquals("USD",g.budgets.single().limit!!.currencyCode);assertEquals(5L,g.budgets.single().spaceId)
    }
    @Test fun recurringPlanDoesNotCreateLedgerEntryAndCanOmitFundingUntilConfirmation()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.onEvent(PlanningEvent.OpenPlan());advanceUntilIdle()
        v.onEvent(PlanningEvent.Plan(v.state.value.controls.plan!!.copy(name="Data",amount="15",space=2)));advanceUntilIdle()
        assertTrue(v.state.value.canSave);v.onEvent(PlanningEvent.Save);advanceUntilIdle()
        assertEquals("Data",g.plans.single().name);assertTrue(g.confirmations.isEmpty())
    }
    @Test fun purchaseReminderMayOmitAmountButPaymentAndEstimateCannot()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.onEvent(PlanningEvent.OpenPlan());advanceUntilIdle()
        val f=v.state.value.controls.plan!!.copy(name="Hair product",kind=MoneyPlanKind.PURCHASE)
        v.onEvent(PlanningEvent.Plan(f));advanceUntilIdle();assertTrue(v.state.value.canSave)
        v.onEvent(PlanningEvent.Plan(f.copy(kind=MoneyPlanKind.ESTIMATE)));advanceUntilIdle();assertFalse(v.state.value.canSave)
    }
    @Test fun confirmationRequiresCompatibleFundingAndActualAmount()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);val plan=g.data.value.plans.first().copy(accountId=null,categoryId=null,amount=null)
        v.onEvent(PlanningEvent.OpenSettlement(plan));advanceUntilIdle();assertFalse(v.state.value.canSave)
        var f=v.state.value.controls.settlement!!.copy(amount="25",account=3,category=2)
        v.onEvent(PlanningEvent.Settlement(f));advanceUntilIdle();assertFalse(v.state.value.canSave)
        f=f.copy(account=1);v.onEvent(PlanningEvent.Settlement(f));advanceUntilIdle();assertTrue(v.state.value.canSave)
        v.onEvent(PlanningEvent.Save);advanceUntilIdle();assertEquals(2500L,g.confirmations.single().second.amount.minorUnits)
    }
    @Test fun repeatedSaveWhileWritingCallsGatewayOnce()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.onEvent(PlanningEvent.OpenSettlement(g.data.value.plans.first()));advanceUntilIdle()
        g.hold=CompletableDeferred();v.onEvent(PlanningEvent.Save);v.onEvent(PlanningEvent.Save);runCurrent()
        assertEquals(1,g.confirmations.size);assertTrue(v.state.value.controls.saving)
        g.hold!!.complete(Unit);advanceUntilIdle();assertNull(v.state.value.controls.settlement)
    }
    @Test fun failedWriteRetainsEditableDraftForRetry()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.onEvent(PlanningEvent.OpenSettlement(g.data.value.plans.first()));advanceUntilIdle()
        g.failWrite=true;v.onEvent(PlanningEvent.Save);advanceUntilIdle();assertNotNull(v.state.value.controls.settlement);assertEquals(PlanningUiError.SAVE,v.state.value.controls.error)
        g.failWrite=false;v.onEvent(PlanningEvent.Save);advanceUntilIdle();assertNull(v.state.value.controls.settlement)
    }
    @Test fun restoredDraftKeepsStablePlanIdAndFinancialSelections()=runTest(dispatcher){
        val g=FakePlanningGateway();val saved=SavedStateHandle();val first=vm(g,saved)
        first.onEvent(PlanningEvent.OpenPlan());advanceUntilIdle();val f=first.state.value.controls.plan!!.copy(name="Transport",amount="50",account=1,category=2,space=2)
        first.onEvent(PlanningEvent.Plan(f));advanceUntilIdle()
        val restored=vm(g,SavedStateHandle(mapOf("planning_draft" to saved.get<String>("planning_draft"))))
        advanceUntilIdle();assertEquals(f,restored.state.value.controls.plan);assertTrue(restored.state.value.canSave)
    }
    @Test fun linkingExistingMovementCopiesItsDateAndNeverRequestsANewEntry()=runTest(dispatcher){
        val g=FakePlanningGateway();val tx=g.data.value.ledger.movements.first{it.transferId==null && it.direction==MoneyDirection.EXPENSE}
        val v=vm(g);v.onEvent(PlanningEvent.OpenSettlement(g.data.value.plans.first().copy(amount=tx.amount,categoryId=tx.categoryId)));advanceUntilIdle()
        assertTrue(v.state.value.candidates.any{it.id==tx.id});v.onEvent(PlanningEvent.Link(tx.id));advanceUntilIdle()
        assertEquals(tx.id,v.state.value.controls.settlement!!.linked);assertEquals(tx.date.toString(),v.state.value.controls.settlement!!.date)
        v.onEvent(PlanningEvent.Save);advanceUntilIdle();assertEquals(tx.id,g.confirmations.single().first.linked)
    }
    @Test fun incompleteSettlementAmountKeepsObservationAliveAndRecoversCandidates()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g)
        val tx=g.data.value.ledger.movements.first{it.transferId==null && it.direction==MoneyDirection.EXPENSE}
        v.onEvent(PlanningEvent.OpenSettlement(g.data.value.plans.first().copy(amount=tx.amount,categoryId=tx.categoryId,
            accountId=tx.accountId,pocketId=tx.pocketId,spaceId=tx.spaceId)));advanceUntilIdle()
        val form=v.state.value.controls.settlement!!.copy(date=tx.date.toString())
        v.onEvent(PlanningEvent.Settlement(form));advanceUntilIdle();assertTrue(v.state.value.candidates.any{it.id==tx.id})
        listOf("","invalid","0").forEach{amount->
            v.onEvent(PlanningEvent.Settlement(form.copy(amount=amount)));advanceUntilIdle()
            assertFalse(v.state.value.canSave);assertTrue(v.state.value.candidates.isEmpty())
            assertNull(v.state.value.controls.error);assertNotNull(v.state.value.workspace)
        }
        v.onEvent(PlanningEvent.Settlement(form));advanceUntilIdle()
        assertTrue(v.state.value.canSave);assertTrue(v.state.value.candidates.any{it.id==tx.id})
        assertTrue(g.confirmations.isEmpty())
    }
    @Test fun unassignedPlanCanLinkReceiptFromPocketWithDifferentPurposeThanItsAccount()=runTest(dispatcher){
        val g=FakePlanningGateway();val data=g.data.value
        val tx=data.ledger.movements.first{it.transferId==null && it.direction==MoneyDirection.EXPENSE}
        val account=data.ledger.snapshot.accounts.first{it.account.id==tx.accountId}.account
        val purpose=data.spaces.first{it.id!=account.spaceId}.id
        val pocket=MoneyPocket(999,account.id,purpose,"Business fund")
        val paid=tx.copy(id=999,pocketId=pocket.id,spaceId=purpose,date=java.time.LocalDate.now())
        g.data.value=data.copy(ledger=data.ledger.copy(
            snapshot=data.ledger.snapshot.copy(pockets=data.ledger.snapshot.pockets+MoneyPocketBalance(pocket,Money(0,tx.amount.currencyCode))),
            movements=data.ledger.movements+paid))
        val v=vm(g);v.onEvent(PlanningEvent.OpenSettlement(data.plans.first().copy(amount=paid.amount,categoryId=paid.categoryId,
            accountId=account.id,pocketId=pocket.id,spaceId=null)));advanceUntilIdle()
        assertTrue(v.state.value.canSave);assertTrue(v.state.value.candidates.any{it.id==paid.id})
        v.onEvent(PlanningEvent.Link(paid.id));advanceUntilIdle();v.onEvent(PlanningEvent.Save);advanceUntilIdle()
        assertEquals(paid.id,g.confirmations.single().first.linked);assertEquals(purpose,g.confirmations.single().second.spaceId)
    }
    @Test fun editingFinancialFieldsClearsExistingMovementLink()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.onEvent(PlanningEvent.OpenSettlement(g.data.value.plans.first()));advanceUntilIdle()
        val tx=g.data.value.ledger.movements.first();v.onEvent(PlanningEvent.Link(tx.id));advanceUntilIdle()
        v.onEvent(PlanningEvent.Settlement(v.state.value.controls.settlement!!.copy(amount="20")));advanceUntilIdle();assertNull(v.state.value.controls.settlement!!.linked)
    }
    @Test fun permissionDenialAndSnoozeNeverConfirmPayments()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);assertFalse(v.state.value.notificationsAllowed)
        val p=g.data.value.plans.first();v.onEvent(PlanningEvent.Snooze(p));advanceUntilIdle()
        assertEquals(p.nextDue,g.snoozes.single().first.nextDue);assertEquals(v.state.value.controls.today.plusDays(1),g.snoozes.single().second)
        v.onEvent(PlanningEvent.Skip(p));advanceUntilIdle();assertTrue(g.confirmations.isEmpty())
    }
    @Test fun loaderFailureHidesFinancialContentAndRetryRecovers()=runTest(dispatcher){
        val g=FakePlanningGateway();g.failInitialize=true;val v=vm(g);advanceUntilIdle()
        assertNull(v.state.value.workspace);assertTrue(v.state.value.preferences.hideAmounts)
        g.failInitialize=false;v.onEvent(PlanningEvent.Retry);advanceUntilIdle();assertNotNull(v.state.value.workspace)
    }
    @Test fun externalPrivacyChangesRemainVisibleAfterLocalToggle()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.onEvent(PlanningEvent.Privacy);advanceUntilIdle();assertTrue(v.state.value.preferences.hideAmounts)
        g.preferences.value=g.preferences.value.copy(hideAmounts=false);advanceUntilIdle();assertFalse(v.state.value.preferences.hideAmounts)
    }
    @Test fun budgetPastPeriodAndSpaceFilteringDoesNotMixBusinessOrUsdPlans()=runTest(dispatcher){
        val g=FakePlanningGateway();val v=vm(g);v.start("plans","COP",3,null);advanceUntilIdle();assertTrue(v.state.value.plans.isEmpty())
        assertTrue(g.confirmations.isEmpty())
    }
}
