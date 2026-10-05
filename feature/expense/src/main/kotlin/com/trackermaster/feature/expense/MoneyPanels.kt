package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.model.AccountType
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoneyPanels(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val controls = state.controls
    if (controls.settingsOpen) MoneySettingsPanel(state, onEvent)
    if (controls.filtersOpen) MoneyFiltersPanel(state, onEvent)
    if (controls.accountEditor != null) MoneyAccountPanel(state, onEvent)
    if (controls.pocketEditor != null) MoneyPocketPanel(state, onEvent)
    if (controls.categoryEditor || controls.spaceEditor) {
        val category = controls.categoryEditor
        val close = { onEvent(MoneyEvent.OpenPanel(if (category) "close_category" else "close_space")) }
        AlertDialog(onDismissRequest = close, title = { Text(stringResource(if (category) R.string.m_new_category else R.string.m_new_space)) },
            text = { Column {
                OutlinedTextField(controls.customName, { onEvent(MoneyEvent.CustomName(it)) }, label = { Text(stringResource(R.string.m_name)) },
                    enabled = !controls.saving, singleLine = true)
                controls.customError?.let { MoneyError(it) }
            } }, confirmButton = { MoneyTextButton({ onEvent(if (category) MoneyEvent.SaveCategory else MoneyEvent.SaveSpace) },
                enabled = !controls.saving) { Text(stringResource(R.string.m_save)) } },
            dismissButton = { MoneyTextButton(close, enabled = !controls.saving) { Text(stringResource(R.string.m_cancel)) } })
    }
    if (controls.rangeOpen) {
        val picker = rememberDateRangePickerState(initialSelectedStartDateMillis = utcMillis(state.range.from),
            initialSelectedEndDateMillis = utcMillis(state.range.through))
        val close = { onEvent(MoneyEvent.OpenPanel("close_range")) }
        DatePickerDialog(onDismissRequest = close,
            confirmButton = { MoneyTextButton({
                val start = picker.selectedStartDateMillis?.let(::utcDay)
                val end = picker.selectedEndDateMillis?.let(::utcDay)
                if (start != null && end != null) onEvent(MoneyEvent.Range(start, end))
            }, enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null) { Text(stringResource(R.string.m_apply)) } },
            dismissButton = { MoneyTextButton(close) { Text(stringResource(R.string.m_cancel)) } }) {
            DateRangePicker(picker, Modifier.heightIn(max = 520.dp))
        }
    }
    if (controls.editorDateOpen && controls.editor != null) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = utcMillis(controls.editor.date))
        val close = { onEvent(MoneyEvent.OpenPanel("close_date")) }
        DatePickerDialog(onDismissRequest = close,
            confirmButton = { MoneyTextButton({ picker.selectedDateMillis?.let { onEvent(MoneyEvent.EntryDate(utcDay(it))) } },
                enabled = picker.selectedDateMillis != null) { Text(stringResource(R.string.m_apply)) } },
            dismissButton = { MoneyTextButton(close) { Text(stringResource(R.string.m_cancel)) } }) { DatePicker(picker) }
    }
}

@Composable
fun MoneySettingsPanel(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val close = { onEvent(MoneyEvent.OpenPanel("close_settings")) }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.m_settings)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MoneyChoice(stringResource(R.string.m_density), stringResource(if (state.preferences.detailed) R.string.m_detailed else R.string.m_simple),
                listOf("false" to stringResource(R.string.m_simple), "true" to stringResource(R.string.m_detailed)),
                { onEvent(MoneyEvent.Appearance(it.toBoolean(), state.preferences.sections)) }, enabled = !state.controls.saving)
            MoneyChoice(stringResource(R.string.m_navigation), stringResource(if (state.preferences.sections) R.string.m_sections else R.string.m_tabs),
                listOf("false" to stringResource(R.string.m_tabs), "true" to stringResource(R.string.m_sections)),
                { onEvent(MoneyEvent.Appearance(state.preferences.detailed, it.toBoolean())) }, enabled = !state.controls.saving)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.m_hide), Modifier.weight(1f))
                Switch(state.preferences.hideAmounts, { onEvent(MoneyEvent.Privacy) }, enabled = !state.controls.saving)
            }
            Text(stringResource(R.string.m_privacy_detail), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { MoneyTextButton(close) { Text(stringResource(R.string.m_apply)) } })
}

