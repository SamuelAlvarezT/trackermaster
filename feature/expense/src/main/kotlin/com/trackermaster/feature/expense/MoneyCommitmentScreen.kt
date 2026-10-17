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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.money.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyCommitmentScreen(state: MoneyCommitmentUiState, onEvent: (CommitmentEvent)->Unit, onBack: ()->Unit,
    onNotifications: ()->Unit = {}) {
    val c=state.controls; val f=c.draft
    val title=stringResource(if(f!=null)when(f.editor){CommitmentEditor.DEBT->R.string.c_debt;CommitmentEditor.GOAL->R.string.c_goal;CommitmentEditor.REPAYMENT->R.string.c_repay;CommitmentEditor.ALLOCATION->if(f.withdraw)R.string.c_withdraw else R.string.c_contribute}else R.string.c_title)
    Scaffold(topBar={CommitmentTopBar(title,f!=null,c.saving,state.preferences.hideAmounts,
        {if(f!=null || c.detail!=null)onEvent(CommitmentEvent.Close)else onBack()},
        {onEvent(CommitmentEvent.Privacy)},onNotifications)
    },bottomBar={if(f!=null)Surface(tonalElevation=3.dp){Column(Modifier.imePadding().navigationBarsPadding().padding(16.dp)){
        FilledTonalButton({onEvent(CommitmentEvent.Save)},Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("commitment_save"),enabled=state.canSave){Text(stringResource(if(c.saving)R.string.m_saving else R.string.m_save))}
    }}}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("commitment_list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(c.error!=null)item("error"){Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium){Column(Modifier.padding(16.dp)){
                Text(stringResource(when(c.error){"LOAD"->R.string.m_load_error;"INVALID"->R.string.c_invalid;"FUNDS"->R.string.m_funds_error;"CONFLICT"->R.string.c_conflict;else->R.string.m_save_error}))
                if(c.error=="LOAD")MoneyTextButton({onEvent(CommitmentEvent.Retry)}){Text(stringResource(R.string.m_retry))}
            }}}
            when {
                state.loading->items(3,key={"loading:$it"}){Surface(Modifier.fillMaxWidth().height(100.dp),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant){}}
                state.workspace==null->item("retry"){MoneyTextButton({onEvent(CommitmentEvent.Retry)}){Text(stringResource(R.string.m_retry))}}
                f!=null->{item("editor"){CommitmentForm(state,onEvent)}
                    if(f.editor==CommitmentEditor.REPAYMENT){
                        item("link_info"){Text(stringResource(R.string.c_receipt_hint),style=MaterialTheme.typography.bodySmall)}
                        if(f.linked!=null)item("linked"){Text(stringResource(R.string.p_linked));MoneyOutlinedButton({onEvent(CommitmentEvent.Link(null))},Modifier.fillMaxWidth()){Text(stringResource(R.string.p_new_movement))}}
                        items(state.candidates,key={"candidate:${it.id}"}){m->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(dayLabel(m.date));MoneyAmount(m.amount,state.preferences.hideAmounts)
                            MoneyTextButton({onEvent(CommitmentEvent.Link(m.id))}){Text(stringResource(R.string.p_link_movement))}}}}
                    }
                }
                c.detail!=null->{val debt=state.data.debts.find{it.id==c.detail}
                    if(debt!=null){item("detail_title"){Text(debt.person,style=MaterialTheme.typography.headlineSmall);Text(stringResource(R.string.c_history_hint),style=MaterialTheme.typography.bodySmall)}
                        val movements=state.workspace.ledger.movements.associateBy{it.id}
                        val payments=state.data.payments.filter{it.debtId==debt.id}.sortedByDescending{movements[it.movementId]?.date}
                        if(payments.isEmpty())item("no_payments"){Text(stringResource(R.string.c_no_payments))}
                        items(payments,key={it.id}){p->val m=movements[p.movementId]
                            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text(stringResource(if(p.event==PrincipalEvent.DISBURSEMENT)R.string.c_disbursement else R.string.c_repay));m?.let{Text(dayLabel(it.date));MoneyAmount(it.amount,state.preferences.hideAmounts)}
                                if(p.event==PrincipalEvent.REPAYMENT && !debt.archived)MoneyTextButton({onEvent(CommitmentEvent.Reverse(p.id))}){Text(stringResource(R.string.c_reverse))}
                            }}
                        }
                    }
                }
                else->{
                    item("filters"){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                        TabRow(if(c.tab=="debts")0 else 1){Tab(c.tab=="debts",{onEvent(CommitmentEvent.Tab("debts"))},text={Text(stringResource(R.string.c_debts))});Tab(c.tab=="goals",{onEvent(CommitmentEvent.Tab("goals"))},text={Text(stringResource(R.string.c_goals))})}
                        MoneyChoice(stringResource(R.string.m_currency),c.currency,listOf("COP" to "COP","USD" to "USD"),{onEvent(CommitmentEvent.Currency(it))})
                        val spaces=listOf("" to stringResource(R.string.m_all_spaces))+state.workspace.spaces.map{it.id.toString() to spaceTitle(it)}
                        MoneyChoice(stringResource(R.string.m_space),spaces.find{it.first==c.space?.toString()}?.second?:spaces.first().second,spaces,{onEvent(CommitmentEvent.Space(it.toLongOrNull()))})
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.c_show_archived),Modifier.weight(1f));Switch(c.archived,{onEvent(CommitmentEvent.Archived(it))})}
                    }}
                    if(c.tab=="debts") {
                        item("totals"){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Text(stringResource(R.string.c_payable));MoneyAmount(state.payable,state.preferences.hideAmounts)
                            HorizontalDivider();Text(stringResource(R.string.c_receivable));MoneyAmount(state.receivable,state.preferences.hideAmounts)
                            Text(stringResource(R.string.c_principal_hint),style=MaterialTheme.typography.bodySmall)
                        }}}
                        item("new_debt"){FilledTonalButton({onEvent(CommitmentEvent.New(CommitmentEditor.DEBT))},Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.Add,null);Text(stringResource(R.string.c_new_debt))}}
                        if(state.debts.isEmpty())item("empty"){MoneyEmpty(stringResource(R.string.c_empty_debts),stringResource(R.string.c_empty_debts_detail),stringResource(R.string.c_new_debt),Icons.Default.Handshake,{onEvent(CommitmentEvent.New(CommitmentEditor.DEBT))})}
                        items(state.debts,key={it.debt.id}){DebtCard(it,state.preferences.hideAmounts,onEvent)}
                    } else {
                        item("goal_hint"){Text(stringResource(R.string.c_goal_hint),style=MaterialTheme.typography.bodyMedium)}
                        item("new_goal"){FilledTonalButton({onEvent(CommitmentEvent.New(CommitmentEditor.GOAL))},Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.Add,null);Text(stringResource(R.string.c_new_goal))}}
                        if(state.goals.isEmpty())item("empty"){MoneyEmpty(stringResource(R.string.c_empty_goals),stringResource(R.string.c_goal_hint),stringResource(R.string.c_new_goal),Icons.Default.Savings,{onEvent(CommitmentEvent.New(CommitmentEditor.GOAL))})}
                        items(state.goals,key={it.goal.id}){progress->
                            val pocket=state.workspace.ledger.snapshot.pockets.single{it.pocket.id==progress.goal.pocketId}.pocket
                            val account=state.workspace.ledger.snapshot.accounts.single{it.account.id==pocket.accountId}.account
                            GoalCard(progress,state.preferences.hideAmounts,"${account.name} · ${pocket.name}",onEvent)
                        }
                    }
                }
            }
        }
        CommitmentDialogs(state,onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CommitmentTopBar(title:String,editing:Boolean,saving:Boolean,hidden:Boolean,
    onBack:()->Unit,onPrivacy:()->Unit,onNotifications:()->Unit) {
    val navigation: @Composable ()->Unit = {
        IconButton(onBack,enabled=!saving){Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.r_back))}
    }
    val actions: @Composable RowScope.()->Unit = {
        if(!editing)IconButton(onNotifications,Modifier.testTag("commitment_notifications"),enabled=!saving){Icon(Icons.Default.NotificationsNone,stringResource(R.string.c_notifications))}
        IconButton(onPrivacy,Modifier.testTag("commitment_privacy"),enabled=!saving){Icon(if(hidden)Icons.Default.Visibility else Icons.Default.VisibilityOff,stringResource(if(hidden)R.string.m_show else R.string.m_hide))}
    }
    // A separate title row keeps navigation and privacy controls visible with enlarged text.
    if(LocalDensity.current.fontScale>=1.3f)MediumTopAppBar(title={Text(title)},navigationIcon=navigation,actions=actions)
    else TopAppBar(title={Text(title)},navigationIcon=navigation,actions=actions)
}

