package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeCommitmentGateway : MoneyCommitmentGateway {
    override val workspace=MutableStateFlow(MoneyPreviewData.workspace())
    override val commitments=MutableStateFlow(CommitmentPreviewData.data())
    override val preferences=MutableStateFlow(MoneyPreferences())
    override val settings=MutableStateFlow(AppSettings(localeTag="en"))
    var failLoad=false;var failWrite=false;var hold:CompletableDeferred<Unit>?=null
    val debts=mutableListOf<Pair<MoneyDebt,MoneyMovement?>>()
    val goals=mutableListOf<SavingsGoal>()
    val repayments=mutableListOf<Pair<CommitmentDraft,MoneyMovement>>()
    val allocations=mutableListOf<Pair<CommitmentDraft,Money>>()
    var changedCount=0
    override suspend fun initialize(){if(failLoad)error("Load failed")}
    private suspend fun write(){hold?.await();if(failWrite)error("Save failed")}
    override suspend fun debt(value:MoneyDebt,opening:MoneyMovement?,existing:Boolean){write();debts+=value to opening}
    override suspend fun goal(value:SavingsGoal,account:Long?,space:Long){write();goals+=value}
    override suspend fun repay(value:CommitmentDraft,movement:MoneyMovement){repayments+=value to movement;write()}
    override suspend fun allocate(value:CommitmentDraft,amount:Money){allocations+=value to amount;write()}
    override suspend fun reverse(id:String,version:Long){write()}
    override suspend fun privacy(hidden:Boolean){preferences.value=preferences.value.copy(hideAmounts=hidden)}
    override fun changed(){changedCount++}
}
