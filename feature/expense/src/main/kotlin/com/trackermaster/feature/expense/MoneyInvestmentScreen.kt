package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.money.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoneyInvestmentScreen(state: InvestmentUiState, onAction: (InvestmentAction) -> Unit, back: () -> Unit) {
    val hidden = state.preferences.hideAmounts
    val draft = state.draft
    val selected = state.rows.find { it.investment.id == state.selected }
    Scaffold(topBar = {
        MediumTopAppBar(title = { Text(if (draft != null) investmentEditorTitle(draft) else stringResource(R.string.i_title)) },
            navigationIcon = { IconButton({ if (state.draft != null || state.selected != null) onAction(InvestmentAction.Close) else back() }, Modifier.sizeIn(minWidth = 48.dp,minHeight = 48.dp), enabled = !state.saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.m_summary)) } },
            actions = { IconButton({ onAction(InvestmentAction.Privacy) },Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).testTag("investment_privacy")) {
                Icon(if(hidden)Icons.Default.VisibilityOff else Icons.Default.Visibility,stringResource(if(hidden)R.string.m_show else R.string.m_hide)) } })
    }, bottomBar = {
        if (draft != null) Surface { Column(Modifier.imePadding().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(state.error)Text(stringResource(R.string.i_save_error),color=MaterialTheme.colorScheme.error)
            MoneyButton({onAction(InvestmentAction.Save)},Modifier.fillMaxWidth().testTag("investment_save"),enabled=state.canSave) {
                if(state.saving) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Text(stringResource(R.string.m_save))
            }
        } }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("investment_list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(state.loading) items(3) { LinearProgressIndicator(Modifier.fillMaxWidth().height(40.dp)) }
            else if(state.loadError) item { MoneyEmpty(stringResource(R.string.m_load_error),stringResource(R.string.i_retry_detail),stringResource(R.string.m_retry),Icons.Default.Refresh,{onAction(InvestmentAction.Retry)}) }
            else {
                if(state.error) item("error") { Text(stringResource(R.string.i_save_error),color=MaterialTheme.colorScheme.error) }
                if(draft!=null) item("editor") { InvestmentEditor(state,draft,onAction) }
                else if(selected!=null) {
                    val p=selected.investment
                    item("position") { InvestmentCard(selected,hidden,null) }
                    if(!p.archived) item("actions") {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            InvestmentEventKind.entries.forEach { kind -> MoneyOutlinedButton({onAction(InvestmentAction.Operation(p,kind))}) { Text(investmentKindTitle(kind)) } }
                            MoneyTextButton({onAction(InvestmentAction.Edit(p))}) { Text(stringResource(R.string.m_edit)) }
                            if(selected.cost.minorUnits==0L && selected.value.minorUnits==0L) MoneyTextButton({onAction(InvestmentAction.Archive(p))}){Text(stringResource(R.string.i_archive))}
                        }
                    } else item("restore") { MoneyOutlinedButton({onAction(InvestmentAction.Archive(p))}){Text(stringResource(R.string.i_unarchive))} }
                    item("history_heading") { Text(stringResource(R.string.i_history),style=MaterialTheme.typography.titleMedium) }
                    val events=state.book.events.filter { it.investmentId==p.id }.sortedWith(compareByDescending<InvestmentEvent>{it.date}.thenByDescending{it.sequence})
                    if(events.isEmpty()) item("history_empty"){Text(stringResource(R.string.i_no_events))}
                    items(events,key={it.id}) { event -> Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(investmentKindTitle(event.kind));Text(dayLabel(event.date));MoneyAmount(event.amount,hidden)
                            if(event.kind==InvestmentEventKind.WITHDRAWAL){Text(stringResource(R.string.i_cost_returned));MoneyAmount(event.cost,hidden)}
                            if(!hidden && event.note.isNotBlank())Text(event.note)
                            if(!p.archived) MoneyTextButton({onAction(InvestmentAction.Reverse(event))}){Text(stringResource(R.string.i_reverse))}
                        }
                    } }
                } else {
                    item("scope") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("COP","USD").forEach{code->FilterChip(state.currency==code,{onAction(InvestmentAction.Currency(code))},{Text(code)})} }
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(state.space==null,{onAction(InvestmentAction.Space(null))},{Text(stringResource(R.string.m_all_spaces))})
                            state.workspace?.spaces?.forEach { space -> FilterChip(state.space==space.id,{onAction(InvestmentAction.Space(space.id))},{Text(spaceTitle(space))}) }
                        }
                    } }
                    item("explanation"){Text(stringResource(R.string.i_explanation),style=MaterialTheme.typography.bodyMedium)}
                    if(state.rows.isEmpty()) item("empty") { MoneyEmpty(stringResource(R.string.i_empty),stringResource(R.string.i_empty_detail),stringResource(R.string.i_new),Icons.AutoMirrored.Filled.TrendingUp,{onAction(InvestmentAction.New)}) }
                    else {
                        item("new"){MoneyButton({onAction(InvestmentAction.New)},Modifier.fillMaxWidth()){Text(stringResource(R.string.i_new))}}
                        items(state.rows,key={it.investment.id}) { row -> InvestmentCard(row,hidden,{onAction(InvestmentAction.Show(row.investment.id))}) }
                    }
                }
            }
        }
    }
    if(state.dateOpen && draft!=null) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=utcMillis(runCatching{LocalDate.parse(draft.date)}.getOrDefault(LocalDate.now())))
        DatePickerDialog(onDismissRequest={onAction(InvestmentAction.Date(runCatching{LocalDate.parse(draft.date)}.getOrDefault(LocalDate.now())))},
            confirmButton={MoneyTextButton({picker.selectedDateMillis?.let{onAction(InvestmentAction.Date(utcDay(it)))}},enabled=picker.selectedDateMillis!=null){Text(stringResource(R.string.m_apply))}}) { DatePicker(picker) }
    }
    state.reverse?.let { AlertDialog(onDismissRequest={onAction(InvestmentAction.Reverse(null))},title={Text(stringResource(R.string.i_reverse))},
        text={Text(stringResource(R.string.i_reverse_detail))},confirmButton={MoneyTextButton({onAction(InvestmentAction.ConfirmReverse)},enabled=!state.saving){Text(stringResource(R.string.i_reverse))}},
        dismissButton={MoneyTextButton({onAction(InvestmentAction.Reverse(null))},enabled=!state.saving){Text(stringResource(R.string.m_cancel))}}) }
}

