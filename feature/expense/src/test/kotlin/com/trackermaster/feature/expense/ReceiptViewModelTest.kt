package com.trackermaster.feature.expense

import androidx.lifecycle.*
import com.trackermaster.core.data.money.ReceiptSource
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
class ReceiptViewModelTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val stores=mutableListOf<ViewModelStore>()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){stores.forEach{it.clear()};Dispatchers.resetMain()}
    private fun vm(gateway:FakeReceiptGateway,handle:SavedStateHandle=SavedStateHandle()):ReceiptViewModel {
        val store=ViewModelStore().also{stores+=it}
        return ViewModelProvider(store,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=ReceiptViewModel(gateway,handle,dispatcher) as T
        })[ReceiptViewModel::class.java]
    }
    private fun ready(vm:ReceiptViewModel){vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(accountId=1,categoryId=1,confirmed=true)))}
    @Test fun suggestionsNeverChooseFundingOrConfirmBankMovement()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway();val vm=vm(gateway);vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle()
        assertEquals("50000",vm.state.value.form.amount);assertNull(vm.state.value.form.accountId)
        assertFalse(vm.state.value.form.confirmed);assertFalse(vm.state.value.canSave)
        vm.onEvent(ReceiptEvent.Save);assertTrue(gateway.confirmed.isEmpty())
        ready(vm);advanceUntilIdle();assertTrue(vm.state.value.canSave)
    }
    @Test fun repeatedSaveDuringCommitUsesOneStableReceiptId()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway();val vm=vm(gateway);vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle();ready(vm);advanceUntilIdle()
        val id=vm.state.value.receipt!!.id;gateway.hold=CompletableDeferred()
        vm.onEvent(ReceiptEvent.Save);vm.onEvent(ReceiptEvent.Save);assertEquals(1,gateway.confirmed.size)
        assertTrue(vm.state.value.saving);gateway.hold!!.complete(Unit);advanceUntilIdle()
        assertTrue(vm.state.value.saved);assertEquals(id,gateway.confirmed.single().first)
    }
    @Test fun duplicateAcknowledgementResetsAfterFinancialFieldsChange()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway().apply{matches=listOf(ReceiptDuplicate(12,ReceiptDuplicateReason.FILE))}
        val vm=vm(gateway);vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle();ready(vm);advanceUntilIdle()
        assertFalse(vm.state.value.canSave);vm.onEvent(ReceiptEvent.Acknowledge);advanceUntilIdle();assertTrue(vm.state.value.canSave)
        vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(note="Edited note")));advanceUntilIdle();assertTrue(vm.state.value.canSave)
        vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(amount="50001")));advanceUntilIdle()
        assertNull(vm.state.value.form.duplicateAcknowledgement);assertFalse(vm.state.value.canSave)
    }
    @Test fun processRecreationRestoresEditedDraftAndDoesNotReimportSource()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway();val handle=SavedStateHandle();val first=vm(gateway,handle)
        first.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle();ready(first)
        first.onEvent(ReceiptEvent.Update(first.state.value.form.copy(note="Keep",amount="123.45")));advanceUntilIdle()
        val id=first.state.value.receipt!!.id;stores.first().clear()
        val recreated=vm(gateway,SavedStateHandle(mapOf("receipt_id" to handle.get<String>("receipt_id"),"receipt_form" to handle.get<String>("receipt_form"))))
        advanceUntilIdle();assertEquals(id,recreated.state.value.receipt!!.id);assertEquals("123.45",recreated.state.value.form.amount)
        assertEquals("Keep",recreated.state.value.form.note);assertEquals(1,gateway.drafts.value.size)
    }
    @Test fun pendingOrFailedBankStatusCannotBeBypassedByConfirmation()=runTest(dispatcher) {
        for(text in listOf("Nequi\nPendiente","Nequi\nFallida")) {
            val gateway=FakeReceiptGateway().apply{this.text=text};val vm=vm(gateway)
            vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle()
            vm.onEvent(ReceiptEvent.Update(ReceiptDraft(ReceiptEntryKind.EXPENSE,"100","COP","2026-10-04",1,categoryId=1,confirmed=true)));advanceUntilIdle()
            assertFalse(vm.state.value.canSave);vm.onEvent(ReceiptEvent.Save);assertTrue(gateway.confirmed.isEmpty())
        }
    }
    @Test fun unreadableReceiptCanBeFilledManuallyWithExplicitRequiredFields()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway().apply{unreadable=true};val vm=vm(gateway)
        vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle()
        assertEquals("UNREADABLE",vm.state.value.receipt!!.stage);assertNull(vm.state.value.form.kind)
        vm.onEvent(ReceiptEvent.Update(ReceiptDraft(ReceiptEntryKind.EXPENSE,"100","COP","2026-10-04",1,categoryId=1,confirmed=true)));advanceUntilIdle()
        assertTrue(vm.state.value.canSave)
    }
    @Test fun duplicateReadFailureBlocksSaveUntilSuccessfulRetry()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway().apply{failDuplicates=true};val vm=vm(gateway)
        vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle();ready(vm);advanceUntilIdle()
        assertFalse(vm.state.value.canSave);assertEquals(ReceiptFailure.UNREADABLE,vm.state.value.error)
        gateway.failDuplicates=false;vm.onEvent(ReceiptEvent.Retry);advanceUntilIdle();assertTrue(vm.state.value.canSave)
    }
    @Test fun writeFailureRetainsDraftForRetryAndPrivacyUpdatesImmediately()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway();val vm=vm(gateway);vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle();ready(vm);advanceUntilIdle()
        val draft=vm.state.value.form;gateway.failSave=true;vm.onEvent(ReceiptEvent.Save);advanceUntilIdle()
        assertEquals(draft,vm.state.value.form);assertTrue(vm.state.value.saveError);assertFalse(vm.state.value.saved)
        vm.onEvent(ReceiptEvent.Privacy);assertTrue(vm.state.value.hidden);advanceUntilIdle()
        gateway.failSave=false;vm.onEvent(ReceiptEvent.Save);advanceUntilIdle();assertTrue(vm.state.value.saved)
    }
    @Test fun crossCurrencyAndSameFundingTransfersRequireCorrectTargetDetails()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway();val vm=vm(gateway);vm.receive(ReceiptSource.SharedText("proof"));advanceUntilIdle()
        vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(kind=ReceiptEntryKind.TRANSFER,accountId=1,targetAccountId=3,confirmed=true)));advanceUntilIdle()
        assertFalse(vm.state.value.canSave)
        vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(receivedAmount="10.50")));advanceUntilIdle();assertTrue(vm.state.value.canSave)
        vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(targetAccountId=1)));advanceUntilIdle();assertFalse(vm.state.value.canSave)
    }
    @Test fun privacyRemainsSynchronizedAfterItChangesInAnotherMoneyScreen()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway();val vm=vm(gateway)
        vm.onEvent(ReceiptEvent.Privacy);advanceUntilIdle();assertTrue(vm.state.value.hidden)
        gateway.preferences.value=gateway.preferences.value.copy(hideAmounts=false);advanceUntilIdle()
        assertFalse(vm.state.value.hidden)
    }
    @Test fun loadFailureCanBeRetriedWithoutLeavingStaleErrorOrAmountsVisible()=runTest(dispatcher) {
        val gateway=FakeReceiptGateway().apply{failInitialize=true};val vm=vm(gateway);advanceUntilIdle()
        assertNull(vm.state.value.workspace);assertNotNull(vm.state.value.error);assertTrue(vm.state.value.hidden)
        gateway.failInitialize=false;vm.onEvent(ReceiptEvent.Retry);advanceUntilIdle()
        assertNotNull(vm.state.value.workspace);assertNull(vm.state.value.error);assertFalse(vm.state.value.loading)
    }
}
