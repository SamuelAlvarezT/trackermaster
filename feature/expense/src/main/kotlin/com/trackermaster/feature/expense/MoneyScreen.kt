package com.trackermaster.feature.expense

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyContent(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val controls = state.controls
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { onEvent(MoneyEvent.LocaleChanged(locale.toLanguageTag())) }
    val snackbars = remember { SnackbarHostState() }
    val notice = controls.notice
    val noticeText = notice?.let { stringResource(when (it.kind) {
        MoneyNoticeKind.SAVED -> R.string.m_saved
        MoneyNoticeKind.DELETED -> R.string.m_deleted
        MoneyNoticeKind.RESTORED -> R.string.m_restored
        MoneyNoticeKind.PREFERENCES_FAILED -> R.string.m_preferences_failed
    }) }
    val undoText = stringResource(R.string.m_undo)
    LaunchedEffect(notice?.id) {
        if (notice != null && noticeText != null) {
            val result = snackbars.showSnackbar(noticeText,
                actionLabel = if (notice.kind == MoneyNoticeKind.DELETED) undoText else null,
                withDismissAction = true, duration = if (notice.kind == MoneyNoticeKind.DELETED) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onEvent(MoneyEvent.Undo)
            else onEvent(MoneyEvent.DismissNotice(notice.id))
        }
    }
    val canGoBack = controls.destination == MoneyDestination.ACCOUNTS ||
        (state.preferences.sections && controls.destination != MoneyDestination.SUMMARY)
    BackHandler(canGoBack && controls.editor == null) {
        onEvent(MoneyEvent.Navigate(MoneyDestination.SUMMARY))
    }
    Scaffold(
        contentWindowInsets=WindowInsets(0,0,0,0),
        topBar = { TopAppBar(title = { Text(if (canGoBack) destinationTitle(controls.destination) else stringResource(R.string.m_title)) },
            navigationIcon = { if (canGoBack) IconButton({ onEvent(MoneyEvent.Navigate(MoneyDestination.SUMMARY)) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.m_summary)) } },
            actions = {
                IconButton({ onEvent(MoneyEvent.Privacy) }, enabled = state.load == MoneyLoadState.CONTENT && !controls.saving,
                    modifier = Modifier.testTag("money_privacy")) {
                    Icon(if (state.preferences.hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        stringResource(if (state.preferences.hideAmounts) R.string.m_show else R.string.m_hide))
                }
                IconButton({ onEvent(MoneyEvent.OpenPanel("settings")) }) { Icon(Icons.Default.Tune, stringResource(R.string.m_settings)) }
            }) },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (state.load == MoneyLoadState.CONTENT && controls.destination != MoneyDestination.ACCOUNTS)
                ExtendedFloatingActionButton(onClick = { onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.EXPENSE)) },
                    modifier = Modifier.testTag("money_add_expense"),
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.m_add_expense)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.load == MoneyLoadState.LOADING) MoneySkeleton()
            else if (state.load == MoneyLoadState.ERROR) Box(Modifier.padding(16.dp)) { MoneyError(MoneyUiError.LOAD) { onEvent(MoneyEvent.Retry) } }
            else {
                if (controls.destination != MoneyDestination.ACCOUNTS) {
                    MoneyScope(state, onEvent)
                    if (!state.preferences.sections) {
                        val destinations = listOf(MoneyDestination.SUMMARY, MoneyDestination.MOVEMENTS,
                            MoneyDestination.BUDGETS, MoneyDestination.REPORTS)
                        val selected = destinations.indexOf(controls.destination).coerceAtLeast(0)
                        ScrollableTabRow(selected, edgePadding = 8.dp, indicator = { positions ->
                            if (selected in positions.indices) TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(positions[selected]), color = MaterialTheme.colorScheme.onSurface)
                        }) {
                            destinations.forEach { destination -> Tab(controls.destination == destination,
                                { onEvent(MoneyEvent.Navigate(destination)) }, modifier = Modifier.heightIn(min = 48.dp),
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(destinationTitle(destination)) }) }
                        }
                    }
                }
                if (controls.error != null && controls.editor == null && controls.accountEditor == null && controls.pocketEditor == null)
                    Box(Modifier.padding(16.dp)) { MoneyError(controls.error) }
                if (controls.destination == MoneyDestination.ACCOUNTS) MoneyAccountsScreen(state, onEvent)
                else if (state.preferences.sections && controls.destination == MoneyDestination.SUMMARY) MoneySections(state, onEvent)
                else when (controls.destination) {
                    MoneyDestination.SUMMARY -> MoneyOverviewScreen(state, onEvent)
                    MoneyDestination.MOVEMENTS -> MoneyHistoryScreen(state, onEvent)
                    MoneyDestination.REPORTS -> MoneyReportsScreen(state, onEvent)
                    MoneyDestination.BUDGETS -> MoneyBudgetsScreen(state, onEvent)
                    else -> Unit
                }
            }
        }
    }
    if (state.load == MoneyLoadState.CONTENT) {
        if (controls.editor != null) MoneyEditorDialog(state, onEvent)
        MoneyPanels(state, onEvent)
    } else if (controls.settingsOpen) MoneySettingsPanel(state, onEvent)
}

