package com.trackermaster.feature.expense

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun categoryTitle(category: MoneyCategory?): String = when (category?.builtinKey) {
    "food" -> stringResource(R.string.m_food)
    "transport" -> stringResource(R.string.m_transport)
    "shopping" -> stringResource(R.string.m_shopping)
    "bills" -> stringResource(R.string.m_bills)
    "salary" -> stringResource(R.string.m_salary)
    "other" -> stringResource(R.string.m_other)
    else -> category?.name ?: stringResource(R.string.m_uncategorized)
}
internal fun categoryIcon(category: MoneyCategory?): ImageVector = when (category?.builtinKey) {
    "food" -> Icons.Default.Restaurant
    "transport" -> Icons.Default.DirectionsBus
    "shopping" -> Icons.Default.ShoppingBag
    "bills" -> Icons.AutoMirrored.Filled.ReceiptLong
    "salary" -> Icons.Default.Work
    else -> Icons.Default.Category
}
@Composable
internal fun spaceTitle(space: MoneySpace?): String = when (space?.kind) {
    MoneySpaceKind.UNASSIGNED -> stringResource(R.string.m_unassigned)
    MoneySpaceKind.PERSONAL -> stringResource(R.string.m_personal)
    MoneySpaceKind.BUSINESS -> stringResource(R.string.m_business)
    MoneySpaceKind.SAVINGS -> stringResource(R.string.m_savings)
    MoneySpaceKind.TRAVEL -> stringResource(R.string.m_travel)
    MoneySpaceKind.CUSTOM -> space.name
    null -> stringResource(R.string.m_all_spaces)
}
@Composable
internal fun typeTitle(type: AccountType): String = stringResource(when (type) {
    AccountType.CHECKING -> R.string.m_checking
    AccountType.SAVINGS -> R.string.m_savings_type
    AccountType.CARD -> R.string.m_credit
    AccountType.CASH -> R.string.m_cash
})
@Composable
internal fun periodTitle(period: MoneyPeriodKind): String = stringResource(when (period) {
    MoneyPeriodKind.WEEK -> R.string.m_week
    MoneyPeriodKind.MONTH -> R.string.m_month
    MoneyPeriodKind.YEAR -> R.string.m_year
    MoneyPeriodKind.RANGE -> R.string.m_range
})
@Composable
internal fun entryTitle(kind: MoneyEntryKind): String = stringResource(when (kind) {
    MoneyEntryKind.EXPENSE -> R.string.m_expense
    MoneyEntryKind.INCOME -> R.string.m_income
    MoneyEntryKind.TRANSFER -> R.string.m_transfer
})
@Composable
internal fun destinationTitle(destination: MoneyDestination): String = stringResource(when (destination) {
    MoneyDestination.SUMMARY -> R.string.m_summary
    MoneyDestination.MOVEMENTS -> R.string.m_movements
    MoneyDestination.BUDGETS -> R.string.m_budgets
    MoneyDestination.REPORTS -> R.string.m_reports
    MoneyDestination.ACCOUNTS -> R.string.m_accounts
})
internal fun errorResource(error: MoneyUiError): Int = when (error) {
    MoneyUiError.LOAD -> R.string.m_load_error
    MoneyUiError.AMOUNT -> R.string.m_amount_error
    MoneyUiError.ACCOUNT -> R.string.m_account_error
    MoneyUiError.CATEGORY -> R.string.m_category_error
    MoneyUiError.FUNDS -> R.string.m_funds_error
    MoneyUiError.CONFLICT -> R.string.m_conflict_error
    MoneyUiError.CURRENCY -> R.string.m_currency_error
    MoneyUiError.NAME -> R.string.m_name_error
    else -> R.string.m_save_error
}
@Composable
internal fun moneyLabel(amount: Money, hidden: Boolean): String = if (hidden) stringResource(R.string.m_hidden)
    else amount.format(LocalConfiguration.current.locales[0])

@Composable
internal fun MoneyAmount(amount: Money, hidden: Boolean, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium, animate: Boolean = false) {
    val label = moneyLabel(amount, hidden)
    if (animate && !hidden) Crossfade(label, animationSpec = tween(160), label = "moneyAmount",
        modifier = modifier.clearAndSetSemantics { contentDescription = label }) { value ->
        Text(value, style = style, fontWeight = FontWeight.SemiBold)
    } else Text(label, modifier, style = style, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun MoneyHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (action != null) MoneyTextButton(onAction, Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}
@Composable
internal fun MoneyEmpty(title: String, detail: String, action: String, icon: ImageVector,
    onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(22.dp).size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onAction, Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}
@Composable
internal fun MoneyError(error: MoneyUiError, onRetry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(errorResource(error)), color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) MoneyTextButton(onRetry) { Text(stringResource(R.string.m_retry)) }
        }
    }
}
@Composable
internal fun MoneyChoice(label: String, value: String, options: List<Pair<String, String>>,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        MoneyOutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { if (compact) contentDescription = label }, enabled = enabled) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                if (!compact) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) },
                onClick = { expanded = false; onSelect(id) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
    }
}
@Composable
internal fun dayLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> stringResource(R.string.m_today)
    LocalDate.now().minusDays(1) -> stringResource(R.string.m_yesterday)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
}

// Reuse shared theme roles with readable text even when the selected dark accent is dim.
@Composable
internal fun MoneyTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), content = content)
}

@Composable
internal fun MoneyOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), content = content)
}
