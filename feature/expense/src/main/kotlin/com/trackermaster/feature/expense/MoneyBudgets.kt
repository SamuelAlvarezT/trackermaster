package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

@Composable
fun MoneyBudgetsScreen(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
        if (state.budgets.items.isEmpty()) item("empty") { MoneyBudgetBody(state, onEvent) }
        else items(state.budgets.items, key = { it.id }) { MoneyBudgetCard(it, state.preferences.hideAmounts) }
    }
}

@Composable
internal fun MoneyBudgetBody(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    if (state.budgets.items.isEmpty()) MoneyEmpty(stringResource(R.string.m_budget_empty), stringResource(R.string.m_budget_empty_detail),
        stringResource(R.string.m_view_expenses), Icons.Default.PieChart, {
            onEvent(MoneyEvent.Filters(MoneyFilters(kind = MoneyEntryKind.EXPENSE)))
            onEvent(MoneyEvent.Navigate(MoneyDestination.MOVEMENTS))
        })
    else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.budgets.items.take(3).forEach { MoneyBudgetCard(it, state.preferences.hideAmounts) }
    }
}

@Composable
private fun MoneyBudgetCard(item: MoneyBudgetItem, hidden: Boolean) {
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(categoryTitle(item.category), style = MaterialTheme.typography.titleMedium)
                if (item.limit != null) MoneyAmount(item.limit, hidden)
                else {
                    val label = if (hidden) stringResource(R.string.m_hidden)
                        else NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).format(item.legacyAmount)
                    Text(stringResource(R.string.m_budget_original, label))
                    Text(stringResource(R.string.m_budget_unassigned), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.m_budget_config), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
}
