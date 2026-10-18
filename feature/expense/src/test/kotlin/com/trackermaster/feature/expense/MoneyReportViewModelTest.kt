package com.trackermaster.feature.expense

import androidx.lifecycle.*
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyReportViewModelTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val stores=mutableListOf<ViewModelStore>()
    private class Exporter:MoneyReportExportGateway {
        val snapshots=mutableListOf<MoneyReport>()
        var hold:CompletableDeferred<Unit>?=null
        var fail=false
        override suspend fun create(report:MoneyReport,pdf:Boolean,localeTag:String):String{snapshots+=report;hold?.await();if(fail)error("export");return "money-report-00000000-0000-0000-0000-000000000000.csv"}
    }
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){stores.forEach{it.clear()};Dispatchers.resetMain()}
    private fun vm(g:FakeMoneyGateway,e:Exporter):MoneyViewModel{val store=ViewModelStore().also{stores+=it};return ViewModelProvider(store,object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):T=MoneyViewModel(g,SavedStateHandle(),dispatcher,e) as T})[MoneyViewModel::class.java]}
    @Test fun hiddenAmountsCannotStartAnExport()=runTest(dispatcher){val g=FakeMoneyGateway();g.preferences.value=g.preferences.value.copy(hideAmounts=true);val e=Exporter();vm(g,e).onEvent(MoneyEvent.Export(false));assertTrue(e.snapshots.isEmpty())}
    @Test fun duplicateExportIsIgnoredAndSnapshotKeepsItsScope()=runTest(dispatcher){val g=FakeMoneyGateway();val e=Exporter().also{it.hold=CompletableDeferred()};val v=vm(g,e);v.onEvent(MoneyEvent.Export(false));v.onEvent(MoneyEvent.Currency("USD"));v.onEvent(MoneyEvent.Export(true));assertEquals(1,e.snapshots.size);assertEquals("COP",e.snapshots.single().scope.currency);e.hold!!.complete(Unit);advanceUntilIdle();assertFalse(v.uiState.value.controls.exporting);assertTrue(v.effects.first() is MoneyEffect.ShareReport)}
    @Test fun failureKeepsFiltersAndAllowsRetry()=runTest(dispatcher){val g=FakeMoneyGateway();val e=Exporter().also{it.fail=true};val v=vm(g,e);v.onEvent(MoneyEvent.Filters(MoneyFilters(accountId=1)));v.onEvent(MoneyEvent.Export(true));advanceUntilIdle();assertEquals(MoneyUiError.EXPORT,v.uiState.value.controls.error);assertEquals(1L,v.uiState.value.controls.filters.accountId);assertFalse(v.uiState.value.controls.exporting);e.fail=false;v.onEvent(MoneyEvent.Export(false));advanceUntilIdle();assertTrue(v.effects.first() is MoneyEffect.ShareReport)}
    @Test fun privacyDuringExportPreventsAutomaticSharing()=runTest(dispatcher){val g=FakeMoneyGateway();val e=Exporter().also{it.hold=CompletableDeferred()};val v=vm(g,e);v.onEvent(MoneyEvent.Export(false));v.onEvent(MoneyEvent.Privacy);e.hold!!.complete(Unit);advanceUntilIdle();assertTrue(v.uiState.value.preferences.hideAmounts);assertFalse(v.uiState.value.controls.exporting);assertNull(withTimeoutOrNull(10){v.effects.first()})}
    @Test fun chartAndCsvUseSameCategoryAndAccountFilters()=runTest(dispatcher){val e=Exporter();val v=vm(FakeMoneyGateway(),e);v.onEvent(MoneyEvent.Filters(MoneyFilters(accountId=1,categoryId=1)));v.onEvent(MoneyEvent.Export(false));assertEquals(v.uiState.value.reports.totals,e.snapshots.single().totals);assertTrue(e.snapshots.single().entries.all{it.movement.accountId==1L && it.movement.categoryId==1L})}
}
