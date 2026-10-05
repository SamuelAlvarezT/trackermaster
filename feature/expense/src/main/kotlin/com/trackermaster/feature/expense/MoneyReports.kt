package com.trackermaster.feature.expense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun MoneyReportsScreen(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item("categories") {
            if (state.reports.categories.isEmpty()) MoneyEmpty(stringResource(R.string.m_no_report), stringResource(R.string.m_no_report_detail),
                stringResource(R.string.m_add_expense), Icons.Default.PieChart, { onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE)) })
            else MoneyCategoryChart(state.reports, state.preferences.hideAmounts)
        }
        item("monthly") { MoneyMonthlyChart(state.reports, state.preferences.hideAmounts) }
    }
}

@Composable
internal fun MoneyCategoryChart(report: MoneyReportsUiState, hidden: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.outline, scheme.onSurfaceVariant, scheme.error)
    val labels = report.categories.map { categoryTitle(it.category) }
    val accessible = if (hidden) stringResource(R.string.m_chart_hidden) else stringResource(R.string.m_chart_accessible,
        report.categories.mapIndexed { index, row -> "${labels[index]}: ${moneyLabel(row.amount, false)}" }.joinToString("; "))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.m_spending), style = MaterialTheme.typography.titleMedium)
            if (hidden) {
                Icon(Icons.Default.VisibilityOff, stringResource(R.string.m_chart_hidden), Modifier.align(Alignment.CenterHorizontally))
                Text(stringResource(R.string.m_hidden))
            } else if (report.categories.isNotEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(174.dp).semantics { contentDescription = accessible }) {
                        val stroke = 22.dp.toPx()
                        val diameter = size.minDimension - stroke
                        val total = report.categories.sumOf { it.amount.minorUnits.toDouble().coerceAtLeast(0.0) }
                        var start = -90f
                        if (total > 0) report.categories.forEachIndexed { index, row ->
                            val sweep = (row.amount.minorUnits.toDouble().coerceAtLeast(0.0) / total * 360).toFloat()
                            if (sweep > 0) drawArc(colors[index % colors.size], start, sweep, false,
                                Offset(stroke / 2, stroke / 2), Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Butt))
                            start += sweep
                        } else drawCircle(scheme.outline, radius = diameter / 2, style = Stroke(stroke))
                    }
                    Icon(Icons.Default.PieChart, null, Modifier.size(30.dp), tint = scheme.onSurfaceVariant)
                }
                report.categories.forEachIndexed { index, row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.size(12.dp), shape = MaterialTheme.shapes.small, color = colors[index % colors.size]) {}
                        Text(labels[index], Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        MoneyAmount(row.amount, false, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else Text(stringResource(R.string.m_no_report), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MoneyMonthlyChart(report: MoneyReportsUiState, hidden: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy", locale)
    val caption = if (hidden) stringResource(R.string.m_chart_hidden) else report.months.joinToString("; ") {
        "${it.day.format(monthFormat)}: ${it.net.format(locale)}"
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.m_flow_trend), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.m_chart_detail), style = MaterialTheme.typography.bodySmall)
            if (!hidden) Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = caption }) {
                val maxValue = report.months.maxOfOrNull { maxOf(abs(it.income.minorUnits.toDouble()), abs(it.expense.minorUnits.toDouble()), abs(it.net.minorUnits.toDouble())) }
                    ?.coerceAtLeast(1.0) ?: 1.0
                val center = size.height / 2
                val step = size.width / report.months.size.coerceAtLeast(1)
                drawLine(scheme.outline, Offset(0f, center), Offset(size.width, center), strokeWidth = 1.dp.toPx())
                var previous: Offset? = null
                report.months.forEachIndexed { index, month ->
                    val x = (index + .5f) * step
                    val incomeHeight = (month.income.minorUnits.toDouble() / maxValue * center).toFloat()
                    val expenseHeight = (month.expense.minorUnits.toDouble() / maxValue * center).toFloat()
                    drawLine(scheme.primary, Offset(x - step * .13f, center), Offset(x - step * .13f, center - incomeHeight), strokeWidth = step * .18f)
                    drawLine(scheme.error, Offset(x + step * .13f, center), Offset(x + step * .13f, center - expenseHeight), strokeWidth = step * .18f)
                    val point = Offset(x, center - (month.net.minorUnits.toDouble() / maxValue * center).toFloat())
                    previous?.let { drawLine(scheme.tertiary, it, point, strokeWidth = 2.dp.toPx()) }
                    drawCircle(scheme.tertiary, 3.dp.toPx(), point)
                    previous = point
                }
            } else Text(stringResource(R.string.m_hidden))
            report.months.forEach { month ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    Text(month.day.format(monthFormat), style = MaterialTheme.typography.titleSmall)
                    Text("${stringResource(R.string.m_income)}: ${moneyLabel(month.income, hidden)}", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.m_expense)}: ${moneyLabel(month.expense, hidden)}", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.m_net)}: ${moneyLabel(month.net, hidden)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