@Composable
private fun MoneyFiltersPanel(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val filters = state.history.filters
    val close = { onEvent(MoneyEvent.OpenPanel("close_filters")) }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.m_filters)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyChoice(stringResource(R.string.m_account), state.accounts.accounts.find { it.account.id == filters.accountId }?.account?.name ?: stringResource(R.string.m_all_accounts),
                listOf("all" to stringResource(R.string.m_all_accounts)) + state.accounts.accounts.map { it.account.id.toString() to it.account.name },
                { onEvent(MoneyEvent.Filters(filters.copy(accountId = it.toLongOrNull()))) })
            MoneyChoice(stringResource(R.string.m_category), if (filters.categoryId == null) stringResource(R.string.m_all_categories)
                else categoryTitle(state.categories.find { it.id == filters.categoryId }),
                listOf("all" to stringResource(R.string.m_all_categories)) + state.categories.map { it.id.toString() to categoryTitle(it) },
                { onEvent(MoneyEvent.Filters(filters.copy(categoryId = it.toLongOrNull()))) })
            MoneyChoice(stringResource(R.string.m_movements), filters.kind?.let { entryTitle(it) } ?: stringResource(R.string.m_all_types),
                listOf("all" to stringResource(R.string.m_all_types)) + MoneyEntryKind.entries.map { it.name to entryTitle(it) },
                { onEvent(MoneyEvent.Filters(filters.copy(kind = MoneyEntryKind.entries.find { kind -> kind.name == it }))) })
            MoneyOutlinedButton({ onEvent(MoneyEvent.Period(MoneyPeriodKind.RANGE)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.m_range)) }
            MoneyTextButton({ onEvent(MoneyEvent.Filters(MoneyFilters())) }) { Text(stringResource(R.string.m_clear_filters)) }
        } }, confirmButton = { MoneyTextButton(close) { Text(stringResource(R.string.m_apply)) } })
}

@Composable
fun MoneyAccountPanel(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val form = state.controls.accountEditor ?: return
    val close = { onEvent(MoneyEvent.OpenPanel("close_account")) }
    val decimal = DecimalFormatSymbols(LocalConfiguration.current.locales[0]).decimalSeparator
    AlertDialog(onDismissRequest = close,
        title = { Text(stringResource(if (form.id == 0L) R.string.m_create_account else R.string.m_account_edit)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(form.name, { onEvent(MoneyEvent.AccountEditor(form.copy(name = it))) },
                Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.m_name)) }, singleLine = true, enabled = !state.controls.saving)
            MoneyChoice(stringResource(R.string.m_account_type), typeTitle(form.type), AccountType.entries.map { it.name to typeTitle(it) },
                { onEvent(MoneyEvent.AccountEditor(form.copy(type = AccountType.valueOf(it)))) }, enabled = !state.controls.saving)
            MoneyChoice(stringResource(R.string.m_currency), form.currency,
                (state.currencies + form.currency).distinct().map { it to it },
                { onEvent(MoneyEvent.AccountEditor(form.copy(currency = it))) }, enabled = !form.hasMovements && !state.controls.saving)
            OutlinedTextField(form.initialAmount.replace('.', decimal), { onEvent(MoneyEvent.AccountEditor(form.copy(initialAmount = it.replace(decimal, '.')))) },
                Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.m_initial)) }, prefix = { Text(form.currency) },
                enabled = !form.hasMovements && !state.controls.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            if (form.hasMovements) Text(stringResource(R.string.m_initial_locked), style = MaterialTheme.typography.bodySmall)
            MoneyChoice(stringResource(R.string.m_space), spaceTitle(state.spaces.find { it.id == form.spaceId }),
                state.spaces.map { it.id.toString() to spaceTitle(it) },
                { onEvent(MoneyEvent.AccountEditor(form.copy(spaceId = it.toLong()))) }, enabled = !state.controls.saving)
            MoneyTextButton({ onEvent(MoneyEvent.OpenPanel("space")) }, enabled = !state.controls.saving) { Text(stringResource(R.string.m_new_space)) }
            form.error?.let { MoneyError(it) }
        } }, confirmButton = { MoneyTextButton({ onEvent(MoneyEvent.SaveAccount) }, enabled = !state.controls.saving) {
            Text(stringResource(if (state.controls.saving) R.string.m_saving else R.string.m_save)) } },
        dismissButton = { MoneyTextButton(close, enabled = !state.controls.saving) { Text(stringResource(R.string.m_cancel)) } })
}

@Composable
fun MoneyPocketPanel(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val form = state.controls.pocketEditor ?: return
    val close = { onEvent(MoneyEvent.OpenPanel("close_pocket")) }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.m_create_pocket)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.m_pocket_detail), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(form.name, { onEvent(MoneyEvent.PocketEditor(form.copy(name = it))) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.m_name)) }, enabled = !state.controls.saving, singleLine = true)
            MoneyChoice(stringResource(R.string.m_space), spaceTitle(state.spaces.find { it.id == form.spaceId }),
                state.spaces.map { it.id.toString() to spaceTitle(it) },
                { onEvent(MoneyEvent.PocketEditor(form.copy(spaceId = it.toLong()))) }, enabled = !state.controls.saving)
            MoneyTextButton({ onEvent(MoneyEvent.OpenPanel("space")) }) { Text(stringResource(R.string.m_new_space)) }
            form.error?.let { MoneyError(it) }
        } }, confirmButton = { MoneyTextButton({ onEvent(MoneyEvent.SavePocket) }, enabled = !state.controls.saving) {
            Text(stringResource(if (state.controls.saving) R.string.m_saving else R.string.m_save)) } },
        dismissButton = { MoneyTextButton(close, enabled = !state.controls.saving) { Text(stringResource(R.string.m_cancel)) } })
}

private fun utcMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun utcDay(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
