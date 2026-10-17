package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.money.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyPlanningScreen(state: MoneyPlanningUiState, onEvent: (PlanningEvent)->Unit, onBack: ()->Unit,
    onNotifications: ()->Unit = {}, onNotificationSettings: ()->Unit = {}) {
    val c=state.controls
    val editing=c.budget!=null || c.plan!=null || c.settlement!=null
    val snackbar=remember{SnackbarHostState()}
    val saved=stringResource(R.string.p_saved)
    LaunchedEffect(c.notice){if(c.notice>0)snackbar.showSnackbar(saved)}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(when {
        c.budget!=null -> if(c.budget.id==0L)R.string.p_create_budget else R.string.p_edit_budget
        c.plan!=null -> if(c.plan.version==0L)R.string.p_new_plan else R.string.p_edit_plan
        c.settlement!=null -> R.string.p_confirm_plan
        else -> R.string.p_planning
    }))},navigationIcon={IconButton({if(!c.saving){if(editing)onEvent(PlanningEvent.CloseEditor)else onBack()}}){Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.r_back))}},
        actions={IconButton({onEvent(PlanningEvent.Privacy)},enabled=!c.saving){Icon(if(state.preferences.hideAmounts)Icons.Default.Visibility else Icons.Default.VisibilityOff,
            stringResource(if(state.preferences.hideAmounts)R.string.m_show else R.string.m_hide))}})},
        snackbarHost={SnackbarHost(snackbar)},bottomBar={if(editing)Surface(shadowElevation=4.dp){Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)){
            FilledTonalButton({onEvent(PlanningEvent.Save)},Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("planning_save"),enabled=state.canSave){
                if(c.saving)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                else Text(stringResource(if(c.settlement!=null)R.string.p_confirm_save else R.string.m_save))
            }
            if(!state.canSave && !c.saving)Text(stringResource(R.string.p_form_invalid),style=MaterialTheme.typography.bodySmall)
        }}}) {padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("planning_list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(c.error!=null)item("error"){PlanningError(c.error){onEvent(PlanningEvent.Retry)}}
            when {
                state.loading -> item("loading"){CircularProgressIndicator()}
                state.workspace==null -> item("failure"){MoneyTextButton({onEvent(PlanningEvent.Retry)}){Text(stringResource(R.string.m_retry))}}
                c.budget!=null -> item("budget_form"){BudgetEditor(state,onEvent)}
                c.plan!=null -> item("plan_form"){PlanEditor(state,onEvent)}
                c.settlement!=null -> {
                    item("settlement_form"){SettlementEditor(state,onEvent)}
                    item("link_intro"){Text(stringResource(R.string.p_link_intro))}
                    item("new_movement"){MoneyOutlinedButton({onEvent(PlanningEvent.Link(null))},Modifier.fillMaxWidth()){Text(stringResource(R.string.p_new_movement))}}
                    if(c.settlement.linked!=null)item("linked"){Text(stringResource(R.string.p_linked),color=MaterialTheme.colorScheme.primary)}
                    else if(state.candidates.isEmpty())item("no_candidates"){Text(stringResource(R.string.p_no_candidates),style=MaterialTheme.typography.bodySmall)}
                    items(state.candidates,key={"candidate:${it.id}"}){row->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
                        MoneyAmount(row.amount,state.preferences.hideAmounts);Text(dayLabel(row.date))
                        MoneyTextButton({onEvent(PlanningEvent.Link(row.id))}){Text(stringResource(R.string.p_link_movement))}
                    }}}
                }
                else -> {
                    item("navigation"){Column{
                        TabRow(selectedTabIndex=if(c.tab=="plans")1 else 0){
                            Tab(c.tab=="budgets",{onEvent(PlanningEvent.Tab("budgets"))},text={Text(stringResource(R.string.m_budgets))})
                            Tab(c.tab=="plans",{onEvent(PlanningEvent.Tab("plans"))},text={Text(stringResource(R.string.p_plans))})
                        }
                        MoneyChoice(stringResource(R.string.m_currency),c.currency,listOf("COP" to "COP","USD" to "USD"),{onEvent(PlanningEvent.Currency(it))})
                        val spaces=listOf("none" to stringResource(R.string.m_all_spaces))+state.workspace.spaces.map{it.id.toString() to spaceTitle(it)}
                        MoneyChoice(stringResource(R.string.m_space),spaces.find{it.first==c.space?.toString()}?.second?:stringResource(R.string.m_all_spaces),spaces,{onEvent(PlanningEvent.Space(it.toLongOrNull()))})
                    }}
                    if(!state.notificationsAllowed)item("notifications"){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
                        Text(stringResource(R.string.p_notifications_disabled))
                        MoneyTextButton(onNotifications){Text(stringResource(R.string.p_enable_notifications))}
                        MoneyTextButton(onNotificationSettings){Text(stringResource(R.string.p_notification_settings))}
                    }}}
                    if(c.tab=="budgets") {
                        item("budget_actions"){FilledTonalButton({onEvent(PlanningEvent.OpenBudget())},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.p_create_budget))}}
                        item("budget_hint"){Text(stringResource(R.string.p_budget_hint),style=MaterialTheme.typography.bodySmall)}
                        if(state.budgets.isEmpty())item("budget_empty"){MoneyEmpty(stringResource(R.string.m_budget_empty),stringResource(R.string.m_budget_empty_detail),
                            stringResource(R.string.p_create_budget),Icons.Default.PieChart,{onEvent(PlanningEvent.OpenBudget())})}
                        items(state.budgets,key={"budget:${it.id}"}){item->Column{
                            MoneyBudgetCard(item,state.preferences.hideAmounts){onEvent(PlanningEvent.OpenBudget(item.id))}
                            MoneyTextButton({onEvent(PlanningEvent.DeleteBudget(item.id))}){Text(stringResource(R.string.m_delete))}
                        }}
                    }else{
                        item("forecast"){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(stringResource(R.string.p_forecast));MoneyAmount(state.forecast,state.preferences.hideAmounts)
                            Text(stringResource(R.string.p_not_balance),style=MaterialTheme.typography.bodySmall)
                        }}}
                        item("create_plan"){FilledTonalButton({onEvent(PlanningEvent.OpenPlan())},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.p_new_plan))}}
                        if(state.plans.isEmpty())item("plans_empty"){MoneyEmpty(stringResource(R.string.p_plan_empty),stringResource(R.string.p_plan_empty_detail),stringResource(R.string.p_new_plan),Icons.Default.EventRepeat,{onEvent(PlanningEvent.OpenPlan())})}
                        items(state.plans,key={"plan:${it.id}"}){plan->PlanCard(plan,state,onEvent)}
                    }
                }
            }
        }
    }
    PlanningDialogs(state,onEvent)
}

