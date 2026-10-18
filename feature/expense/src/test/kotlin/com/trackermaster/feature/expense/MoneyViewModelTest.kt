package com.trackermaster.feature.expense

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.trackermaster.core.data.money.MoneyPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun vm(gateway: FakeMoneyGateway, state: SavedStateHandle = SavedStateHandle()): MoneyViewModel {
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MoneyViewModel(gateway, state, dispatcher) as T
        })[MoneyViewModel::class.java]
    }
    private fun draft(vm: MoneyViewModel, amount: String = "5000") {
        vm.onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE))
        vm.onEvent(MoneyEvent.Editor(vm.uiState.value.controls.editor!!.copy(amount = amount)))
    }
    @Test fun widgetRequestsOpenAnEditorOnceAndDoNotOverwriteADraft()=runTest(dispatcher) {
        val vm=vm(FakeMoneyGateway())
        assertTrue(vm.openFromWidget("first","INCOME"))
        assertEquals(MoneyEntryKind.INCOME,vm.uiState.value.controls.editor!!.kind)
        vm.onEvent(MoneyEvent.Editor(vm.uiState.value.controls.editor!!.copy(amount="100",note="Keep")))
        assertTrue(vm.openFromWidget("first","INCOME"))
        assertTrue(vm.openFromWidget("second","EXPENSE"))
        assertEquals("Keep",vm.uiState.value.controls.editor!!.note)
        assertEquals(MoneyEntryKind.INCOME,vm.uiState.value.controls.editor!!.kind)
        vm.onEvent(MoneyEvent.CloseEditor)
        assertTrue(vm.openFromWidget("second","EXPENSE"));assertNull(vm.uiState.value.controls.editor)
        assertTrue(vm.openFromWidget("third","EXPENSE"));assertEquals(MoneyEntryKind.EXPENSE,vm.uiState.value.controls.editor!!.kind)
    }
    @Test fun privacyChangesFromReceiptScreenAreObservedAfterLocalToggle()=runTest(dispatcher) {
        val gateway=FakeMoneyGateway();val vm=vm(gateway)
        vm.onEvent(MoneyEvent.Privacy);advanceUntilIdle();assertTrue(vm.uiState.value.preferences.hideAmounts)
        gateway.preferences.value=gateway.preferences.value.copy(hideAmounts=false);advanceUntilIdle()
        assertFalse(vm.uiState.value.preferences.hideAmounts)
    }
    @Test fun doubleSaveCommitsOneMovementAndUpdatesBalanceExactly() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway(); val vm = vm(gateway); val before = vm.uiState.value.overview.balance
        draft(vm); gateway.hold = CompletableDeferred()
        vm.onEvent(MoneyEvent.SaveEntry); vm.onEvent(MoneyEvent.SaveEntry)
        assertEquals(1, gateway.recorded.size); assertTrue(vm.uiState.value.controls.saving)
        gateway.hold!!.complete(Unit); advanceUntilIdle()
        assertNull(vm.uiState.value.controls.editor)
        assertEquals(before.minorUnits - 500000, vm.uiState.value.overview.balance.minorUnits)
        assertEquals(1L, gateway.preferences.value.lastAccountId)
    }
    @Test fun writeFailurePreservesAmountDateNoteAndRequestForRetry() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway(); val vm = vm(gateway); draft(vm)
        val day = LocalDate.of(2026, 9, 17)
        vm.onEvent(MoneyEvent.Editor(vm.uiState.value.controls.editor!!.copy(note = "Keep me", date = day)))
        val original = vm.uiState.value.controls.editor!!
        gateway.failWrites = true; vm.onEvent(MoneyEvent.SaveEntry); advanceUntilIdle()
        val retained = vm.uiState.value.controls.editor!!
        assertEquals(original.requestId, retained.requestId); assertEquals("5000", retained.amount)
        assertEquals("Keep me", retained.note); assertEquals(day, retained.date); assertEquals(MoneyUiError.SAVE, retained.error)
        gateway.failWrites = false; vm.onEvent(MoneyEvent.SaveEntry); advanceUntilIdle()
        assertNull(vm.uiState.value.controls.editor)
        assertEquals(1, gateway.data.value.ledger.movements.count { it.entryRequestId == original.requestId })
    }
    @Test fun preferencesFailureAfterCommitDoesNotPresentMovementAsUnsaved() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway(); val vm = vm(gateway); draft(vm); gateway.failPreferences = true
        vm.onEvent(MoneyEvent.SaveEntry); advanceUntilIdle()
        assertNull(vm.uiState.value.controls.editor); assertNull(vm.uiState.value.controls.error)
        assertEquals(MoneyNoticeKind.PREFERENCES_FAILED, vm.uiState.value.controls.notice!!.kind)
        assertEquals(1, gateway.recorded.size)
    }
    @Test fun recreatedViewModelRestoresStableDraftRequestIdAndDetails() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway(); val handle = SavedStateHandle(); val first = vm(gateway, handle); draft(first)
        first.onEvent(MoneyEvent.Editor(first.uiState.value.controls.editor!!.copy(note = "Receipt", date = LocalDate.of(2026, 8, 7))))
        val bundle = handle.get<Bundle>("money_draft")!!
        val second = vm(gateway, SavedStateHandle(mapOf("money_draft" to Bundle(bundle))))
        val restored = second.uiState.value.controls.editor!!
        assertEquals(first.uiState.value.controls.editor!!.requestId, restored.requestId)
        assertEquals("5000", restored.amount); assertEquals("Receipt", restored.note); assertEquals(LocalDate.of(2026, 8, 7), restored.date)
        second.onEvent(MoneyEvent.SaveEntry); advanceUntilIdle(); assertNull(second.uiState.value.controls.editor)
    }
    @Test fun readErrorFailsClosedAndRetryRestoresContent() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway().apply { failInitialize = true }; val vm = vm(gateway)
        assertEquals(MoneyLoadState.ERROR, vm.uiState.value.load); assertTrue(vm.uiState.value.preferences.hideAmounts)
        gateway.failInitialize = false; vm.onEvent(MoneyEvent.Retry); advanceUntilIdle()
        assertEquals(MoneyLoadState.CONTENT, vm.uiState.value.load)
    }
    @Test fun privacyIsImmediateEvenIfPersistenceFailsAndOtherEventsArrive() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway(); val vm = vm(gateway); gateway.failPreferences = true
        vm.onEvent(MoneyEvent.Privacy); assertTrue(vm.uiState.value.preferences.hideAmounts)
        vm.onEvent(MoneyEvent.Navigate(MoneyDestination.MOVEMENTS)); advanceUntilIdle()
        assertTrue(vm.uiState.value.preferences.hideAmounts)
        assertEquals(MoneyUiError.SAVE, vm.uiState.value.controls.error)
    }
    @Test fun appearanceKeepsSpacePeriodAndFilters() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway(); val vm = vm(gateway)
        vm.onEvent(MoneyEvent.Space(5)); vm.onEvent(MoneyEvent.Period(MoneyPeriodKind.YEAR)); vm.onEvent(MoneyEvent.Filters(MoneyFilters("Travel")))
        vm.onEvent(MoneyEvent.Appearance(true, true)); advanceUntilIdle()
        assertEquals(5L, vm.uiState.value.controls.spaceId); assertEquals("USD", vm.uiState.value.controls.currency)
        assertEquals(MoneyPeriodKind.YEAR, vm.uiState.value.controls.period.kind); assertEquals("Travel", vm.uiState.value.controls.filters.query)
        assertTrue(vm.uiState.value.preferences.sections); assertTrue(vm.uiState.value.preferences.detailed)
    }
    @Test fun switchingCurrencyAccountClearsAmountRatherThanReinterpretingIt() = runTest(dispatcher) {
        val vm = vm(FakeMoneyGateway()); draft(vm)
        vm.onEvent(MoneyEvent.Editor(vm.uiState.value.controls.editor!!.copy(accountId = 3)))
        val editor = vm.uiState.value.controls.editor!!
        assertEquals("", editor.amount); assertEquals("USD", editor.currency); assertFalse(editor.canSave)
    }
    @Test fun selectingSavingsUsesItsPocketAndTravelUsesUsdAccount() = runTest(dispatcher) {
        val vm = vm(FakeMoneyGateway())
        vm.onEvent(MoneyEvent.Space(4)); vm.onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE))
        assertEquals(1L, vm.uiState.value.controls.editor!!.accountId)
        assertEquals(1L, vm.uiState.value.controls.editor!!.pocketId)
        vm.onEvent(MoneyEvent.CloseEditor); vm.onEvent(MoneyEvent.Space(5)); vm.onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE))
        assertEquals(3L, vm.uiState.value.controls.editor!!.accountId)
        assertEquals("USD", vm.uiState.value.controls.editor!!.currency)
    }
    @Test fun deletingAndUndoRestoresBalancesAndRows() = runTest(dispatcher) {
        val gateway = FakeMoneyGateway(); val vm = vm(gateway); val original = gateway.data.value
        vm.onEvent(MoneyEvent.Delete(1)); advanceUntilIdle()
        assertEquals(MoneyNoticeKind.DELETED, vm.uiState.value.controls.notice!!.kind)
        assertEquals(original.ledger.snapshot.currencies.first().minorUnits + 1890000, vm.uiState.value.overview.balance.minorUnits)
        vm.onEvent(MoneyEvent.Undo); advanceUntilIdle()
        assertEquals(original.ledger.snapshot, gateway.data.value.ledger.snapshot)
        assertEquals(original.ledger.movements.toSet(), gateway.data.value.ledger.movements.toSet())
    }
}
