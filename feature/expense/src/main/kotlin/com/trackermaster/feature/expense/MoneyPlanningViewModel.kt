package com.trackermaster.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.domain.money.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MoneyPlanningViewModel @Inject constructor(private val gateway: MoneyPlanningGateway, private val saved: SavedStateHandle,
    @MoneyComputation private val computation: CoroutineDispatcher) : ViewModel() {
    private val controls = MutableStateFlow(PlanningDrafts.decode(saved["planning_draft"]))
    private val refresh = MutableStateFlow(0)
    private val _state = MutableStateFlow(MoneyPlanningUiState(controls=controls.value))
    val state = _state.asStateFlow()
    private var workspace: MoneyWorkspaceData? = null
    private var observation: Job? = null
    private var pendingBudget: Long? = null
    init { observe() }
    fun start(tab: String, currency: String, space: Long?, budget: Long?) {
        if (saved.get<String>("planning_draft") != null) return
        change { it.copy(tab=if(tab=="plans") "plans" else "budgets",currency=if(currency=="USD") "USD" else "COP",space=space) }
        pendingBudget=budget
        if (workspace!=null && budget!=null) {pendingBudget=null;onEvent(PlanningEvent.OpenBudget(budget))}
    }
    private fun observe() {
        observation?.cancel()
        observation=viewModelScope.launch {
            _state.update { it.copy(loading=true) }
            try {
                gateway.initialize()
                combine(gateway.data,gateway.preferences,gateway.settings,gateway.occurrences,combine(controls,refresh){ c,_ -> c }) { data,prefs,settings,occurrences,c ->
                    if (c.privacyOverride==prefs.hideAmounts) controls.compareAndSet(c,c.copy(privacyOverride=null))
                    val locale=Locale.forLanguageTag(settings.localeTag)
                    val budgets=data.budgets.filter { (it.spaceId==null || c.space==null || it.spaceId==c.space) && (it.limit?.currencyCode?.let { code -> code==c.currency } ?: true) }
                        .map { planningBudgetItem(it,data,c.today,locale) }
                    val plans=data.plans.filter { plan -> plan.currency==c.currency && (c.space==null || plan.spaceId==c.space) }
                    val canSave=runCatching { when {
                        c.budget!=null -> budgetValue(c.budget,data)
                        c.plan!=null -> planValue(c.plan,data)
                        c.settlement!=null -> movement(c.settlement,data)
                        else -> error("No editor")
                    }; true }.getOrDefault(false) && !c.saving
                    val candidates=c.settlement?.let { f ->
                        val amount=runCatching { ReceiptValidation.amount(f.amount,f.currency) }.getOrNull()
                        val date=runCatching { LocalDate.parse(f.date) }.getOrNull()
                        val space=f.space ?: data.ledger.snapshot.pockets.find { it.pocket.id==f.pocket }?.pocket?.spaceId
                            ?: data.ledger.snapshot.accounts.find { it.account.id==f.account }?.account?.spaceId
                        if(amount==null || date==null) emptyList() else data.ledger.movements.filter {
                        !it.principal && it.investmentId==null && it.transferId==null && it.accountId==f.account && it.pocketId==f.pocket && it.categoryId==f.category &&
                        it.spaceId==space && it.direction==f.direction && it.amount==amount &&
                        occurrences.none { old -> old.movementId==it.id } &&
                        kotlin.math.abs(it.date.toEpochDay()-date.toEpochDay())<=7
                    }.sortedByDescending { it.date }.take(20) } ?: emptyList()
                    data to MoneyPlanningUiState(false,data,settings,prefs.copy(hideAmounts=c.privacyOverride?:prefs.hideAmounts),c,budgets,plans,occurrences,candidates,canSave,
                        gateway.notificationsAllowed(),PlanRules.forecast(plans,c.today.withDayOfMonth(1),c.today.withDayOfMonth(c.today.lengthOfMonth()),c.currency,MoneyDirection.EXPENSE))
                }.flowOn(computation).collect { (data,value) ->
                    workspace=data;_state.value=value
                    pendingBudget?.let { id -> pendingBudget=null;onEvent(PlanningEvent.OpenBudget(id)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {workspace=null;_state.update { it.copy(loading=false,workspace=null,preferences=it.preferences.copy(hideAmounts=true),controls=controls.value.copy(error=PlanningUiError.LOAD)) }}
        }
    }
    private fun change(block: (PlanningControls)->PlanningControls) {
        controls.update(block); saved["planning_draft"]=PlanningDrafts.encode(controls.value)
    }
    fun onEvent(event: PlanningEvent) {
        if (controls.value.saving && event!=PlanningEvent.Refresh) return
        when(event) {
            PlanningEvent.Retry -> observe()
            PlanningEvent.Refresh -> {change { it.copy(today=LocalDate.now()) }; refresh.update { it+1 };runCatching {gateway.refresh()}}
            PlanningEvent.Privacy -> {
                val hide=!state.value.preferences.hideAmounts
                change{it.copy(privacyOverride=hide)};_state.update{it.copy(preferences=it.preferences.copy(hideAmounts=hide))}
                viewModelScope.launch {try{gateway.privacy(hide)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){change{it.copy(error=PlanningUiError.SAVE)}}}
            }
            is PlanningEvent.Tab -> change { it.copy(tab=event.value,error=null) }
            is PlanningEvent.Currency -> if(event.value in setOf("COP","USD")) change { it.copy(currency=event.value,error=null) }
            is PlanningEvent.Space -> change { it.copy(space=event.value,error=null) }
            is PlanningEvent.OpenBudget -> {
                val old=workspace?.budgets?.find { it.id==event.id }
                if(event.id!=null && old==null) {change {it.copy(error=PlanningUiError.STALE)};return}
                change { it.copy(budget=if(old==null) BudgetForm(currency=it.currency,space=it.space) else BudgetForm(old.id,
                    old.limit?.decimal?.toPlainString()?:old.legacyAmount.toString(),old.limit?.currencyCode?:it.currency,old.categoryId,old.spaceId,old.period,old.alertThreshold.toString(),
                    old.startDate?.toString()?:it.today.toString(),old.endDate?.toString()?:it.today.plusMonths(1).toString()),plan=null,settlement=null,error=null) }
            }
            is PlanningEvent.Budget -> change { it.copy(budget=event.value,error=null) }
            is PlanningEvent.OpenPlan -> {
                val p=event.value
                change { it.copy(plan=if(p==null) PlanForm(currency=it.currency,space=it.space) else PlanForm(p.id,p.name,p.kind,p.direction,p.amount?.decimal?.toPlainString().orEmpty(),
                    p.currency,p.accountId,p.pocketId,p.categoryId,p.nextDue.toString(),p.frequency,p.reminderDays.toString(),p.note,p.version,p.anchor.toString(),p.nextDue.toString(),p.active,p.spaceId),budget=null,settlement=null,error=null) }
            }
            is PlanningEvent.Plan -> change { it.copy(plan=event.value,error=null) }
            is PlanningEvent.OpenSettlement -> event.plan.let { p -> change { it.copy(settlement=SettlementForm(p.id,p.nextDue.toString(),p.version,p.name,p.currency,p.direction,
                p.amount?.decimal?.toPlainString().orEmpty(),p.accountId,p.pocketId,p.categoryId,it.today.toString(),p.note,space=p.spaceId),budget=null,plan=null,error=null) } }
            is PlanningEvent.Settlement -> change { it.copy(settlement=event.value.copy(linked=null),error=null) }
            is PlanningEvent.Link -> {
                val row=workspace?.ledger?.movements?.find { it.id==event.id }
                change { it.copy(settlement=it.settlement?.let { f -> if(row==null) f.copy(linked=null) else
                    f.copy(linked=row.id,amount=row.amount.decimal.toPlainString(),account=row.accountId,pocket=row.pocketId,category=row.categoryId,date=row.date.toString(),note=row.note) },error=null) }
            }
            PlanningEvent.CloseEditor -> change { it.copy(budget=null,plan=null,settlement=null,dateField=null,error=null) }
            PlanningEvent.Save -> save()
            is PlanningEvent.DateField -> change { it.copy(dateField=event.field) }
            is PlanningEvent.Date -> change { when(it.dateField) {
                "from" -> it.copy(budget=it.budget?.copy(from=event.value.toString()),dateField=null,error=null)
                "through" -> it.copy(budget=it.budget?.copy(through=event.value.toString()),dateField=null,error=null)
                "due" -> it.copy(plan=it.plan?.copy(due=event.value.toString()),dateField=null,error=null)
                else -> it.copy(settlement=it.settlement?.copy(date=event.value.toString(),linked=null),dateField=null,error=null)
            } }
            is PlanningEvent.Toggle -> write { gateway.status(event.plan,!event.plan.active) }
            is PlanningEvent.Skip -> write {gateway.skip(event.plan)}
            is PlanningEvent.Snooze -> write {gateway.snooze(event.plan,controls.value.today.plusDays(1))}
            is PlanningEvent.DeleteBudget -> change {it.copy(deleteBudget=event.id)}
            is PlanningEvent.DeletePlan -> change {it.copy(deletePlan=event.plan)}
            PlanningEvent.ConfirmDelete -> {
                val c=controls.value
                write {c.deleteBudget?.let {gateway.deleteBudget(it)};c.deletePlan?.let {gateway.status(it,false,true)}
                    change{it.copy(deleteBudget=null,deletePlan=null)}}
            }
        }
    }
    private fun budgetValue(f: BudgetForm,data: MoneyWorkspaceData): MoneyBudget {
        val limit=requireNotNull(ReceiptValidation.amount(f.amount,f.currency))
        val value=MoneyBudget(f.id,f.category,limit.decimal.toDouble(),limit,f.space,f.period,requireNotNull(f.threshold.toIntOrNull()),
            if(f.period=="CUSTOM") LocalDate.parse(f.from) else null,if(f.period=="CUSTOM") LocalDate.parse(f.through) else null)
        BudgetRules.validate(value);require(f.category==0L || data.categories.any{it.id==f.category && !it.isIncome})
        require(f.space==null || data.spaces.any{it.id==f.space});return value
    }
    private fun planValue(f: PlanForm,data: MoneyWorkspaceData): MoneyPlan {
        val due=LocalDate.parse(f.due)
        val amount=if(f.amount.isBlank() && f.kind==MoneyPlanKind.PURCHASE) null else requireNotNull(ReceiptValidation.amount(f.amount,f.currency))
        val value=MoneyPlan(f.id,f.name,f.kind,f.direction,amount,f.currency,f.account,f.pocket,f.category,
            if(f.originalDue==f.due && f.anchor!=null) LocalDate.parse(f.anchor) else due,due,f.frequency,requireNotNull(f.reminderDays.toIntOrNull()),note=f.note,active=f.active,version=f.version,spaceId=f.space)
        PlanRules.validate(value);validateFunding(f.account,f.pocket,f.category,f.currency,f.direction,data,false)
        require(f.space==null || data.spaces.any{it.id==f.space});return value
    }
    private fun validateFunding(account: Long?,pocket: Long?,category: Long?,currency: String,direction: MoneyDirection,data: MoneyWorkspaceData,required:Boolean) {
        require(!required || (account!=null && category!=null))
        account?.let {id->require(data.ledger.snapshot.accounts.any{it.account.id==id && !it.account.archived && it.account.initialBalance.currencyCode==currency})}
        pocket?.let{id->require(data.ledger.snapshot.pockets.any{it.pocket.id==id && it.pocket.accountId==account})}
        category?.let{id->require(data.categories.any{it.id==id && it.isIncome==(direction==MoneyDirection.INCOME)})}
    }
    private fun movement(f: SettlementForm,data: MoneyWorkspaceData): MoneyMovement {
        validateFunding(f.account,f.pocket,f.category,f.currency,f.direction,data,true)
        require(f.note.length<=2000)
        val account=data.ledger.snapshot.accounts.first{it.account.id==f.account}.account
        val space=f.pocket?.let{id->data.ledger.snapshot.pockets.first{it.pocket.id==id}.pocket.spaceId}?:account.spaceId
        require(f.space==null || f.space==space)
        return MoneyMovement(accountId=account.id,amount=requireNotNull(ReceiptValidation.amount(f.amount,f.currency)),direction=f.direction,
            date=LocalDate.parse(f.date),spaceId=space,categoryId=f.category,pocketId=f.pocket,note=f.note)
    }
    private fun save() {
        val c=controls.value;val data=workspace?:return
        val action=try {when {
            c.budget!=null -> {val v=budgetValue(c.budget,data);suspend {gateway.budget(v)}}
            c.plan!=null -> {val v=planValue(c.plan,data);suspend {gateway.plan(v)}}
            c.settlement!=null -> {val v=movement(c.settlement,data);suspend {gateway.confirm(c.settlement,v)}}
            else -> return
        }}catch(_:Exception){change{it.copy(error=PlanningUiError.INVALID)};return}
        write {action();change{it.copy(budget=null,plan=null,settlement=null,dateField=null)}}
    }
    private fun write(action:suspend()->Unit) {
        if(controls.value.saving)return
        change{it.copy(saving=true,error=null)};_state.update{it.copy(controls=controls.value,canSave=false)}
        viewModelScope.launch {
            try {action();runCatching{gateway.notifyChanged()};change{it.copy(notice=it.notice+1)}}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){change{it.copy(error=when(error) {
                is PlanningException -> when(error.reason){PlanningFailure.STALE,PlanningFailure.CONFLICT->PlanningUiError.STALE;PlanningFailure.ALREADY_LINKED->PlanningUiError.LINKED;else->PlanningUiError.SAVE}
                is MoneyWriteException -> if(error.reason==MoneyWriteFailure.INSUFFICIENT_FUNDS)PlanningUiError.FUNDS else PlanningUiError.STALE
                else -> PlanningUiError.SAVE
            })}}
            finally{change{it.copy(saving=false)}}
        }
    }
}