@Composable
private fun MoneyScope(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val controls = state.controls
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MoneyChoice(stringResource(R.string.m_space), spaceTitle(state.spaces.find { it.id == controls.spaceId }),
                listOf("all" to stringResource(R.string.m_all_spaces)) + state.spaces.map { it.id.toString() to spaceTitle(it) },
                { onEvent(MoneyEvent.Space(it.toLongOrNull())) }, Modifier.weight(1f))
            MoneyChoice(stringResource(R.string.m_currency), controls.currency, state.currencies.map { it to it },
                { onEvent(MoneyEvent.Currency(it)) }, Modifier.width(112.dp), compact = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onEvent(MoneyEvent.ShiftPeriod(-1)) }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.m_previous)) }
            MoneyChoice(stringResource(R.string.m_date), periodTitle(controls.period.kind),
                MoneyPeriodKind.entries.map { it.name to periodTitle(it) },
                { onEvent(MoneyEvent.Period(MoneyPeriodKind.valueOf(it))) }, Modifier.weight(1f))
            IconButton({ onEvent(MoneyEvent.ShiftPeriod(1)) }) { Icon(Icons.Default.ChevronRight, stringResource(R.string.m_next)) }
        }
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0])
        Text("${state.range.from.format(formatter)} – ${state.range.through.format(formatter)}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoneySections(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("money_sections"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("overview") { MoneyOverviewBody(state, onEvent) }
        item("recent_heading") { MoneyHeading(stringResource(R.string.m_movements), stringResource(R.string.m_see_all)) {
            onEvent(MoneyEvent.Navigate(MoneyDestination.MOVEMENTS)) } }
        items(state.overview.recent, key = { "recent:${it.key}" }) { MoneyTransactionCard(it, state.preferences.hideAmounts, onEvent) }
        item("accounts") { MoneyAccountsShortcut(state, onEvent) }
        item("budgets") {
            MoneyHeading(stringResource(R.string.m_budgets), stringResource(R.string.m_see_all)) { onEvent(MoneyEvent.Navigate(MoneyDestination.BUDGETS)) }
            MoneyBudgetBody(state, onEvent)
        }
        item("reports") {
            MoneyHeading(stringResource(R.string.m_reports), stringResource(R.string.m_see_all)) { onEvent(MoneyEvent.Navigate(MoneyDestination.REPORTS)) }
            MoneyCategoryChart(state.reports, state.preferences.hideAmounts)
        }
    }
}

@Composable
private fun MoneySkeleton() {
    val label = stringResource(R.string.m_loading)
    Column(Modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) { index -> Surface(Modifier.fillMaxWidth().height(if (index == 0) 160.dp else 76.dp),
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {} }
    }
}
