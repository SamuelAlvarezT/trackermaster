package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trackermaster.core.domain.money.Money
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.awaitCancellation

@Composable
internal fun MoneyEditorDialog(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    Dialog({ onEvent(MoneyEvent.CloseEditor) }, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = !state.controls.saving, dismissOnClickOutside = false)) {
        MoneyEditorContent(state, onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoneyEditorContent(state: MoneyHomeUiState, onEvent: (MoneyEvent) -> Unit) {
    val editor = state.controls.editor ?: return
    val locale = LocalConfiguration.current.locales[0]
    val decimal = remember(locale) { DecimalFormatSymbols(locale).decimalSeparator }
    var keypadTarget by remember { mutableStateOf("amount") }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(keypadTarget) { if (keypadTarget != "note") keyboard?.hide() }
    val update: (MoneyEditorUiState) -> Unit = { onEvent(MoneyEvent.Editor(it)) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (editor.id == 0L) R.string.m_editor_title else R.string.m_editor_edit)) },
        navigationIcon = { IconButton({ onEvent(MoneyEvent.CloseEditor) }, enabled = !editor.saving) {
            Icon(Icons.Default.Close, stringResource(R.string.m_close)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding()) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                item("kind") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyEntryKind.entries.forEach { kind -> FilterChip(editor.kind == kind,
                            { update(editor.copy(kind = kind)) }, enabled = !editor.saving && (editor.id == 0L ||
                                (editor.kind == MoneyEntryKind.TRANSFER) == (kind == MoneyEntryKind.TRANSFER)),
                            label = { Text(entryTitle(kind)) }) }
                    }
                }
                item("amount") {
                    MoneyKeypadInput {
                    OutlinedTextField(editor.amount.replace('.', decimal), { update(editor.copy(amount = it.replace(decimal, '.'))) },
                        Modifier.fillMaxWidth().testTag("money_amount_input").onFocusChanged {
                            if (it.isFocused) { keypadTarget = "amount"; keyboard?.hide() }
                        }, singleLine = true,
                        enabled = !editor.saving, label = { Text(stringResource(R.string.m_amount)) },
                        prefix = { Text(editor.currency) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineMedium,
                        supportingText = { Text(pluralStringResource(R.plurals.m_amount_precision,
                            Money.scale(editor.currency), Money.scale(editor.currency))) })
                    }
                }
                item("source") { MoneyFundingSelector(state, editor.accountId, editor.pocketId,
                    if (editor.kind == MoneyEntryKind.TRANSFER) stringResource(R.string.m_source) else stringResource(R.string.m_account),
                    { update(editor.copy(accountId = it)) }, { update(editor.copy(pocketId = it)) }) }
                if (editor.kind == MoneyEntryKind.TRANSFER) {
                    item("target") { MoneyFundingSelector(state, editor.targetAccountId, editor.targetPocketId, stringResource(R.string.m_target),
                        { update(editor.copy(targetAccountId = it)) }, { update(editor.copy(targetPocketId = it)) }) }
                    if (editor.currency != editor.targetCurrency) item("received") {
                        MoneyKeypadInput {
                        OutlinedTextField(editor.receivedAmount.replace('.', decimal), { update(editor.copy(receivedAmount = it.replace(decimal, '.'))) },
                            Modifier.fillMaxWidth().testTag("money_received_input").onFocusChanged {
                                if (it.isFocused) { keypadTarget = "received"; keyboard?.hide() }
                            }, singleLine = true, enabled = !editor.saving,
                            label = { Text(stringResource(R.string.m_received)) }, prefix = { Text(editor.targetCurrency) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                    item("transfer_detail") { Text(stringResource(R.string.m_transfer_detail), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else item("categories") {
                    Text(stringResource(R.string.m_category), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                        state.categories.filter { it.isIncome == (editor.kind == MoneyEntryKind.INCOME) }.forEach { category ->
                            FilterChip(editor.categoryId == category.id, { update(editor.copy(categoryId = category.id)) },
                                modifier = Modifier.heightIn(min = 48.dp), enabled = !editor.saving,
                                label = { Text(categoryTitle(category)) }, leadingIcon = { Icon(categoryIcon(category), null, Modifier.size(18.dp)) })
                        }
                        AssistChip({ onEvent(MoneyEvent.OpenPanel("category")) }, enabled = !editor.saving,
                            label = { Text(stringResource(R.string.m_new_category)) }, leadingIcon = { Icon(Icons.Default.Add, null) })
                    }
                }
                item("details") {
                    MoneyTextButton({ update(editor.copy(expanded = !editor.expanded)) }, enabled = !editor.saving) {
                        Text(stringResource(if (editor.expanded) R.string.m_less else R.string.m_more))
                    }
                    if (editor.expanded) {
                        val date = editor.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
                        MoneyOutlinedButton({ onEvent(MoneyEvent.OpenPanel("date")) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !editor.saving) {
                            Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("${stringResource(R.string.m_date)} · $date")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(editor.note, { update(editor.copy(note = it)) }, Modifier.fillMaxWidth().onFocusChanged {
                            if (it.isFocused) keypadTarget = "note"
                        },
                            label = { Text(stringResource(R.string.m_note)) }, minLines = 2, enabled = !editor.saving)
                        if (editor.attachmentPath != null) Text(stringResource(R.string.m_attachment_kept), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (editor.error != null) item("error") { MoneyError(editor.error) }
            }
            if (keypadTarget != "note") {
                val received = keypadTarget == "received" && editor.kind == MoneyEntryKind.TRANSFER && editor.currency != editor.targetCurrency
                MoneyNumberPad(if (received) editor.receivedAmount else editor.amount, decimal, !editor.saving) {
                    update(if (received) editor.copy(receivedAmount = it) else editor.copy(amount = it))
                }
            }
            FilledTonalButton({ onEvent(MoneyEvent.SaveEntry) }, Modifier.fillMaxWidth().padding(vertical = 10.dp).heightIn(min = 52.dp).testTag("money_save_entry"),
                enabled = editor.canSave && !editor.saving) {
                if (editor.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp)); Text(stringResource(if (editor.saving) R.string.m_saving else R.string.m_save))
            }
        }
    }
}

/** Numeric fields retain selection and hardware input without opening a second soft keyboard. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MoneyKeypadInput(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }, content = content)
}

@Composable
private fun MoneyFundingSelector(state: MoneyHomeUiState, accountId: Long?, pocketId: Long?, label: String,
    onAccount: (Long) -> Unit, onPocket: (Long?) -> Unit) {
    val accounts = state.accounts.accounts
    val pockets = state.accounts.pockets.filter { it.pocket.accountId == accountId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MoneyChoice(label, accounts.find { it.account.id == accountId }?.account?.name ?: stringResource(R.string.m_account),
            accounts.filter { !it.account.archived }.map { it.account.id.toString() to "${it.account.name} · ${it.account.initialBalance.currencyCode}" },
            { onAccount(it.toLong()) }, enabled = !state.controls.saving)
        if (pockets.isNotEmpty()) MoneyChoice(stringResource(R.string.m_pockets),
            pockets.find { it.pocket.id == pocketId }?.pocket?.name ?: stringResource(R.string.m_unallocated),
            listOf("none" to stringResource(R.string.m_unallocated)) + pockets.map { it.pocket.id.toString() to
                "${it.pocket.name} · ${spaceTitle(state.spaces.find { space -> space.id == it.pocket.spaceId })}" },
            { onPocket(it.toLongOrNull()) }, enabled = !state.controls.saving)
    }
}

@Composable
private fun MoneyNumberPad(value: String, decimal: Char, enabled: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("money_keypad")) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "delete")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    FilledTonalButton({ when (key) {
                        "delete" -> onChange(value.dropLast(1))
                        "." -> if (!value.contains('.')) onChange(value.ifEmpty { "0" } + ".")
                        else -> if (value.length < 23) onChange(if (value == "0") key else value + key)
                    } }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("money_key:$key"), enabled = enabled,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        if (key == "delete") Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.m_backspace))
                        else Text(if (key == ".") decimal.toString() else key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