@Composable private fun PlanningError(error: PlanningUiError, retry: ()->Unit) {
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium){Column(Modifier.padding(16.dp)){
        Text(stringResource(when(error){PlanningUiError.LOAD->R.string.m_load_error;PlanningUiError.INVALID->R.string.p_form_invalid;
            PlanningUiError.STALE->R.string.p_stale;PlanningUiError.FUNDS->R.string.m_funds_error;PlanningUiError.LINKED->R.string.p_link_error;PlanningUiError.SAVE->R.string.m_save_error}))
        if(error==PlanningUiError.LOAD)MoneyTextButton(retry){Text(stringResource(R.string.m_retry))}
    }}
}
@Composable internal fun frequencyTitle(frequency: MoneyFrequency):String=stringResource(when(frequency){
    MoneyFrequency.ONCE->R.string.p_once;MoneyFrequency.DAILY->R.string.p_daily;MoneyFrequency.WEEKLY->R.string.p_weekly;
    MoneyFrequency.MONTHLY->R.string.p_monthly;MoneyFrequency.YEARLY->R.string.p_yearly})
@Composable private fun planTitle(kind: MoneyPlanKind)=stringResource(when(kind){MoneyPlanKind.PAYMENT->R.string.p_payment;MoneyPlanKind.PURCHASE->R.string.p_purchase;MoneyPlanKind.ESTIMATE->R.string.p_estimate})
@Composable private fun PlanCard(plan: MoneyPlan,state: MoneyPlanningUiState,onEvent:(PlanningEvent)->Unit) {
    var menu by remember{mutableStateOf(false)}
    var skip by remember{mutableStateOf(false)}
    val done=!plan.active && plan.frequency==MoneyFrequency.ONCE && state.occurrences.any{it.planId==plan.id && it.due==plan.nextDue}
    Card(Modifier.fillMaxWidth().testTag("plan_${plan.id}")){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(plan.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Box{IconButton({menu=true},enabled=!state.controls.saving){Icon(Icons.Default.MoreVert,stringResource(R.string.m_edit))}
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem(text={Text(stringResource(R.string.m_edit))},onClick={menu=false;onEvent(PlanningEvent.OpenPlan(plan))},modifier=Modifier.heightIn(min=48.dp))
                    if(!done)DropdownMenuItem(text={Text(stringResource(if(plan.active)R.string.p_pause else R.string.p_resume))},onClick={menu=false;onEvent(PlanningEvent.Toggle(plan))},modifier=Modifier.heightIn(min=48.dp))
                    DropdownMenuItem(text={Text(stringResource(R.string.m_delete))},onClick={menu=false;onEvent(PlanningEvent.DeletePlan(plan))},modifier=Modifier.heightIn(min=48.dp))
                }
            }
        }
        Text(planTitle(plan.kind)+" · "+frequencyTitle(plan.frequency))
        Text(spaceTitle(state.workspace?.spaces?.find{it.id==plan.spaceId}),style=MaterialTheme.typography.bodySmall)
        plan.amount?.let{MoneyAmount(it,state.preferences.hideAmounts)}
        Text(stringResource(R.string.p_due,dayLabel(plan.nextDue)))
        val overdue=plan.active && plan.nextDue.isBefore(state.controls.today)
        val skipped=done && state.occurrences.any{it.planId==plan.id && it.due==plan.nextDue && it.status=="SKIPPED"}
        Text(stringResource(if(skipped)R.string.p_skipped else if(done)R.string.p_completed else if(!plan.active)R.string.p_paused else if(overdue)R.string.p_overdue else R.string.p_pending),
            color=if(overdue)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        state.occurrences.filter{it.planId==plan.id && it.status=="COMPLETED"}.take(1).forEach{old->
            if(state.workspace?.ledger?.movements?.none{it.id==old.movementId}==true)Text(stringResource(R.string.p_missing_movement),color=MaterialTheme.colorScheme.error)
        }
        if(plan.active){
            FilledTonalButton({onEvent(PlanningEvent.OpenSettlement(plan))},Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!state.controls.saving){Text(stringResource(R.string.p_confirm_plan))}
            MoneyTextButton({onEvent(PlanningEvent.Snooze(plan))},enabled=!state.controls.saving){Text(stringResource(R.string.p_snooze))}
            MoneyTextButton({skip=true},enabled=!state.controls.saving){Text(stringResource(R.string.p_skip))}
        }
    }}
    if(skip)AlertDialog(onDismissRequest={skip=false},title={Text(stringResource(R.string.p_skip_title))},text={Text(stringResource(R.string.p_skip_detail))},
        confirmButton={MoneyTextButton({skip=false;onEvent(PlanningEvent.Skip(plan))}){Text(stringResource(R.string.p_skip))}},
        dismissButton={MoneyTextButton({skip=false}){Text(stringResource(R.string.m_cancel))}})
}

