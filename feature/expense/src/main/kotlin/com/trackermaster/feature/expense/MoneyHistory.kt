package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.money.MoneyDirection

@Composable
fun MoneyHistoryScreen(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val history = state.history
    var createDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupMenu by remember { mutableStateOf(false) }
    val groupedRows = state.userGroups.flatMap { it.rows }.map { it.key }.toSet()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(history.filters.query, { onEvent(MoneyEvent.Filters(history.filters.copy(query = it))) },
                Modifier.weight(1f).testTag("money_search"), singleLine = true,
                label = { Text(stringResource(R.string.m_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) })
            IconButton({ onEvent(MoneyEvent.OpenPanel("filters")) }, Modifier.testTag("money_filters")) {
                Icon(Icons.Default.FilterList, stringResource(R.string.m_filters)) }
        }
        if (state.controls.selectedGroupKeys.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                Text(stringResource(R.string.group_selected,state.controls.selectedGroupKeys.size))
                MoneyTextButton({groupName="";createDialog=true},Modifier.fillMaxWidth(),enabled=!state.controls.saving) {Text(stringResource(R.string.group_create))}
                if (state.userGroups.isNotEmpty()) Box {
                    MoneyTextButton({groupMenu=true},Modifier.fillMaxWidth(),enabled=!state.controls.saving) { Text(stringResource(R.string.group_add_existing)) }
                    DropdownMenu(groupMenu,{groupMenu=false}) { state.userGroups.forEach { item ->
                        DropdownMenuItem(text={Text(item.group.name)},onClick={groupMenu=false;onEvent(MoneyEvent.AddToGroup(item.group.id))})
                    } }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("money_history"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.userGroups.isEmpty() && history.groups.isEmpty()) item("empty") {
                val hasFilters = history.filters != MoneyFilters()
                MoneyEmpty(stringResource(if (hasFilters) R.string.m_no_results else R.string.m_no_movements),
                    stringResource(if (hasFilters) R.string.m_no_results_detail else R.string.m_no_movements_detail),
                    stringResource(if (hasFilters) R.string.m_clear_filters else if (history.hasAccounts) R.string.m_add_expense else R.string.m_create_account),
                    Icons.AutoMirrored.Filled.ReceiptLong, {
                        if (hasFilters) onEvent(MoneyEvent.Filters(MoneyFilters()))
                        else if (history.hasAccounts) onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE)) else onEvent(MoneyEvent.OpenAccount())
                    })
            }
            if (state.userGroups.isNotEmpty()) item("groups_title") { Text(stringResource(R.string.group_history_title),style=MaterialTheme.typography.titleMedium) }
            items(state.userGroups, key={"user-group:${it.group.id}"}) { item -> MoneyUserGroupCard(item,state.preferences.hideAmounts,onEvent) }
            history.groups.forEach { group ->
                val ungrouped = group.rows.filterNot { it.key in groupedRows }
                if (ungrouped.isNotEmpty()) item("day:${group.day}") {
                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(dayLabel(group.day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.m_day_net, moneyLabel(MoneyPresentation.groups(ungrouped, group.net.currencyCode).single().net, state.preferences.hideAmounts)),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(ungrouped, key = { it.key }) { row ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        val key = "movement:${row.movement.id}"
                        Checkbox(key in state.controls.selectedGroupKeys,{onEvent(MoneyEvent.ToggleGroupMember(key))},Modifier.testTag("group-select:${row.key}"),enabled=!state.controls.saving)
                        Box(Modifier.weight(1f)) { MoneySwipeRow(row, state.preferences.hideAmounts, onEvent) }
                    }
                }
            }
        }
    }
    if (createDialog) AlertDialog(onDismissRequest={createDialog=false},title={Text(stringResource(R.string.group_create))},
        text={OutlinedTextField(groupName,{groupName=it.take(120)},label={Text(stringResource(R.string.group_name))},singleLine=true)},
        confirmButton={TextButton(onClick={createDialog=false;onEvent(MoneyEvent.CreateGroup(groupName))},enabled=groupName.isNotBlank()) {Text(stringResource(R.string.m_save))}},
        dismissButton={TextButton(onClick={createDialog=false}) {Text(stringResource(R.string.m_cancel))}})
}

