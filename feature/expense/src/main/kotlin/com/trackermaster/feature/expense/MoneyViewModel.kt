package com.trackermaster.feature.expense

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.money.MoneyUndoToken
import com.trackermaster.core.domain.money.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MoneyViewModel @Inject constructor(private val gateway: MoneyGateway,
    private val savedState: SavedStateHandle,
    @MoneyComputation private val computation: CoroutineDispatcher,
    private val exporter: MoneyReportExportGateway = UnavailableMoneyReportExport) : ViewModel() {
    private val controls = MutableStateFlow(MoneyControls(editor = restoreDraft(savedState["money_draft"])))
    private val _uiState = MutableStateFlow(MoneyHomeUiState(controls = controls.value))
    val uiState = _uiState.asStateFlow()
    private val effectChannel = Channel<MoneyEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    val localeTag = gateway.localeTag.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
        java.util.Locale.getDefault().toLanguageTag())
    private var workspace: MoneyWorkspaceData? = null
    private var observation: Job? = null
    private var writing = false
    private var undo: MoneyUndoToken? = null
    private var noticeId = 0L
    private val presenter = MoneyPresenter()

    init { observe() }
    /** Consume a launcher request once after real accounts load; preserve an existing draft. */
    fun openFromWidget(token: String,kind: String): Boolean {
        if(kind !in listOf("EXPENSE","INCOME")) return true
        if(savedState.get<String>("money_widget_request")==token) return true
        if(uiState.value.load!=MoneyLoadState.CONTENT || workspace==null) return false
        savedState["money_widget_request"]=token
        if(controls.value.editor==null && controls.value.accountEditor==null && controls.value.pocketEditor==null) {
            val prefs=uiState.value.preferences
            val currency=prefs.widgetCurrency ?: workspace?.ledger?.snapshot?.accounts
                ?.find { it.account.id==prefs.widgetAccountId }?.total?.currencyCode
                ?: workspace?.ledger?.snapshot?.spaces?.find { it.spaceId==prefs.widgetSpaceId }?.balance?.currencyCode
                ?: workspace?.ledger?.snapshot?.currencies?.firstOrNull()?.currencyCode ?: "COP"
            change { it.copy(currency=currency,spaceId=prefs.widgetSpaceId) }
            onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.valueOf(kind),source=prefs.widgetAccountId))
        }
        return true
    }
    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _uiState.update { it.copy(load = MoneyLoadState.LOADING) }
            try {
                gateway.initialize()
                combine(gateway.data, gateway.preferences, controls) { data, prefs, control ->
                    if(control.privacyOverride==prefs.hideAmounts) controls.compareAndSet(control,control.copy(privacyOverride=null))
                    data to presenter.build(data, control)
                        .copy(preferences = prefs.copy(hideAmounts = control.privacyOverride ?: prefs.hideAmounts))
                }.flowOn(computation).collect { (data, state) ->
                    workspace = data
                    _uiState.value = state.copy(preferences = state.preferences.copy(
                        hideAmounts = controls.value.privacyOverride ?: state.preferences.hideAmounts))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _uiState.update { it.copy(load = MoneyLoadState.ERROR,
                    preferences = it.preferences.copy(hideAmounts = true)) }
            }
        }
    }
    fun onEvent(event: MoneyEvent) {
        when (event) {
            is MoneyEvent.Export -> export(event.pdf)
            MoneyEvent.ExportFailed -> change { it.copy(error = MoneyUiError.EXPORT) }
            MoneyEvent.Investments -> effectChannel.trySend(MoneyEffect.Investments(controls.value.currency,controls.value.spaceId))
            is MoneyEvent.Commitments -> effectChannel.trySend(MoneyEffect.Commitments(event.tab,controls.value.currency,controls.value.spaceId))
            is MoneyEvent.Planning -> effectChannel.trySend(MoneyEffect.Planning(event.tab,event.budgetId,controls.value.currency,controls.value.spaceId))
            MoneyEvent.ImportReceipt -> effectChannel.trySend(MoneyEffect.ImportReceipt)
            is MoneyEvent.ViewReceipt -> if (!uiState.value.preferences.hideAmounts) effectChannel.trySend(MoneyEffect.ViewReceipt(event.path))
            MoneyEvent.Retry -> observe()
            is MoneyEvent.Navigate -> change { it.copy(destination = event.destination) }
            is MoneyEvent.LocaleChanged -> change { it.copy(localeTag = event.tag) }
            is MoneyEvent.Space -> change { state ->
                val code = workspace?.ledger?.snapshot?.spaces?.firstOrNull { it.spaceId == event.id }?.balance?.currencyCode
                    ?: if (workspace?.spaces?.find { it.id == event.id }?.kind == MoneySpaceKind.TRAVEL) "USD" else state.currency
                state.copy(spaceId = event.id, currency = code)
            }
            is MoneyEvent.Currency -> if (event.code in uiState.value.currencies) change { it.copy(currency = event.code) }
            is MoneyEvent.Period -> if (event.kind == MoneyPeriodKind.RANGE) change { it.copy(rangeOpen = true) }
                else change { it.copy(period = it.period.copy(kind = event.kind)) }
            is MoneyEvent.ShiftPeriod -> change { it.copy(period = MoneyPresentation.shift(it.period, event.direction)) }
            is MoneyEvent.Range -> if (!event.through.isBefore(event.from)) change {
                it.copy(period = MoneyPeriod(MoneyPeriodKind.RANGE, event.from, event.from, event.through), rangeOpen = false)
            }
            is MoneyEvent.Filters -> change { it.copy(filters = event.filters) }
            is MoneyEvent.ShowArchived -> change { it.copy(showArchived = event.show) }
            is MoneyEvent.Appearance -> write(notify = false) { gateway.appearance(event.detailed, event.sections) }
            MoneyEvent.Privacy -> {
                if (writing) return
                val hidden = !uiState.value.preferences.hideAmounts
                change { it.copy(privacyOverride = hidden) }
                _uiState.update { it.copy(preferences = it.preferences.copy(hideAmounts = hidden)) }
                write(notify = false) { gateway.privacy(hidden) }
            }
            is MoneyEvent.OpenPanel -> panel(event.panel)
            is MoneyEvent.OpenEntry -> openEntry(event)
            is MoneyEvent.Editor -> updateEditor(event.value)
            MoneyEvent.CloseEditor -> if (!writing) { change { it.copy(editor = null, editorDateOpen = false, categoryEditor = false) }; savedState["money_draft"] = null }
            is MoneyEvent.EntryDate -> { change { it.copy(editor = it.editor?.copy(date = event.day), editorDateOpen = false) }; persistDraft() }
            MoneyEvent.SaveEntry -> saveEntry()
            is MoneyEvent.Delete -> write {
                undo = gateway.delete(event.id)
                notice(MoneyNoticeKind.DELETED)
            }
            MoneyEvent.Undo -> undo?.let { token -> write { gateway.restore(token); undo = null; notice(MoneyNoticeKind.RESTORED) } }
            is MoneyEvent.DismissNotice -> change { if (it.notice?.id == event.id) it.copy(notice = null) else it }
            is MoneyEvent.OpenAccount -> {
                val account = event.account
                change { it.copy(accountEditor = if (account == null) MoneyAccountEditorUiState(currency = it.currency,
                    spaceId = it.spaceId ?: if (it.currency == "USD") 5 else 2) else
                    MoneyAccountEditorUiState(account.id, account.name, account.type, account.initialBalance.currencyCode,
                        account.initialBalance.decimal.toPlainString(), account.spaceId,
                        workspace?.ledger?.movements?.any { row -> row.accountId == account.id } == true)) }
            }
            is MoneyEvent.AccountEditor -> change { it.copy(accountEditor = event.value.copy(error = null)) }
            MoneyEvent.SaveAccount -> saveAccount()
            is MoneyEvent.Archive -> write { gateway.archive(event.id, event.archived); notice(MoneyNoticeKind.SAVED) }
            is MoneyEvent.OpenPocket -> change { it.copy(pocketEditor = MoneyPocketEditorUiState(event.accountId, spaceId = it.spaceId ?: 4)) }
            is MoneyEvent.PocketEditor -> change { it.copy(pocketEditor = event.value.copy(error = null)) }
            MoneyEvent.SavePocket -> savePocket()
            is MoneyEvent.CustomName -> change { it.copy(customName = event.value, customError = null) }
            MoneyEvent.SaveCategory -> saveCustom(category = true)
            MoneyEvent.SaveSpace -> saveCustom(category = false)
        }
    }
    private fun panel(panel: String) {
        if (writing && panel.startsWith("close")) return
        change {
        when (panel) {
            "settings" -> it.copy(settingsOpen = true)
            "filters" -> it.copy(filtersOpen = true)
            "date" -> it.copy(editorDateOpen = true)
            "category" -> it.copy(categoryEditor = true, customName = "", customError = null)
            "space" -> it.copy(spaceEditor = true, customName = "", customError = null)
            "close_settings" -> it.copy(settingsOpen = false)
            "close_filters" -> it.copy(filtersOpen = false)
            "close_date" -> it.copy(editorDateOpen = false)
            "close_range" -> it.copy(rangeOpen = false)
            "close_account" -> it.copy(accountEditor = null)
            "close_pocket" -> it.copy(pocketEditor = null)
            "close_category" -> it.copy(categoryEditor = false)
            "close_space" -> it.copy(spaceEditor = false)
            "close" -> it.copy(settingsOpen = false, filtersOpen = false, rangeOpen = false, editorDateOpen = false,
                accountEditor = null, pocketEditor = null, categoryEditor = false, spaceEditor = false)
            else -> it
        }
        }
    }
    private fun change(transform: (MoneyControls) -> MoneyControls) { controls.update(transform) }
    private fun export(pdf: Boolean) {
        if(controls.value.exporting || controls.value.saving || uiState.value.preferences.hideAmounts) return
        val document = uiState.value.reports.document ?: return
        val locale = controls.value.localeTag
        change { it.copy(exporting = true,error = null) }
        viewModelScope.launch {
            try {
                val filename = exporter.create(document,pdf,locale)
                if(!uiState.value.preferences.hideAmounts) effectChannel.send(MoneyEffect.ShareReport(filename,pdf))
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { change { it.copy(error = MoneyUiError.EXPORT) } }
            finally { change { it.copy(exporting = false) } }
        }
    }
    private fun notice(kind: MoneyNoticeKind) { change { it.copy(notice = MoneyNotice(++noticeId, kind)) } }

    private fun openEntry(event: MoneyEvent.OpenEntry) {
        if(event.row?.movement?.investmentId != null) {
            effectChannel.trySend(MoneyEffect.Investments(controls.value.currency,controls.value.spaceId))
            return
        }
        if(event.row?.movement?.principal == true) {
            effectChannel.trySend(MoneyEffect.Commitments("debts",controls.value.currency,controls.value.spaceId))
            return
        }
        if (writing) return
        val data = workspace ?: return
        val original = event.row?.movement
        val incoming = event.row?.incoming
        val candidates = data.ledger.snapshot.accounts.filter { !it.account.archived }
        val preferred = candidates.find { it.account.id == event.source }
            ?: candidates.find { it.account.id == uiState.value.preferences.lastAccountId &&
                it.account.initialBalance.currencyCode == controls.value.currency &&
                (controls.value.spaceId == null || it.account.spaceId == controls.value.spaceId ||
                    data.ledger.snapshot.pockets.any { pocket -> pocket.pocket.accountId == it.account.id && pocket.pocket.spaceId == controls.value.spaceId }) }
            ?: candidates.find { it.account.initialBalance.currencyCode == controls.value.currency &&
                (controls.value.spaceId == null || it.account.spaceId == controls.value.spaceId) }
            ?: candidates.find { it.account.initialBalance.currencyCode == controls.value.currency &&
                data.ledger.snapshot.pockets.any { pocket -> pocket.pocket.accountId == it.account.id && pocket.pocket.spaceId == controls.value.spaceId } }
        if (preferred == null && original == null) { onEvent(MoneyEvent.OpenAccount()); return }
        val sourceId = original?.accountId ?: preferred?.account?.id
        val lastPocket = uiState.value.preferences.lastPocketId?.takeIf { id ->
            data.ledger.snapshot.pockets.any { it.pocket.id == id && it.pocket.accountId == sourceId &&
                (controls.value.spaceId == null || it.pocket.spaceId == controls.value.spaceId) } }
        val matchingPocket = data.ledger.snapshot.pockets.firstOrNull {
            it.pocket.accountId == sourceId && it.pocket.spaceId == controls.value.spaceId &&
                preferred?.account?.spaceId != controls.value.spaceId
        }?.pocket?.id
        val pocket = original?.pocketId ?: if (original == null && event.targetPocket == null) lastPocket ?: matchingPocket else null
        val targetId = incoming?.accountId ?: if (event.targetPocket != null) sourceId
            else candidates.firstOrNull { it.account.id != sourceId }?.account?.id
        val kind = if (incoming != null) MoneyEntryKind.TRANSFER else if (original != null)
            if (original.direction == MoneyDirection.INCOME) MoneyEntryKind.INCOME else MoneyEntryKind.EXPENSE else event.kind
        val editor = MoneyEditorUiState(id = original?.id ?: 0,
            requestId = original?.entryRequestId ?: original?.transferId ?: UUID.randomUUID().toString(),
            transferId = original?.transferId, kind = kind, amount = original?.amount?.decimal?.toPlainString() ?: "",
            receivedAmount = incoming?.amount?.decimal?.toPlainString() ?: "", accountId = sourceId, pocketId = pocket,
            targetAccountId = targetId, targetPocketId = incoming?.pocketId ?: event.targetPocket,
            categoryId = original?.categoryId ?: data.categories.firstOrNull { it.isIncome == (kind == MoneyEntryKind.INCOME) }?.id,
            date = original?.date ?: LocalDate.now(), note = original?.note ?: "", attachmentPath = original?.attachmentPath,
            expanded = original != null || kind == MoneyEntryKind.TRANSFER)
        change { it.copy(editor = editor, error = null) }
        persistDraft()
    }
    private fun updateEditor(value: MoneyEditorUiState) {
        if (writing) return
        val previous = controls.value.editor ?: return
        val accounts = workspace?.ledger?.snapshot?.accounts.orEmpty()
        val oldCurrency = accounts.find { it.account.id == previous.accountId }?.account?.initialBalance?.currencyCode
        val newCurrency = accounts.find { it.account.id == value.accountId }?.account?.initialBalance?.currencyCode
        var updated = value.copy(error = null, requestId = previous.requestId, transferId = previous.transferId, id = previous.id)
        if (previous.kind != value.kind) updated = updated.copy(categoryId = workspace?.categories?.firstOrNull {
            it.isIncome == (value.kind == MoneyEntryKind.INCOME) }?.id)
        if (previous.accountId != value.accountId) updated = updated.copy(pocketId = null,
            amount = if (oldCurrency == newCurrency) value.amount else "")
        if (previous.targetAccountId != value.targetAccountId) updated = updated.copy(targetPocketId = null, receivedAmount = "")
        change { it.copy(editor = updated, error = null) }
        persistDraft()
    }
    private fun saveEntry() {
        val editor = controls.value.editor ?: return
        val data = workspace ?: return
        val error = MoneyPresentation.editorError(editor, data.ledger, data.categories)
        if (error != null) { change { it.copy(editor = editor.copy(error = error)) }; return }
        write {
            val source = data.ledger.snapshot.accounts.first { it.account.id == editor.accountId }.account
            val amount = requireNotNull(MoneyPresentation.amount(editor.amount, source.initialBalance.currencyCode))
            if (editor.kind == MoneyEntryKind.TRANSFER) {
                val target = data.ledger.snapshot.accounts.first { it.account.id == editor.targetAccountId }.account
                val received = if (target.initialBalance.currencyCode == source.initialBalance.currencyCode) amount
                    else requireNotNull(MoneyPresentation.amount(editor.receivedAmount, target.initialBalance.currencyCode))
                gateway.transfer(editor, amount, received)
            } else {
                val space = editor.pocketId?.let { id -> data.ledger.snapshot.pockets.first { it.pocket.id == id }.pocket.spaceId } ?: source.spaceId
                gateway.record(MoneyMovement(id = editor.id, accountId = source.id, amount = amount,
                    direction = if (editor.kind == MoneyEntryKind.INCOME) MoneyDirection.INCOME else MoneyDirection.EXPENSE,
                    date = editor.date, spaceId = space, categoryId = editor.categoryId, pocketId = editor.pocketId,
                    note = editor.note, attachmentPath = editor.attachmentPath, entryRequestId = editor.requestId))
            }
            // A preferences failure after a committed movement must never report that movement as unsaved.
            change { it.copy(editor = null, editorDateOpen = false) }
            savedState["money_draft"] = null
            notice(MoneyNoticeKind.SAVED)
            try { gateway.rememberFunding(source.id, editor.pocketId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice(MoneyNoticeKind.PREFERENCES_FAILED) }
        }
    }
    private fun saveAccount() {
        val form = controls.value.accountEditor ?: return
        val initial = MoneyPresentation.amount(form.initialAmount, form.currency)
        val error = if (form.name.isBlank()) MoneyUiError.NAME else if (initial == null) MoneyUiError.AMOUNT else null
        if (error != null) { change { it.copy(accountEditor = form.copy(error = error)) }; return }
        write {
            gateway.account(MoneyAccount(form.id, form.name.trim(), form.type, requireNotNull(initial), form.spaceId))
            change { it.copy(accountEditor = null, destination = MoneyDestination.ACCOUNTS) }
            notice(MoneyNoticeKind.SAVED)
        }
    }
    private fun savePocket() {
        val form = controls.value.pocketEditor ?: return
        if (form.name.isBlank()) { change { it.copy(pocketEditor = form.copy(error = MoneyUiError.NAME)) }; return }
        write {
            gateway.pocket(form.accountId, form.spaceId, form.name.trim())
            change { it.copy(pocketEditor = null) }; notice(MoneyNoticeKind.SAVED)
        }
    }
    private fun saveCustom(category: Boolean) {
        val name = controls.value.customName.trim()
        if (name.isEmpty()) { change { it.copy(customError = MoneyUiError.NAME) }; return }
        write {
            if (category) {
                val id = gateway.category(name, controls.value.editor?.kind == MoneyEntryKind.INCOME)
                change { it.copy(categoryEditor = false, editor = it.editor?.copy(categoryId = id)) }
                persistDraft()
            } else {
                val id = gateway.space(name)
                change { it.copy(spaceEditor = false, accountEditor = it.accountEditor?.copy(spaceId = id),
                    pocketEditor = it.pocketEditor?.copy(spaceId = id)) }
            }
        }
    }
    private fun write(notify: Boolean = true, action: suspend () -> Unit) {
        if (writing) return
        writing = true
        change { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { action(); if (notify) gateway.notifyChanged() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val error = when (failure) {
                    is MoneyWriteException -> when (failure.reason) {
                        MoneyWriteFailure.INSUFFICIENT_FUNDS -> MoneyUiError.FUNDS
                        MoneyWriteFailure.CURRENCY_CHANGED -> MoneyUiError.CURRENCY
                        MoneyWriteFailure.STALE_ENTRY, MoneyWriteFailure.REQUEST_CONFLICT -> MoneyUiError.CONFLICT
                    }
                    is ArithmeticException -> MoneyUiError.AMOUNT
                    else -> MoneyUiError.SAVE
                }
                change { it.copy(error = error, editor = it.editor?.copy(error = error),
                    accountEditor = it.accountEditor?.copy(error = error), pocketEditor = it.pocketEditor?.copy(error = error)) }
            } finally { writing = false; change { it.copy(saving = false) } }
        }
    }
    private fun persistDraft() { savedState["money_draft"] = controls.value.editor?.bundle() }
}

private fun MoneyEditorUiState.bundle() = Bundle().apply {
    putLong("id", id); putString("request", requestId); putString("transfer", transferId); putString("kind", kind.name)
    putString("amount", amount); putString("received", receivedAmount); putLong("account", accountId ?: -1)
    putLong("pocket", pocketId ?: -1); putLong("target", targetAccountId ?: -1); putLong("targetPocket", targetPocketId ?: -1)
    putLong("category", categoryId ?: -1); putLong("day", date.toEpochDay()); putString("note", note)
    putString("attachment", attachmentPath); putBoolean("expanded", expanded)
}
private fun restoreDraft(bundle: Bundle?): MoneyEditorUiState? = bundle?.let { runCatching {
    MoneyEditorUiState(id = it.getLong("id"), requestId = requireNotNull(it.getString("request")),
        transferId = it.getString("transfer"), kind = MoneyEntryKind.valueOf(requireNotNull(it.getString("kind"))),
        amount = it.getString("amount").orEmpty(), receivedAmount = it.getString("received").orEmpty(),
        accountId = it.getLong("account").takeIf { id -> id != -1L }, pocketId = it.getLong("pocket").takeIf { id -> id != -1L },
        targetAccountId = it.getLong("target").takeIf { id -> id != -1L }, targetPocketId = it.getLong("targetPocket").takeIf { id -> id != -1L },
        categoryId = it.getLong("category").takeIf { id -> id != -1L }, date = LocalDate.ofEpochDay(it.getLong("day")),
        note = it.getString("note").orEmpty(), attachmentPath = it.getString("attachment"), expanded = it.getBoolean("expanded"))
}.getOrNull() }