@Composable internal fun PlanningField(value: String,onValue:(String)->Unit,label:Int,tag:String,enabled:Boolean=true,numeric:Boolean=false,hidden:Boolean=false) {
    OutlinedTextField(value,onValue,Modifier.fillMaxWidth().testTag(tag),label={Text(stringResource(label))},enabled=enabled,
        singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text),
        visualTransformation=if(hidden)PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable private fun BudgetEditor(state:MoneyPlanningUiState,onEvent:(PlanningEvent)->Unit) {
    val f=state.controls.budget?:return;val data=state.workspace?:return
    fun update(value:BudgetForm){onEvent(PlanningEvent.Budget(value))}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(data.budgets.find{it.id==f.id}?.limit==null && f.id!=0L)Text(stringResource(R.string.p_legacy))
        PlanningField(f.amount,{update(f.copy(amount=it))},R.string.m_amount,"budget_amount",numeric=true,hidden=state.preferences.hideAmounts)
        MoneyChoice(stringResource(R.string.m_currency),f.currency,listOf("COP" to "COP","USD" to "USD"),{update(f.copy(currency=it))})
        val categories=listOf("0" to stringResource(R.string.p_all_categories))+data.categories.filter{!it.isIncome}.map{it.id.toString() to categoryTitle(it)}
        MoneyChoice(stringResource(R.string.m_category),categories.find{it.first==f.category.toString()}?.second?:stringResource(R.string.m_uncategorized),categories,{update(f.copy(category=it.toLong()))})
        val spaces=listOf("none" to stringResource(R.string.m_all_spaces))+data.spaces.map{it.id.toString() to spaceTitle(it)}
        MoneyChoice(stringResource(R.string.m_space),spaces.find{it.first==f.space?.toString()}?.second?:stringResource(R.string.m_all_spaces),spaces,{update(f.copy(space=it.toLongOrNull()))})
        val periods=listOf("WEEKLY" to stringResource(R.string.p_weekly),"MONTHLY" to stringResource(R.string.p_monthly),"YEARLY" to stringResource(R.string.p_yearly),"CUSTOM" to stringResource(R.string.p_custom))
        MoneyChoice(stringResource(R.string.p_budget_period),periods.find{it.first==f.period}?.second.orEmpty(),periods,{update(f.copy(period=it))})
        if(f.period=="CUSTOM"){
            PlanningDate(f.from,R.string.p_from){onEvent(PlanningEvent.DateField("from"))}
            PlanningDate(f.through,R.string.p_through){onEvent(PlanningEvent.DateField("through"))}
        }
        PlanningField(f.threshold,{update(f.copy(threshold=it))},R.string.p_alert_threshold,"budget_threshold",numeric=true)
        Text(stringResource(R.string.p_budget_hint),style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun PlanEditor(state:MoneyPlanningUiState,onEvent:(PlanningEvent)->Unit) {
    val f=state.controls.plan?:return
    fun update(value:PlanForm){onEvent(PlanningEvent.Plan(value))}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        PlanningField(f.name,{update(f.copy(name=it.take(120)))},R.string.m_name,"plan_name")
        MoneyChoice(stringResource(R.string.p_plan_type),planTitle(f.kind),MoneyPlanKind.entries.map{it.name to planTitle(it)},
            {val kind=MoneyPlanKind.valueOf(it);update(f.copy(kind=kind,direction=if(kind==MoneyPlanKind.PAYMENT)f.direction else MoneyDirection.EXPENSE,category=null))})
        if(f.kind==MoneyPlanKind.PAYMENT)MoneyChoice(stringResource(R.string.p_direction),stringResource(if(f.direction==MoneyDirection.EXPENSE)R.string.m_expense else R.string.m_income),
            listOf("EXPENSE" to stringResource(R.string.m_expense),"INCOME" to stringResource(R.string.m_income)),{update(f.copy(direction=MoneyDirection.valueOf(it),category=null))})
        MoneyChoice(stringResource(R.string.m_currency),f.currency,listOf("COP" to "COP","USD" to "USD"),{update(f.copy(currency=it,account=null,pocket=null))})
        PlanningField(f.amount,{update(f.copy(amount=it))},if(f.kind==MoneyPlanKind.PURCHASE)R.string.p_optional_amount else R.string.m_amount,"plan_amount",numeric=true,hidden=state.preferences.hideAmounts)
        Text(stringResource(R.string.p_optional_funding),style=MaterialTheme.typography.bodySmall)
        if(f.account==null) {
            val spaces=listOf("none" to stringResource(R.string.m_all_spaces))+(state.workspace?.spaces.orEmpty().map{it.id.toString() to spaceTitle(it)})
            MoneyChoice(stringResource(R.string.m_space),spaces.find{it.first==f.space?.toString()}?.second?:stringResource(R.string.m_all_spaces),spaces,{update(f.copy(space=it.toLongOrNull()))})
        }
        PlanningFunding(state,f.currency,f.direction,f.account,f.pocket,f.category,true,{a,p,c->update(f.copy(account=a,pocket=p,category=c))})
        PlanningDate(f.due,R.string.m_date){onEvent(PlanningEvent.DateField("due"))}
        MoneyChoice(stringResource(R.string.p_frequency),frequencyTitle(f.frequency),MoneyFrequency.entries.map{it.name to frequencyTitle(it)},{update(f.copy(frequency=MoneyFrequency.valueOf(it)))})
        PlanningField(f.reminderDays,{update(f.copy(reminderDays=it))},R.string.p_reminder_days,"plan_days",numeric=true)
        Text(stringResource(R.string.p_notifications_time),style=MaterialTheme.typography.bodySmall)
        PlanningField(f.note,{update(f.copy(note=it.take(2000)))},R.string.m_note,"plan_note")
    }
}
@Composable private fun SettlementEditor(state:MoneyPlanningUiState,onEvent:(PlanningEvent)->Unit) {
    val f=state.controls.settlement?:return;val enabled=f.linked==null && !state.controls.saving
    fun update(value:SettlementForm){onEvent(PlanningEvent.Settlement(value))}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(f.name,style=MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.p_confirm_intro))
        PlanningField(f.amount,{update(f.copy(amount=it))},R.string.m_amount,"settlement_amount",enabled=enabled,numeric=true,hidden=state.preferences.hideAmounts)
        Text(f.currency)
        PlanningFunding(state,f.currency,f.direction,f.account,f.pocket,f.category,false,{a,p,c->update(f.copy(account=a,pocket=p,category=c))},enabled)
        PlanningDate(f.date,R.string.m_date,enabled){onEvent(PlanningEvent.DateField("actual"))}
        PlanningField(f.note,{update(f.copy(note=it.take(2000)))},R.string.m_note,"settlement_note",enabled=enabled)
    }
}
@Composable private fun PlanningFunding(state:MoneyPlanningUiState,currency:String,direction:MoneyDirection,account:Long?,pocket:Long?,category:Long?,optional:Boolean,
    change:(Long?,Long?,Long?)->Unit,enabled:Boolean=true) {
    val data=state.workspace?:return
    val none=stringResource(if(optional)R.string.p_none else R.string.p_choose_account)
    val accounts=listOf("none" to none)+data.ledger.snapshot.accounts.filter{!it.account.archived && it.account.initialBalance.currencyCode==currency}.map{it.account.id.toString() to it.account.name}
    MoneyChoice(stringResource(R.string.m_account),accounts.find{it.first==account?.toString()}?.second?:none,accounts,{change(it.toLongOrNull(),null,category)},enabled=enabled)
    val pockets=listOf("none" to stringResource(R.string.m_unallocated))+data.ledger.snapshot.pockets.filter{it.pocket.accountId==account}.map{it.pocket.id.toString() to it.pocket.name}
    MoneyChoice(stringResource(R.string.m_pockets),pockets.find{it.first==pocket?.toString()}?.second?:stringResource(R.string.m_unallocated),pockets,{change(account,it.toLongOrNull(),category)},enabled=enabled)
    val categories=listOf("none" to stringResource(if(optional)R.string.p_none else R.string.p_choose_category))+data.categories.filter{it.isIncome==(direction==MoneyDirection.INCOME)}.map{it.id.toString() to categoryTitle(it)}
    MoneyChoice(stringResource(R.string.m_category),categories.find{it.first==category?.toString()}?.second?:categories.first().second,categories,{change(account,pocket,it.toLongOrNull())},enabled=enabled)
}
@Composable private fun PlanningDate(value:String,label:Int,enabled:Boolean=true,onClick:()->Unit) {
    val date=runCatching{LocalDate.parse(value)}.getOrNull()
    MoneyOutlinedButton(onClick,Modifier.fillMaxWidth(),enabled){Text(stringResource(label)+": "+(date?.let{dayLabel(it)}?:value))}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PlanningDialogs(state:MoneyPlanningUiState,onEvent:(PlanningEvent)->Unit) {
    val c=state.controls
    if(c.deleteBudget!=null || c.deletePlan!=null)AlertDialog(onDismissRequest={onEvent(PlanningEvent.DeleteBudget(null));onEvent(PlanningEvent.DeletePlan(null))},
        title={Text(stringResource(if(c.deleteBudget!=null)R.string.p_delete_budget else R.string.p_delete_plan))},text={Text(stringResource(R.string.p_delete_detail))},
        confirmButton={MoneyTextButton({onEvent(PlanningEvent.ConfirmDelete)},enabled=!c.saving){Text(stringResource(R.string.m_delete))}},
        dismissButton={MoneyTextButton({onEvent(PlanningEvent.DeleteBudget(null));onEvent(PlanningEvent.DeletePlan(null))},enabled=!c.saving){Text(stringResource(R.string.m_cancel))}})
    if(c.dateField!=null){
        val text=when(c.dateField){"from"->c.budget?.from;"through"->c.budget?.through;"due"->c.plan?.due;else->c.settlement?.date}
        val selected=runCatching{LocalDate.parse(text)}.getOrDefault(c.today)
        val picker=rememberDatePickerState(initialSelectedDateMillis=selected.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={onEvent(PlanningEvent.DateField(null))},confirmButton={MoneyTextButton({picker.selectedDateMillis?.let{onEvent(PlanningEvent.Date(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()))}}){Text(stringResource(R.string.m_apply))}},
            dismissButton={MoneyTextButton({onEvent(PlanningEvent.DateField(null))}){Text(stringResource(R.string.m_cancel))}}){DatePicker(picker)}
    }
}
