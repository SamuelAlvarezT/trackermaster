package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.model.AccountType

@Composable
fun MoneyAccountsScreen(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val accounts = state.accounts
    LazyColumn(Modifier.fillMaxSize().testTag("money_account_list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("create") {
            FilledTonalButton({ onEvent(MoneyEvent.OpenAccount()) }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("money_create_account"),
                enabled = !state.controls.saving) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.m_create_account))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accounts.showArchived, { onEvent(MoneyEvent.ShowArchived(it)) })
                Text(stringResource(R.string.m_show_archived), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (accounts.accounts.isEmpty()) item("empty") { MoneyEmpty(stringResource(R.string.m_no_accounts),
            stringResource(R.string.m_no_accounts_detail), stringResource(R.string.m_create_account), Icons.Default.AccountBalance,
            { onEvent(MoneyEvent.OpenAccount()) }) }
        items(accounts.accounts.filter { !it.account.archived || accounts.showArchived }, key = { it.account.id }) { row ->
            val account = row.account
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(when (account.type) {
                            AccountType.CASH -> Icons.Default.Payments
                            AccountType.CARD -> Icons.Default.CreditCard
                            AccountType.SAVINGS -> Icons.Default.Savings
                            AccountType.CHECKING -> Icons.Default.AccountBalance
                        }, null)
                        Column(Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("${typeTitle(account.type)} · ${spaceTitle(state.spaces.find { it.id == account.spaceId })}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (account.archived) Text(stringResource(R.string.m_archived), style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton({ onEvent(MoneyEvent.OpenAccount(account)) }, enabled = !state.controls.saving) {
                            Icon(Icons.Default.Edit, stringResource(R.string.m_account_edit)) }
                    }
                    MoneyAmount(row.total, state.preferences.hideAmounts, style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.m_available), style = MaterialTheme.typography.labelLarge)
                    MoneyAmount(row.unallocated, state.preferences.hideAmounts)
                    accounts.pockets.filter { it.pocket.accountId == account.id }.forEach { pocket ->
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(pocket.pocket.name, style = MaterialTheme.typography.titleSmall)
                                Text(spaceTitle(state.spaces.find { it.id == pocket.pocket.spaceId }), style = MaterialTheme.typography.bodySmall)
                                MoneyAmount(pocket.balance, state.preferences.hideAmounts)
                            }
                            if (!account.archived) MoneyTextButton({ onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.TRANSFER,
                                source = account.id, targetPocket = pocket.pocket.id)) }, enabled = !state.controls.saving) {
                                Text(stringResource(R.string.m_reserve)) }
                        }
                    }
                    if (!account.archived) MoneyOutlinedButton({ onEvent(MoneyEvent.OpenPocket(account.id)) },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.controls.saving) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.m_create_pocket))
                    }
                    MoneyTextButton({ onEvent(MoneyEvent.Archive(account.id, !account.archived)) }, enabled = !state.controls.saving) {
                        Text(stringResource(if (account.archived) R.string.m_unarchive else R.string.m_archive)) }
                    Text(stringResource(R.string.m_archive_detail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
