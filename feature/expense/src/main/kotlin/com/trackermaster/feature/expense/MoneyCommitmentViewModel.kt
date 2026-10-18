package com.trackermaster.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.domain.money.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MoneyCommitmentViewModel @Inject constructor(private val gateway: MoneyCommitmentGateway,
    private val saved: SavedStateHandle, @MoneyComputation private val computation: CoroutineDispatcher) : ViewModel() {
    private val controls=MutableStateFlow(CommitmentDrafts.decode(saved["commitment_draft"]))
    private val _state=MutableStateFlow(MoneyCommitmentUiState(controls=controls.value))
    val state=_state.asStateFlow()
    private var observation: Job?=null
    init { observe() }
    fun start(tab: String, currency: String, space: Long?) {
        if(saved.get<String>("commitment_draft")!=null)return
        change { it.copy(tab=if(tab=="goals")"goals" else "debts",currency=if(currency=="USD")"USD" else "COP",space=space) }
    }
    private fun observe() {
        observation?.cancel()
        observation=viewModelScope.launch {
            _state.update{it.copy(loading=true)}
            try {
                gateway.initialize()
                combine(gateway.workspace,gateway.commitments,gateway.preferences,gateway.settings,controls) { w,data,prefs,settings,c ->
                    if(c.privacyOverride==prefs.hideAmounts)controls.compareAndSet(c,c.copy(privacyOverride=null))
                    // Room emits related tables separately. Keep the last coherent state during a transaction's refresh.
                    if(data.payments.any{p->w.ledger.movements.none{it.id==p.movementId && it.principal}} ||
                        data.goals.any{g->w.ledger.snapshot.pockets.none{it.pocket.id==g.pocketId}}) return@combine null
                    val debts=data.debts.filter{it.principal.currencyCode==c.currency && (c.space==null || it.spaceId==c.space) && (c.archived || !it.archived)}
                        .map{CommitmentRules.debt(it,data.payments,w.ledger.movements,LocalDate.now())}
                        .sortedWith(compareByDescending<DebtProgress>{it.overdue}.thenBy{it.debt.due?:LocalDate.MAX}.thenBy{it.debt.person})
                    val goals=data.goals.filter{g->g.target.currencyCode==c.currency && (c.archived || !g.archived) &&
                        (c.space==null || w.ledger.snapshot.pockets.single{it.pocket.id==g.pocketId}.pocket.spaceId==c.space)}
                        .map{g->CommitmentRules.goal(g,w.ledger.snapshot.pockets.single{it.pocket.id==g.pocketId})}
                    val canSave=c.draft?.let{runCatching{validate(it,w,data)}.isSuccess}==true && !c.saving
                    val f=c.draft
                    val candidates=if(f?.editor==CommitmentEditor.REPAYMENT && f.linked==null) {
                        val amount=runCatching{ReceiptValidation.amount(f.amount,f.currency)}.getOrNull()
                        val date=runCatching{LocalDate.parse(f.date)}.getOrNull()
                        w.ledger.movements.filter { m->!m.principal && m.investmentId==null && m.transferId==null && m.accountId==f.account && m.pocketId==f.pocket &&
                            m.spaceId==f.space && m.amount==amount && m.direction==CommitmentRules.direction(f.kind,PrincipalEvent.REPAYMENT) &&
                            date!=null && kotlin.math.abs(m.date.toEpochDay()-date.toEpochDay())<=7 &&
                            data.payments.none{it.movementId==m.id} && m.id !in data.plannedMovementIds }.sortedByDescending{it.date}.take(15)
                    } else emptyList()
                    MoneyCommitmentUiState(false,w,settings,prefs.copy(hideAmounts=c.privacyOverride?:prefs.hideAmounts),c,data,debts,goals,
                        debts.filter{!it.debt.archived && it.debt.kind==DebtKind.PAYABLE}.fold(Money(0,c.currency)){s,d->s+d.outstanding},
                        debts.filter{!it.debt.archived && it.debt.kind==DebtKind.RECEIVABLE}.fold(Money(0,c.currency)){s,d->s+d.outstanding},candidates,canSave)
                }.filterNotNull().flowOn(computation).collect { value -> _state.value=value }
            } catch(e:CancellationException){throw e} catch(_:Exception){_state.update{it.copy(loading=false,workspace=null,preferences=it.preferences.copy(hideAmounts=true),controls=controls.value.copy(error="LOAD"))}}
        }
    }
    private fun change(block:(CommitmentControls)->CommitmentControls) {
        controls.update(block);saved["commitment_draft"]=CommitmentDrafts.encode(controls.value)
    }
    fun onEvent(event:CommitmentEvent) {
        if(controls.value.saving)return
        val w=state.value.workspace
        when(event) {
            CommitmentEvent.Retry->observe()
            CommitmentEvent.Privacy->{val hidden=!state.value.preferences.hideAmounts;change{it.copy(privacyOverride=hidden)};_state.update{it.copy(preferences=it.preferences.copy(hideAmounts=hidden))};viewModelScope.launch{try{gateway.privacy(hidden)}catch(e:CancellationException){throw e}catch(_:Exception){change{it.copy(error="SAVE")}}}}
            is CommitmentEvent.Tab->change{it.copy(tab=event.value,detail=null,error=null)}
            is CommitmentEvent.Currency->if(event.value in setOf("COP","USD"))change{it.copy(currency=event.value,detail=null,error=null)}
            is CommitmentEvent.Space->change{it.copy(space=event.value,detail=null,error=null)}
            is CommitmentEvent.Archived->change{it.copy(archived=event.value)}
            is CommitmentEvent.Detail->change{it.copy(detail=event.id)}
            is CommitmentEvent.Draft->change{it.copy(draft=event.value.copy(linked=null),error=null)}
            is CommitmentEvent.New->{
                val account=w?.ledger?.snapshot?.accounts?.firstOrNull{!it.account.archived && it.total.currencyCode==controls.value.currency}?.account
                change{it.copy(draft=CommitmentDraft(event.editor,currency=it.currency,space=it.space?:if(event.editor==CommitmentEditor.GOAL)4 else account?.spaceId?:2,account=account?.id),error=null)}
            }
            is CommitmentEvent.Debt->event.value.let{d->change{it.copy(draft=CommitmentDraft(CommitmentEditor.DEBT,id=d.id,version=d.version,name=d.person,kind=d.kind,currency=d.principal.currencyCode,space=d.spaceId,amount=d.principal.decimal.toPlainString(),due=d.due?.toString().orEmpty(),note=d.note,existing=true),error=null)}}
            is CommitmentEvent.Goal->event.value.let{g->change{it.copy(draft=CommitmentDraft(CommitmentEditor.GOAL,id=g.id,version=g.version,name=g.name,currency=g.target.currencyCode,amount=g.target.decimal.toPlainString(),pocket=g.pocketId,due=g.due?.toString().orEmpty(),existing=true),error=null)}}
            is CommitmentEvent.Repay->event.value.let{d->
                val account=w?.ledger?.snapshot?.accounts?.firstOrNull{!it.account.archived && it.total.currencyCode==d.principal.currencyCode && it.account.spaceId==d.spaceId}?.account
                val pocket=if(account==null)w?.ledger?.snapshot?.pockets?.firstOrNull{it.balance.currencyCode==d.principal.currencyCode && it.pocket.spaceId==d.spaceId}?.pocket else null
                change{it.copy(draft=CommitmentDraft(CommitmentEditor.REPAYMENT,id=d.id,version=d.version,name=d.person,kind=d.kind,currency=d.principal.currencyCode,space=d.spaceId,account=account?.id?:pocket?.accountId,pocket=pocket?.id),error=null)}
            }
            is CommitmentEvent.Allocate->event.value.let{g->val pocket=w?.ledger?.snapshot?.pockets?.find{it.pocket.id==g.pocketId}?.pocket
                change{it.copy(draft=CommitmentDraft(CommitmentEditor.ALLOCATION,id=g.id,version=g.version,name=g.name,currency=g.target.currencyCode,account=pocket?.accountId,pocket=g.pocketId,withdraw=event.withdraw),error=null)}}
            is CommitmentEvent.Link->{val f=controls.value.draft?:return
                val movement=state.value.candidates.find{it.id==event.id}
                change{it.copy(draft=if(movement==null)f.copy(linked=null)else f.copy(linked=movement.id,date=movement.date.toString(),note=movement.note),error=null)}
            }
            CommitmentEvent.Close->change{it.copy(draft=null,dateField=null,detail=null,error=null)}
            CommitmentEvent.Save->save()
            is CommitmentEvent.ArchiveDebt->write{gateway.debt(event.value.copy(archived=!event.value.archived),null,true)}
            is CommitmentEvent.ArchiveGoal->write{gateway.goal(event.value.copy(archived=!event.value.archived),null,0)}
            is CommitmentEvent.DateField->change{it.copy(dateField=event.value)}
            is CommitmentEvent.Date->change{c->c.copy(draft=c.draft?.let{if(c.dateField=="due")it.copy(due=event.value.toString())else it.copy(date=event.value.toString(),linked=null)},dateField=null)}
            is CommitmentEvent.Reverse->change{it.copy(reverse=event.id)}
            CommitmentEvent.ConfirmReverse->{val payment=state.value.data.payments.find{it.id==controls.value.reverse}?:return
                val debt=state.value.data.debts.find{it.id==payment.debtId}?:return
                write{gateway.reverse(payment.id,debt.version)}}
        }
    }
    private fun validate(f:CommitmentDraft,w:MoneyWorkspaceData,data:MoneyCommitments) {
        require(f.currency in setOf("COP","USD"))
        val amount=ReceiptValidation.amount(f.amount,f.currency)
        if(f.due.isNotBlank())LocalDate.parse(f.due)
        when(f.editor) {
            CommitmentEditor.DEBT->{require(f.name.isNotBlank() && w.spaces.any{it.id==f.space});if(f.disburse)movement(f,w,PrincipalEvent.DISBURSEMENT)
                if(f.existing){val old=data.debts.single{it.id==f.id};require(!old.archived && old.version==f.version)
                    val payments=data.payments.filter{it.debtId==f.id}
                    if(payments.isNotEmpty())require(old.kind==f.kind && old.spaceId==f.space && old.principal.currencyCode==f.currency)
                    CommitmentRules.debt(old.copy(principal=amount),payments,w.ledger.movements,LocalDate.now())}}
            CommitmentEditor.GOAL->{require(f.name.isNotBlank());if(f.pocket!=null){val p=w.ledger.snapshot.pockets.single{it.pocket.id==f.pocket};require(p.balance.currencyCode==f.currency && data.goals.none{it.id!=f.id && it.pocketId==f.pocket});if(!f.existing)require(w.ledger.snapshot.accounts.any{it.account.id==p.pocket.accountId && !it.account.archived})}
                else {val a=w.ledger.snapshot.accounts.single{it.account.id==f.account};require(!a.account.archived && a.total.currencyCode==f.currency && w.spaces.any{it.id==f.space})}}
            CommitmentEditor.REPAYMENT->{movement(f,w,PrincipalEvent.REPAYMENT);val d=data.debts.single{it.id==f.id};require(!d.archived && d.version==f.version);require(amount.minorUnits<=CommitmentRules.debt(d,data.payments,w.ledger.movements,LocalDate.now()).outstanding.minorUnits)}
            CommitmentEditor.ALLOCATION->{LocalDate.parse(f.date);val g=data.goals.single{it.id==f.id};require(!g.archived && g.version==f.version)
                val a=w.ledger.snapshot.accounts.single{it.account.id==f.account};require(!a.account.archived && a.total.currencyCode==f.currency)
                val p=w.ledger.snapshot.pockets.single{it.pocket.id==g.pocketId};require(w.ledger.snapshot.accounts.single{it.account.id==p.pocket.accountId}.account.archived==false)
                require(amount.minorUnits<=if(f.withdraw)p.balance.minorUnits else a.unallocated.minorUnits)}
        }
    }
    private fun movement(f:CommitmentDraft,w:MoneyWorkspaceData,event:PrincipalEvent):MoneyMovement {
        val a=w.ledger.snapshot.accounts.single{it.account.id==f.account}.account
        require(!a.archived && a.initialBalance.currencyCode==f.currency)
        val p=f.pocket?.let{id->w.ledger.snapshot.pockets.single{it.pocket.id==id}.pocket}
        require(p==null || p.accountId==a.id);require((p?.spaceId?:a.spaceId)==f.space)
        return MoneyMovement(accountId=a.id,amount=ReceiptValidation.amount(f.amount,f.currency),direction=CommitmentRules.direction(f.kind,event),date=LocalDate.parse(f.date),spaceId=f.space,pocketId=f.pocket,note=f.note)
    }
    private fun save() {
        val f=controls.value.draft?:return;val w=state.value.workspace?:return
        try{validate(f,w,state.value.data)}catch(_:Exception){change{it.copy(error="INVALID")};return}
        write {
            val amount=ReceiptValidation.amount(f.amount,f.currency)
            when(f.editor) {
                CommitmentEditor.DEBT->gateway.debt(MoneyDebt(f.id,f.name,f.kind,amount,f.space,f.due.takeIf{it.isNotBlank()}?.let(LocalDate::parse),f.note,version=f.version),if(f.disburse)movement(f,w,PrincipalEvent.DISBURSEMENT)else null,f.existing)
                CommitmentEditor.GOAL->gateway.goal(SavingsGoal(f.id,f.name,amount,f.pocket?:0,f.due.takeIf{it.isNotBlank()}?.let(LocalDate::parse),version=f.version),f.account,f.space)
                CommitmentEditor.REPAYMENT->gateway.repay(f,movement(f,w,PrincipalEvent.REPAYMENT))
                CommitmentEditor.ALLOCATION->gateway.allocate(f,amount)
            }
        }
    }
    private fun write(action:suspend()->Unit) {
        if(controls.value.saving)return
        val draft=controls.value.draft
        val destinationSpace=draft?.let{f->if(f.editor==CommitmentEditor.GOAL && f.pocket!=null)state.value.workspace?.ledger?.snapshot?.pockets?.find{it.pocket.id==f.pocket}?.pocket?.spaceId else f.space}
        change{it.copy(saving=true,error=null)}
        viewModelScope.launch {
            try{action();gateway.changed();change{it.copy(saving=false,draft=null,reverse=null,error=null,currency=draft?.currency?:it.currency,
                space=if(it.space!=null && draft?.editor in setOf(CommitmentEditor.DEBT,CommitmentEditor.GOAL))destinationSpace else it.space)}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){change{it.copy(saving=false,error=if(e is MoneyWriteException && e.reason==MoneyWriteFailure.INSUFFICIENT_FUNDS)"FUNDS"else if(e is IllegalArgumentException)"CONFLICT" else "SAVE")}}
        }
    }
}