@Composable private fun DebtCard(progress:DebtProgress, hidden:Boolean, event:(CommitmentEvent)->Unit) {
    val d=progress.debt
    Card(Modifier.fillMaxWidth().testTag("debt:${d.id}")){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(d.person,style=MaterialTheme.typography.titleLarge)
        Text(stringResource(if(d.kind==DebtKind.PAYABLE)R.string.c_payable else R.string.c_receivable),style=MaterialTheme.typography.labelLarge)
        MoneyAmount(progress.outstanding,hidden,style=MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.c_paid,moneyLabel(progress.paid,hidden)),style=MaterialTheme.typography.bodySmall)
        d.due?.let{Text(stringResource(R.string.p_due,dayLabel(it)),style=MaterialTheme.typography.bodySmall)}
        Text(stringResource(if(d.archived)R.string.c_archived else if(progress.outstanding.minorUnits==0L)R.string.c_settled else if(progress.overdue)R.string.c_overdue else R.string.c_pending),color=if(progress.overdue && !d.archived)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if(!hidden && d.note.isNotBlank())Text(d.note,style=MaterialTheme.typography.bodySmall)
        if(!d.archived && progress.outstanding.minorUnits>0)FilledTonalButton({event(CommitmentEvent.Repay(d))},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.c_repay))}
        MoneyTextButton({event(CommitmentEvent.Detail(d.id))},Modifier.fillMaxWidth()){Text(stringResource(R.string.c_payment_history))}
        if(!d.archived)MoneyTextButton({event(CommitmentEvent.Debt(d))},Modifier.fillMaxWidth()){Text(stringResource(R.string.m_edit))}
        MoneyTextButton({event(CommitmentEvent.ArchiveDebt(d))},Modifier.fillMaxWidth(),enabled=d.archived || progress.outstanding.minorUnits==0L){Text(stringResource(if(d.archived)R.string.c_restore else R.string.c_archive))}
    }}
}
@Composable private fun GoalCard(progress:GoalProgress,hidden:Boolean,funding:String,event:(CommitmentEvent)->Unit) {
    val g=progress.goal;val description=stringResource(R.string.c_goal_progress,progress.percent)
    Card(Modifier.fillMaxWidth().testTag("goal:${g.id}")){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(g.name,style=MaterialTheme.typography.titleLarge)
        Text(funding,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.c_reserved));MoneyAmount(progress.reserved,hidden,style=MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.c_target,moneyLabel(g.target,hidden)),style=MaterialTheme.typography.bodySmall)
        if(!hidden){LinearProgressIndicator(progress={progress.percent/100f},modifier=Modifier.fillMaxWidth().height(8.dp).semantics{contentDescription=description});Text(description,style=MaterialTheme.typography.labelLarge)}
        Text(stringResource(R.string.c_remaining,moneyLabel(progress.remaining,hidden)),style=MaterialTheme.typography.bodySmall)
        g.due?.let{Text(stringResource(R.string.p_due,dayLabel(it)),style=MaterialTheme.typography.bodySmall)}
        if(g.archived)Text(stringResource(R.string.c_archived)) else {
            FilledTonalButton({event(CommitmentEvent.Allocate(g,false))},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(stringResource(R.string.c_contribute))}
            MoneyOutlinedButton({event(CommitmentEvent.Allocate(g,true))},Modifier.fillMaxWidth(),enabled=progress.reserved.minorUnits>0){Text(stringResource(R.string.c_withdraw))}
        }
        if(!g.archived)MoneyTextButton({event(CommitmentEvent.Goal(g))},Modifier.fillMaxWidth()){Text(stringResource(R.string.m_edit))}
        MoneyTextButton({event(CommitmentEvent.ArchiveGoal(g))},Modifier.fillMaxWidth()){Text(stringResource(if(g.archived)R.string.c_restore else R.string.c_archive))}
    }}
}

