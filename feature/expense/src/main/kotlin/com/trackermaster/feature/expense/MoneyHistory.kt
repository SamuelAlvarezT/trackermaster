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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(history.filters.query, { onEvent(MoneyEvent.Filters(history.filters.copy(query = it))) },
                Modifier.weight(1f).testTag("money_search"), singleLine = true,
                label = { Text(stringResource(R.string.m_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) })
            IconButton({ onEvent(MoneyEvent.OpenPanel("filters")) }, Modifier.testTag("money_filters")) {
                Icon(Icons.Default.FilterList, stringResource(R.string.m_filters)) }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("money_history"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (history.groups.isEmpty()) item("empty") {
                val hasFilters = history.filters != MoneyFilters()
                MoneyEmpty(stringResource(if (hasFilters) R.string.m_no_results else R.string.m_no_movements),
                    stringResource(if (hasFilters) R.string.m_no_results_detail else R.string.m_no_movements_detail),
                    stringResource(if (hasFilters) R.string.m_clear_filters else if (history.hasAccounts) R.string.m_add_expense else R.string.m_create_account),
                    Icons.AutoMirrored.Filled.ReceiptLong, {
                        if (hasFilters) onEvent(MoneyEvent.Filters(MoneyFilters()))
                        else if (history.hasAccounts) onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE)) else onEvent(MoneyEvent.OpenAccount())
                    })
            }
            history.groups.forEach { group ->
                item("day:${group.day}") {
                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(dayLabel(group.day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.m_day_net, moneyLabel(group.net, state.preferences.hideAmounts)),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(group.rows, key = { it.key }) { MoneySwipeRow(it, state.preferences.hideAmounts, onEvent) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneySwipeRow(row: MoneyHistoryRow, hidden: Boolean, onEvent: (MoneyEvent) -> Unit) {
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE, row))
            SwipeToDismissBoxValue.EndToStart -> onEvent(MoneyEvent.Delete(row.movement.id))
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
}

@Composable
internal fun MoneyTransactionCard(row: MoneyHistoryRow, hidden: Boolean, onEvent: (MoneyEvent) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val edit = stringResource(R.string.m_edit)
    val delete = stringResource(R.string.m_delete)
    val title = if (row.incoming == null) categoryTitle(row.category) else stringResource(R.string.m_transfer)
    Card(Modifier.fillMaxWidth().testTag("entry:${row.key}").semantics {
        customActions = listOf(CustomAccessibilityAction(edit) { onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE, row)); true },
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
                    Text(if (row.incoming != null) stringResource(R.string.m_transfer) else
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
                    DropdownMenuItem({ Text(edit) }, { menu = false; onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE, row)) })
                    DropdownMenuItem({ Text(delete) }, { menu = false; confirmDelete = true })
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.m_delete_title)) }, text = { Text(stringResource(R.string.m_delete_detail)) },
        confirmButton = { MoneyTextButton({ confirmDelete = false; onEvent(MoneyEvent.Delete(row.movement.id)) }) { Text(delete) } },
        dismissButton = { MoneyTextButton({ confirmDelete = false }) { Text(stringResource(R.string.m_cancel)) } })
}