@Composable
private fun MoneyUserGroupCard(item: MoneyUserGroup, hidden: Boolean, onEvent: (MoneyEvent) -> Unit) {
    var expanded by remember(item.group.id) { mutableStateOf(false) }
    var remove by remember(item.group.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.group.name,style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.group_count,item.rows.size,item.drafts.size),style=MaterialTheme.typography.bodySmall)
                    if(item.totals.isNotEmpty()) Column {
                        Text(stringResource(R.string.group_net),style=MaterialTheme.typography.labelSmall)
                        item.totals.forEach { MoneyAmount(it,hidden,style=MaterialTheme.typography.bodySmall) }
                    }
                }
                IconButton(onClick={expanded=!expanded},modifier=Modifier.testTag("group-expand:${item.group.id}")) {Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,stringResource(R.string.group_expand))}
                IconButton(onClick={remove=true},modifier=Modifier.testTag("group-remove:${item.group.id}")) {Icon(Icons.Default.DeleteOutline,stringResource(R.string.group_remove))}
            }
            if(expanded) {
                item.rows.forEach { row ->
                    Text(listOfNotNull(dayLabel(row.movement.date),item.receiptTimes[row.key]).joinToString(" · "),style=MaterialTheme.typography.labelSmall)
                    MoneySwipeRow(row,hidden,onEvent)
                    MoneyTextButton({
                        when {
                            row.movement.investmentId != null -> onEvent(MoneyEvent.Investments)
                            row.movement.principal -> onEvent(MoneyEvent.Commitments())
                            else -> onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE,row))
                        }
                    }) { Text(stringResource(R.string.group_view_detail)) }
                }
                item.drafts.forEach { draft ->
                    MoneyOutlinedButton({onEvent(MoneyEvent.ReviewDraft(draft.id))},Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.group_draft_in,draft.name))
                    }
                }
            }
        }
    }
    if(remove) AlertDialog(onDismissRequest={remove=false},title={Text(stringResource(R.string.group_remove))},
        text={Text(stringResource(R.string.group_remove_detail))},
        confirmButton={TextButton(onClick={remove=false;onEvent(MoneyEvent.DeleteGroup(item.group.id))}) {Text(stringResource(R.string.group_remove))}},
        dismissButton={TextButton(onClick={remove=false}) {Text(stringResource(R.string.m_cancel))}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneySwipeRow(row: MoneyHistoryRow, hidden: Boolean, onEvent: (MoneyEvent) -> Unit) {
    if(row.movement.principal || row.movement.investmentId!=null){MoneyTransactionCard(row,hidden,onEvent);return}
    var confirmSwipeDelete by remember(row.key) { mutableStateOf(false) }
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE, row))
            SwipeToDismissBoxValue.EndToStart -> confirmSwipeDelete = true
            else -> Unit
        }
        false // The database is the source of truth; do not dismiss optimistically after a failed write.
    })
    SwipeToDismissBox(swipe, backgroundContent = {
        Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large,
            color = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Default.Edit, stringResource(R.string.m_edit))
                Icon(Icons.Default.Delete, stringResource(R.string.m_delete))
            }
        }
    }) { MoneyTransactionCard(row, hidden, onEvent) }
    if (confirmSwipeDelete) AlertDialog(onDismissRequest = { confirmSwipeDelete = false },
        title = { Text(stringResource(R.string.m_delete_title)) }, text = { Text(stringResource(R.string.m_delete_detail)) },
        confirmButton = { MoneyTextButton({ confirmSwipeDelete = false; onEvent(MoneyEvent.Delete(row.movement.id)) }) { Text(stringResource(R.string.m_delete)) } },
        dismissButton = { MoneyTextButton({ confirmSwipeDelete = false }) { Text(stringResource(R.string.m_cancel)) } })
}

@Composable
internal fun MoneyTransactionCard(row: MoneyHistoryRow, hidden: Boolean, onEvent: (MoneyEvent) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val edit = stringResource(R.string.m_edit)
    val delete = stringResource(R.string.m_delete)
    val protected = row.movement.principal || row.movement.investmentId != null
    val manageEvent: MoneyEvent = if(row.movement.investmentId != null)MoneyEvent.Investments else MoneyEvent.Commitments()
    val title = if(row.movement.investmentId!=null)stringResource(R.string.i_title)else if(row.movement.principal)stringResource(R.string.c_principal)else if (row.incoming == null) categoryTitle(row.category) else stringResource(R.string.m_transfer)
    val manage = stringResource(if(row.movement.investmentId!=null)R.string.i_title else R.string.c_debts)
    Card(Modifier.fillMaxWidth().testTag("entry:${row.key}").semantics {
        customActions = if(protected)listOf(CustomAccessibilityAction(manage){onEvent(manageEvent);true})else listOf(CustomAccessibilityAction(edit) { onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE, row)); true },
            CustomAccessibilityAction(delete) { confirmDelete = true; true })
    }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Icon(if (row.incoming != null) Icons.Default.SwapHoriz else categoryIcon(row.category), null,
                    Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val source = listOfNotNull(row.accountName, row.pocketName).joinToString(" · ")
                val target = listOfNotNull(row.targetName, row.targetPocketName).joinToString(" · ")
                Text(if (row.incoming == null) source else "$source → $target", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if(protected)stringResource(if(row.movement.direction==MoneyDirection.INCOME)R.string.c_money_in else R.string.c_money_out)else if (row.incoming != null) stringResource(R.string.m_transfer) else
                        stringResource(if (row.movement.direction == MoneyDirection.INCOME) R.string.m_income else R.string.m_expense),
                        style = MaterialTheme.typography.labelSmall)
                    MoneyAmount(row.movement.amount, hidden, style = MaterialTheme.typography.bodyMedium)
                }
                if (row.incoming != null && row.incoming.amount.currencyCode != row.movement.amount.currencyCode)
                    MoneyAmount(row.incoming.amount, hidden, style = MaterialTheme.typography.bodyMedium)
                if (!hidden && row.movement.note.isNotBlank()) Text(row.movement.note, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.m_options)) }
                DropdownMenu(menu, { menu = false }) {
                    row.movement.attachmentPath?.takeIf { it.startsWith("money/receipts/") }?.let { path ->
                        DropdownMenuItem({Text(stringResource(R.string.r_original))}, {menu=false;onEvent(MoneyEvent.ViewReceipt(path))},enabled=!hidden)
                    }
                    if(protected)DropdownMenuItem({Text(manage)},{menu=false;onEvent(manageEvent)})else {
                        DropdownMenuItem({ Text(edit) }, { menu = false; onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE, row)) })
                        DropdownMenuItem({ Text(delete) }, { menu = false; confirmDelete = true })
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.m_delete_title)) }, text = { Text(stringResource(R.string.m_delete_detail)) },
        confirmButton = { MoneyTextButton({ confirmDelete = false; onEvent(MoneyEvent.Delete(row.movement.id)) }) { Text(delete) } },
        dismissButton = { MoneyTextButton({ confirmDelete = false }) { Text(stringResource(R.string.m_cancel)) } })
}