@Composable private fun CommitmentForm(state:MoneyCommitmentUiState,event:(CommitmentEvent)->Unit) {
    val f=requireNotNull(state.controls.draft); val w=requireNotNull(state.workspace);val locked=f.linked!=null
    val debtHasHistory=state.data.payments.any{it.debtId==f.id}
    fun update(value:CommitmentDraft){event(CommitmentEvent.Draft(value))}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(f.name.isNotBlank() && f.editor in setOf(CommitmentEditor.REPAYMENT,CommitmentEditor.ALLOCATION))Text(f.name,style=MaterialTheme.typography.titleLarge)
        if(f.editor==CommitmentEditor.DEBT || f.editor==CommitmentEditor.GOAL)CommitmentField(f.name,{update(f.copy(name=it.take(120)))},if(f.editor==CommitmentEditor.DEBT)R.string.c_person else R.string.m_name,"commitment_name")
        if(f.editor==CommitmentEditor.DEBT){MoneyChoice(stringResource(R.string.c_debt),stringResource(if(f.kind==DebtKind.PAYABLE)R.string.c_payable else R.string.c_receivable),listOf("PAYABLE" to stringResource(R.string.c_payable),"RECEIVABLE" to stringResource(R.string.c_receivable)),{update(f.copy(kind=DebtKind.valueOf(it)))},enabled=!debtHasHistory)}
        if(f.editor in setOf(CommitmentEditor.DEBT,CommitmentEditor.GOAL))MoneyChoice(stringResource(R.string.m_currency),f.currency,listOf("COP" to "COP","USD" to "USD"),{update(f.copy(currency=it,account=null,pocket=null))},enabled=!f.existing || (f.editor==CommitmentEditor.DEBT && !debtHasHistory))
        CommitmentField(f.amount,{update(f.copy(amount=it))},if(f.editor==CommitmentEditor.GOAL)R.string.c_target_amount else R.string.m_amount,"commitment_amount",numeric=true,enabled=!locked,hidden=state.preferences.hideAmounts)
        if(f.editor==CommitmentEditor.DEBT && f.existing)Text(stringResource(if(state.data.payments.any{it.debtId==f.id && it.event==PrincipalEvent.DISBURSEMENT})R.string.c_edit_capital else R.string.c_existing_hint),style=MaterialTheme.typography.bodySmall)
        if(f.editor==CommitmentEditor.DEBT || (f.editor==CommitmentEditor.GOAL && f.pocket==null)) {
            val spaces=w.spaces.map{it.id.toString() to spaceTitle(it)}
            MoneyChoice(stringResource(R.string.m_space),spaces.find{it.first==f.space.toString()}?.second.orEmpty(),spaces,{update(f.copy(space=it.toLong()))},enabled=!f.existing || (f.editor==CommitmentEditor.DEBT && !debtHasHistory))
        }
        if(f.editor==CommitmentEditor.DEBT && !f.existing){
            MoneyChoice(stringResource(R.string.c_opening_mode),stringResource(if(f.disburse)R.string.c_new_loan else R.string.c_existing_debt),listOf("false" to stringResource(R.string.c_existing_debt),"true" to stringResource(R.string.c_new_loan)),{update(f.copy(disburse=it.toBoolean()))})
            Text(stringResource(if(f.disburse)R.string.c_new_loan_hint else R.string.c_existing_hint),style=MaterialTheme.typography.bodySmall)
        }
        if(f.editor==CommitmentEditor.GOAL && !f.existing) {
            val used=state.data.goals.map{it.pocketId}.toSet()
            val pockets=listOf("" to stringResource(R.string.c_new_pocket))+w.ledger.snapshot.pockets.filter{p->p.balance.currencyCode==f.currency && p.pocket.id !in used && w.ledger.snapshot.accounts.any{it.account.id==p.pocket.accountId && !it.account.archived}}.map{it.pocket.id.toString() to it.pocket.name}
            MoneyChoice(stringResource(R.string.m_pockets),pockets.find{it.first==f.pocket?.toString()}?.second?:pockets.first().second,pockets,{update(f.copy(pocket=it.toLongOrNull()))})
        }
        if((f.editor==CommitmentEditor.GOAL && f.existing) || f.editor==CommitmentEditor.ALLOCATION) {
            val pocket=w.ledger.snapshot.pockets.find{it.pocket.id==f.pocket}
            val account=w.ledger.snapshot.accounts.find{it.account.id==pocket?.pocket?.accountId}
            if(pocket!=null && account!=null)Text("${account.account.name} · ${pocket.pocket.name}",style=MaterialTheme.typography.bodyMedium)
            if(f.editor==CommitmentEditor.ALLOCATION) {
                val available=if(f.withdraw)pocket?.balance else w.ledger.snapshot.accounts.find{it.account.id==f.account}?.unallocated
                if(available!=null){Text(stringResource(if(f.withdraw)R.string.c_reserved else R.string.m_available),style=MaterialTheme.typography.labelMedium);MoneyAmount(available,state.preferences.hideAmounts)}
            }
        }
        if(f.editor==CommitmentEditor.REPAYMENT || (f.editor==CommitmentEditor.DEBT && f.disburse) || f.editor==CommitmentEditor.ALLOCATION || (f.editor==CommitmentEditor.GOAL && !f.existing && f.pocket==null)) {
            val accounts=w.ledger.snapshot.accounts.filter{!it.account.archived && it.total.currencyCode==f.currency}.map{it.account.id.toString() to it.account.name}
            MoneyChoice(stringResource(if(f.editor==CommitmentEditor.ALLOCATION){if(f.withdraw)R.string.m_target else R.string.m_source}else R.string.m_account),accounts.find{it.first==f.account?.toString()}?.second?:stringResource(R.string.m_account_error),accounts,{update(f.copy(account=it.toLong(),pocket=if(f.editor==CommitmentEditor.ALLOCATION)f.pocket else null))},enabled=!locked)
            if(f.editor==CommitmentEditor.REPAYMENT || (f.editor==CommitmentEditor.DEBT && f.disburse)) {
                val pockets=listOf("" to stringResource(R.string.m_unallocated))+w.ledger.snapshot.pockets.filter{it.pocket.accountId==f.account}.map{it.pocket.id.toString() to it.pocket.name}
                MoneyChoice(stringResource(R.string.m_pockets),pockets.find{it.first==f.pocket?.toString()}?.second?:pockets.first().second,pockets,{update(f.copy(pocket=it.toLongOrNull()))},enabled=!locked)
                Text(stringResource(R.string.c_funding_hint),style=MaterialTheme.typography.bodySmall)
            }
        }
        if(f.editor==CommitmentEditor.REPAYMENT || f.editor==CommitmentEditor.ALLOCATION || f.disburse)CommitmentDate(f.date,R.string.m_date,locked){event(CommitmentEvent.DateField("date"))}
        if(f.editor in setOf(CommitmentEditor.DEBT,CommitmentEditor.GOAL)) {
            CommitmentDate(f.due,R.string.c_due_optional,false){event(CommitmentEvent.DateField("due"))}
            if(f.due.isNotBlank())MoneyTextButton({update(f.copy(due=""))}){Text(stringResource(R.string.c_clear_due))}
        }
        if(f.editor==CommitmentEditor.DEBT || f.editor==CommitmentEditor.REPAYMENT)CommitmentField(f.note,{update(f.copy(note=it.take(2000)))},R.string.m_note,"commitment_note",enabled=!locked,hidden=state.preferences.hideAmounts)
        if(f.editor==CommitmentEditor.GOAL || f.editor==CommitmentEditor.ALLOCATION)Text(stringResource(R.string.c_goal_hint),style=MaterialTheme.typography.bodySmall)
        if(!state.canSave && !state.controls.saving)Text(stringResource(R.string.c_invalid),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun CommitmentField(value:String,change:(String)->Unit,label:Int,tag:String,numeric:Boolean=false,enabled:Boolean=true,hidden:Boolean=false) {
    OutlinedTextField(value,change,Modifier.fillMaxWidth().testTag(tag),enabled=enabled,label={Text(stringResource(label))},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text),visualTransformation=if(hidden)PasswordVisualTransformation()else VisualTransformation.None)
}
@Composable private fun CommitmentDate(value:String,label:Int,locked:Boolean,click:()->Unit) {
    MoneyOutlinedButton(click,Modifier.fillMaxWidth(),enabled=!locked){Column(Modifier.fillMaxWidth()){
        Text(stringResource(label),style=MaterialTheme.typography.labelSmall)
        Text(value.takeIf{it.isNotBlank()}?.let{dayLabel(LocalDate.parse(it))}?:stringResource(R.string.c_no_due))
    }}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CommitmentDialogs(state:MoneyCommitmentUiState,event:(CommitmentEvent)->Unit) {
    if(state.controls.dateField!=null) {
        val f=state.controls.draft;val date=if(state.controls.dateField=="due")f?.due else f?.date
        val initial=runCatching{LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
        val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(onDismissRequest={event(CommitmentEvent.DateField(null))},confirmButton={MoneyTextButton({picker.selectedDateMillis?.let{event(CommitmentEvent.Date(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))}}){Text(stringResource(R.string.m_apply))}},dismissButton={MoneyTextButton({event(CommitmentEvent.DateField(null))}){Text(stringResource(R.string.m_cancel))}}){DatePicker(picker)}
    }
    if(state.controls.reverse!=null)AlertDialog(onDismissRequest={event(CommitmentEvent.Reverse(null))},title={Text(stringResource(R.string.c_reverse))},text={Text(stringResource(R.string.c_reverse_hint))},confirmButton={MoneyTextButton({event(CommitmentEvent.ConfirmReverse)},enabled=!state.controls.saving){Text(stringResource(R.string.m_apply))}},dismissButton={MoneyTextButton({event(CommitmentEvent.Reverse(null))},enabled=!state.controls.saving){Text(stringResource(R.string.m_cancel))}})
}
