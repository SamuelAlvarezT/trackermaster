package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeInvestmentGateway:MoneyInvestmentGateway {
    override val workspace=MutableStateFlow(MoneyPreviewData.workspace())
    override val book=MutableStateFlow(InvestmentPreviewData.state().book)
    override val occupied=MutableStateFlow(emptySet<Long>())
    override val preferences=MutableStateFlow(MoneyPreferences())
    override val settings=MutableStateFlow(AppSettings(localeTag="en"))
    val saves=mutableListOf<InvestmentDraft>()
    var hold:CompletableDeferred<Unit>?=null
    var fail=false
    var failInitialize=false
    override suspend fun initialize(){if(failInitialize)error("load")}
    override suspend fun save(draft:InvestmentDraft){saves+=draft;hold?.await();if(fail)error("save")}
    override suspend fun archive(value:MoneyInvestment)=Unit
    override suspend fun reverse(sequence:Long,version:Long)=Unit
    override suspend fun privacy(hidden:Boolean){preferences.value=preferences.value.copy(hideAmounts=hidden)}
    override fun changed()=Unit
}
