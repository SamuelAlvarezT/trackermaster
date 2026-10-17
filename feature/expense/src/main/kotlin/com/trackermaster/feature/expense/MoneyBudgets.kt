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
import com.trackermaster.core.domain.money.BudgetStatus
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@Composable
fun MoneyBudgetsScreen(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
        item("actions") { Column {
            FilledTonalButton({onEvent(MoneyEvent.Planning())},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.p_manage_budgets))}
            MoneyTextButton({onEvent(MoneyEvent.Planning("plans"))},Modifier.fillMaxWidth()) {Text(stringResource(R.string.p_plans))}
        } }
        if (state.budgets.items.isEmpty()) item("empty") { MoneyBudgetBody(state, onEvent) }
        else items(state.budgets.items, key = { it.id }) { MoneyBudgetCard(it, state.preferences.hideAmounts) {onEvent(MoneyEvent.Planning(budgetId=it.id))} }
    }
}

@Composable
internal fun MoneyBudgetBody(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    if (state.budgets.items.isEmpty()) MoneyEmpty(stringResource(R.string.m_budget_empty), stringResource(R.string.m_budget_empty_detail),
        stringResource(R.string.p_create_budget), Icons.Default.PieChart, {onEvent(MoneyEvent.Planning())})
    else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.budgets.items.take(3).forEach { item -> MoneyBudgetCard(item, state.preferences.hideAmounts) {onEvent(MoneyEvent.Planning(budgetId=item.id))} }
    }
}

@Composable
internal fun MoneyBudgetCard(item: MoneyBudgetItem, hidden: Boolean, onEdit: () -> Unit = {}) {
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(item.allCategories) stringResource(R.string.p_all_categories) else categoryTitle(item.category), style = MaterialTheme.typography.titleMedium)
                if (item.limit != null) MoneyAmount(item.limit, hidden)
                else {
                    val label = if (hidden) stringResource(R.string.m_hidden)
                        else NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).format(item.legacyAmount)
                    Text(stringResource(R.string.m_budget_original, label))
                    Text(stringResource(R.string.m_budget_unassigned), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.m_budget_config), style = MaterialTheme.typography.bodySmall)
                }
                item.progress?.let { progress ->
                    val status=stringResource(when(progress.status){BudgetStatus.OK->R.string.p_ok;BudgetStatus.NEAR->R.string.p_near;BudgetStatus.LIMIT->R.string.p_limit;BudgetStatus.EXCEEDED->R.string.p_exceeded})
                    val color=when(progress.status){BudgetStatus.OK->MaterialTheme.colorScheme.primary;BudgetStatus.NEAR,BudgetStatus.LIMIT->MaterialTheme.colorScheme.tertiary;BudgetStatus.EXCEEDED->MaterialTheme.colorScheme.error}
                    Text(status,color=color,style=MaterialTheme.typography.labelLarge)
                    if(!hidden) {
                        val description=stringResource(R.string.p_progress,progress.percent,status)
                        LinearProgressIndicator(progress={progress.percent.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth().semantics{stateDescription=description},color=color)
                        Text(stringResource(R.string.p_spent,moneyLabel(progress.spent,false)))
                        Text(stringResource(R.string.p_remaining,moneyLabel(progress.remaining,false)))
                    }
                    Text(stringResource(R.string.p_dates,dayLabel(progress.from),dayLabel(progress.through)),style=MaterialTheme.typography.bodySmall)
                }
                MoneyTextButton(onEdit) {Text(stringResource(if(item.limit==null)R.string.p_configure_budget else R.string.m_edit))}
            }
        }
}