@Composable
internal fun InvestmentCard(row: InvestmentProgress, hidden: Boolean, open: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().testTag("investment_card:${row.investment.id}")) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(row.investment.name,style=MaterialTheme.typography.titleLarge)
        if(row.investment.archived)Text(stringResource(R.string.i_archived))
        Text(stringResource(R.string.i_estimated_value));MoneyAmount(row.value,hidden,style=MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.i_valued_on,dayLabel(row.valuedOn)),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.i_cost_basis));MoneyAmount(row.cost,hidden)
        Text(stringResource(R.string.i_realized));MoneyAmount(row.realized,hidden)
        Text(stringResource(R.string.i_unrealized));MoneyAmount(row.unrealized,hidden)
        if(open!=null)MoneyTextButton(open){Text(stringResource(R.string.i_details))}
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvestmentEditor(state: InvestmentUiState, draft: InvestmentDraft, onAction: (InvestmentAction)->Unit) {
    val w=state.workspace ?: return
    val enabled=!state.saving
    val linked=draft.linked!=null
    fun change(value:InvestmentDraft){onAction(InvestmentAction.Draft(value))}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(stringResource(when(draft.kind){null->if(draft.editing)R.string.i_edit_opening_hint else R.string.i_existing_hint;InvestmentEventKind.VALUATION->R.string.i_valuation_hint;InvestmentEventKind.WITHDRAWAL->R.string.i_withdraw_hint;else->R.string.i_cash_hint}))
        if(draft.kind==null) OutlinedTextField(draft.name,{change(draft.copy(name=it))},Modifier.fillMaxWidth().testTag("investment_name"),label={Text(stringResource(R.string.m_name))},enabled=enabled,singleLine=true)
        if(draft.kind==null && !draft.editing){
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("COP","USD").forEach{code->FilterChip(draft.currency==code,{change(draft.copy(currency=code))},{Text(code)},enabled=enabled)}}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){w.spaces.forEach{space->FilterChip(draft.space==space.id,{change(draft.copy(space=space.id))},{Text(spaceTitle(space))},enabled=enabled)}}
        }
        if(!draft.editing || draft.kind==null) {
            Text(draft.currency,style=MaterialTheme.typography.labelLarge)
            OutlinedTextField(draft.amount,{change(draft.copy(amount=it.replace(',','.')))},Modifier.fillMaxWidth().testTag("investment_amount"),
                label={Text(stringResource(if(draft.editing)R.string.i_opening_value else if(draft.kind==null || draft.kind==InvestmentEventKind.VALUATION)R.string.i_estimated_value else R.string.m_amount))},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),visualTransformation=if(state.preferences.hideAmounts)PasswordVisualTransformation()else VisualTransformation.None,enabled=enabled && !linked,singleLine=true)
            if(draft.kind==null || draft.kind==InvestmentEventKind.WITHDRAWAL) OutlinedTextField(draft.cost,{change(draft.copy(cost=it.replace(',','.')))},Modifier.fillMaxWidth().testTag("investment_cost"),
                label={Text(stringResource(if(draft.kind==null)R.string.i_initial_cost else R.string.i_cost_returned))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),visualTransformation=if(state.preferences.hideAmounts)PasswordVisualTransformation()else VisualTransformation.None,enabled=enabled,singleLine=true)
            MoneyOutlinedButton({onAction(InvestmentAction.Date(null))},enabled=enabled && !linked){Text(stringResource(R.string.i_date,dayLabel(runCatching{LocalDate.parse(draft.date)}.getOrDefault(LocalDate.now()))))}
        }
        if(draft.kind!=null && draft.kind!=InvestmentEventKind.VALUATION) {
            Text(stringResource(R.string.m_account),style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                w.ledger.snapshot.accounts.filter{!it.account.archived && it.total.currencyCode==draft.currency}.forEach{row->
                    if(row.account.spaceId==draft.space)FilterChip(draft.account==row.account.id && draft.pocket==null,{change(draft.copy(account=row.account.id,pocket=null))},{Text(row.account.name)},enabled=enabled && !linked)
                    w.ledger.snapshot.pockets.filter{it.pocket.accountId==row.account.id && it.pocket.spaceId==draft.space}.forEach{pocket->
                        FilterChip(draft.pocket==pocket.pocket.id,{change(draft.copy(account=row.account.id,pocket=pocket.pocket.id))},{Text("${row.account.name} · ${pocket.pocket.name}")},enabled=enabled && !linked)
                    }
                }
            }
            if(draft.kind==InvestmentEventKind.DIVIDEND) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){w.categories.filter{it.isIncome}.forEach{c->
                FilterChip(draft.category==c.id,{change(draft.copy(category=c.id))},{Text(categoryTitle(c))},enabled=enabled && !linked)
            }}
        }
        OutlinedTextField(draft.note,{change(draft.copy(note=it))},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.m_note))},enabled=enabled && !linked)
        if(linked) MoneyOutlinedButton({onAction(InvestmentAction.Link(null))},enabled=enabled){Text(stringResource(R.string.i_unlink))}
        else state.candidates.forEach{movement-> Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
            Text(dayLabel(movement.date));MoneyAmount(movement.amount,state.preferences.hideAmounts)
            MoneyTextButton({onAction(InvestmentAction.Link(movement))},enabled=enabled){Text(stringResource(R.string.i_link))}
        }}}
        if(!state.canSave && !state.saving)Text(stringResource(R.string.i_incomplete),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun investmentKindTitle(kind:InvestmentEventKind):String=stringResource(when(kind){
    InvestmentEventKind.CONTRIBUTION->R.string.i_contribute
    InvestmentEventKind.WITHDRAWAL->R.string.i_withdraw
    InvestmentEventKind.DIVIDEND->R.string.i_dividend
    InvestmentEventKind.VALUATION->R.string.i_valuation
})
@Composable private fun investmentEditorTitle(draft:InvestmentDraft):String=when(draft.kind){
    InvestmentEventKind.VALUATION->stringResource(R.string.i_valuation_title)
    InvestmentEventKind.DIVIDEND->stringResource(R.string.i_dividend_title)
    null->stringResource(if(draft.editing)R.string.m_edit else R.string.i_new)
    else->investmentKindTitle(draft.kind)
}
