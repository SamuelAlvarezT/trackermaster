package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

internal class FakePlanningGateway : MoneyPlanningGateway {
    override val data=MutableStateFlow(PlanningPreviewData.workspace())
    override val preferences=MutableStateFlow(MoneyPreferences())
    override val settings=MutableStateFlow(AppSettings(localeTag="en"))
    override val occurrences=MutableStateFlow<List<MoneyPlanOccurrence>>(emptyList())
    var failInitialize=false
    var failWrite=false
    var enabled=false
    var hold:CompletableDeferred<Unit>?=null
    val budgets=mutableListOf<MoneyBudget>()
    val plans=mutableListOf<MoneyPlan>()
    val confirmations=mutableListOf<Pair<SettlementForm,MoneyMovement>>()
    val skips=mutableListOf<MoneyPlan>()
    val snoozes=mutableListOf<Pair<MoneyPlan,LocalDate>>()
    var refreshes=0
    override suspend fun initialize(){if(failInitialize)error("Load failed")}
    private suspend fun write(){hold?.await();if(failWrite)error("Write failed")}
    override suspend fun budget(value:MoneyBudget){write();budgets+=value}
    override suspend fun deleteBudget(id:Long){write();data.value=data.value.copy(budgets=data.value.budgets.filter{it.id!=id})}
    override suspend fun plan(value:MoneyPlan){write();plans+=value}
    override suspend fun status(value:MoneyPlan,active:Boolean,archived:Boolean){write();data.value=data.value.copy(plans=data.value.plans.map{if(it.id==value.id)it.copy(active=active,archived=archived,version=it.version+1)else it})}
    override suspend fun skip(value:MoneyPlan){write();skips+=value}
    override suspend fun snooze(value:MoneyPlan,day:LocalDate){write();snoozes+=value to day}
    override suspend fun confirm(form:SettlementForm,movement:MoneyMovement){confirmations+=form to movement;write()}
    override suspend fun privacy(value:Boolean){preferences.value=preferences.value.copy(hideAmounts=value)}
    override fun notificationsAllowed()=enabled
    override fun refresh(){refreshes++}
    override fun notifyChanged(){refreshes++}
}
