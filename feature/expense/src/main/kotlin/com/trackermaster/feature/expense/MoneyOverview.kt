package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MoneyOverviewScreen(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("money_summary"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("summary") { MoneyOverviewBody(state, onEvent) }
        item("accounts") { MoneyAccountsShortcut(state, onEvent) }
        if (state.overview.hasAccounts) {
            item("recent_heading") { MoneyHeading(stringResource(R.string.m_recent), stringResource(R.string.m_see_all)) {
                onEvent(MoneyEvent.Navigate(MoneyDestination.MOVEMENTS)) } }
            if (state.overview.recent.isEmpty()) item("empty") { MoneyEmpty(stringResource(R.string.m_no_movements),
                stringResource(R.string.m_no_movements_detail), stringResource(R.string.m_add_expense), Icons.AutoMirrored.Filled.ReceiptLong,
                { onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE)) }) }
            items(state.overview.recent, key = { it.key }) { MoneyTransactionCard(it, state.preferences.hideAmounts, onEvent) }
        }
        if (state.preferences.detailed) item("chart") { MoneyCategoryChart(state.reports, state.preferences.hideAmounts) }
    }
}

@Composable
internal fun MoneyOverviewBody(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val hidden = state.preferences.hideAmounts
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MoneyOutlinedButton({onEvent(MoneyEvent.ImportReceipt)},Modifier.fillMaxWidth().testTag("money_import_receipt")) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong,null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.r_import))
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, null)
                    Text(stringResource(R.string.m_balance), style = MaterialTheme.typography.labelLarge)
                }
                MoneyAmount(state.overview.balance, hidden, Modifier.testTag("money_balance"),
                    style = MaterialTheme.typography.headlineLarge, animate = true)
                Text(spaceTitle(state.spaces.find { it.id == state.controls.spaceId }), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!state.overview.hasAccounts) MoneyEmpty(stringResource(R.string.m_no_accounts), stringResource(R.string.m_no_accounts_detail),
            stringResource(R.string.m_create_account), Icons.Default.AccountBalance,
            { onEvent(MoneyEvent.OpenAccount()) })
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyMetric(stringResource(R.string.m_income), state.overview.totals.income, hidden, Icons.Default.SouthWest, Modifier.weight(1f))
                MoneyMetric(stringResource(R.string.m_expense), state.overview.totals.expenses, hidden, Icons.Default.NorthEast, Modifier.weight(1f))
            }
            if (state.preferences.detailed) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.m_net), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    MoneyAmount(state.overview.totals.net, hidden)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.INCOME)) }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.m_add_income))
                }
                MoneyOutlinedButton({ onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.TRANSFER)) }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.m_transfer))
                }
            }
        }
    }
}

@Composable
private fun MoneyMetric(label: String, amount: com.trackermaster.core.domain.money.Money, hidden: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(18.dp)); Text(label, style = MaterialTheme.typography.labelLarge)
            }
            MoneyAmount(amount, hidden, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun MoneyAccountsShortcut(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    Card(onClick = { onEvent(MoneyEvent.Navigate(MoneyDestination.ACCOUNTS)) }, modifier = Modifier.fillMaxWidth().testTag("money_accounts")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.AccountBalanceWallet, null)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.m_accounts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.m_pockets), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
