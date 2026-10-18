package com.trackermaster.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.domain.money.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MoneyInvestmentViewModel @Inject constructor(private val gateway: MoneyInvestmentGateway,
    private val saved: SavedStateHandle, @MoneyComputation private val computation: CoroutineDispatcher) : ViewModel() {
    private val controls = MutableStateFlow(InvestmentUiState(draft = restore(saved["investment_draft"])))
    private val mutable = MutableStateFlow(controls.value)
    val state = mutable.asStateFlow()
    private var observation: Job? = null
    private var writing = false
    private var privacy: Boolean? = null
    init { observe() }
    fun start(currency: String, space: Long?) { change { it.copy(currency = currency.takeIf { c -> c in listOf("COP", "USD") } ?: "COP", space = space) } }
    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            mutable.update { it.copy(loading = true, loadError = false) }
            try {
                gateway.initialize()
                val data = combine(gateway.workspace, gateway.book, gateway.occupied) { w, b, o -> Triple(w, b, o) }
                combine(data, gateway.preferences, gateway.settings, controls) { (w,b,occupied), prefs, settings, control ->
                    val rows = b.positions.filter { it.currency == control.currency && (control.space == null || it.spaceId == control.space) }
                        .map { InvestmentRules.progress(it, b.events, LocalDate.now()) }
                    val draft = control.draft
                    val candidates = if (draft?.kind != null && draft.kind != InvestmentEventKind.VALUATION) w.ledger.movements.filter { m ->
                        m.investmentId == null && !m.principal && m.transferId == null && m.id !in occupied && m.accountId == draft.account && m.pocketId == draft.pocket &&
                            m.spaceId == draft.space && m.amount == MoneyPresentation.amount(draft.amount, draft.currency) &&
                            m.direction == (if (draft.kind == InvestmentEventKind.CONTRIBUTION) MoneyDirection.EXPENSE else MoneyDirection.INCOME) &&
                            runCatching { kotlin.math.abs(m.date.toEpochDay() - LocalDate.parse(draft.date).toEpochDay()) <= 7 }.getOrDefault(false)
                    }.sortedByDescending { it.date } else emptyList()
                    control.copy(loading = false, loadError = false, workspace = w, book = b, settings = settings, preferences = prefs,
                        rows = rows, candidates = candidates, canSave = draft != null && !control.saving && valid(draft,w,b))
                }.flowOn(computation).collect { current ->
                    if (privacy == current.preferences.hideAmounts) privacy = null
                    mutable.value = current.copy(preferences = current.preferences.copy(hideAmounts = privacy ?: current.preferences.hideAmounts))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loading = false, loadError = true, preferences = it.preferences.copy(hideAmounts = true)) } }
        }
    }

    fun onAction(action: InvestmentAction) {
        if (writing && action != InvestmentAction.Privacy) return
        when (action) {
            InvestmentAction.Retry -> observe()
            InvestmentAction.New -> change { it.copy(draft = InvestmentDraft(currency = it.currency, space = it.space ?: if (it.currency == "USD") 5 else 2), error = false) }
            InvestmentAction.Close -> change { it.copy(draft = null, selected = if (it.draft != null) it.selected else null, dateOpen = false, error = false) }
            is InvestmentAction.Currency -> change { it.copy(currency = action.value, selected = null) }
            is InvestmentAction.Space -> change { it.copy(space = action.value, selected = null) }
            is InvestmentAction.Show -> change { it.copy(selected = action.id, error = false) }
            is InvestmentAction.Edit -> action.value.let { p -> change { it.copy(draft = InvestmentDraft(id = p.id, version = p.version, editing = true,
                name = p.name, currency = p.currency, space = p.spaceId, date = p.opened.toString(), amount = p.initialValue.decimal.toPlainString(), cost = p.initialCost.decimal.toPlainString(), note = p.note), error = false) } }
            is InvestmentAction.Operation -> {
                val p = action.value
                val w = state.value.workspace
                val account = w?.ledger?.snapshot?.accounts?.firstOrNull { !it.account.archived && it.account.spaceId == p.spaceId && it.total.currencyCode == p.currency }
                val pocket = if (account == null) w?.ledger?.snapshot?.pockets?.firstOrNull { row -> row.pocket.spaceId == p.spaceId && row.balance.currencyCode == p.currency && w.ledger.snapshot.accounts.any { it.account.id == row.pocket.accountId && !it.account.archived } } else null
                change { it.copy(draft = InvestmentDraft(id = p.id, version = p.version, kind = action.kind, name = p.name,
                    currency = p.currency, space = p.spaceId, amount = "", cost = "0", account = account?.account?.id ?: pocket?.pocket?.accountId, pocket = pocket?.pocket?.id,
                    category = w?.categories?.firstOrNull { c -> c.isIncome }?.id), error = false) }
            }
            is InvestmentAction.Draft -> change { it.copy(draft = action.value, error = false) }
            is InvestmentAction.Link -> change { s -> s.copy(draft = s.draft?.let { d -> action.value?.let { m -> d.copy(linked = m.id, date = m.date.toString(), note = m.note, category = m.categoryId) } ?: d.copy(linked = null) }) }
            is InvestmentAction.Date -> change { it.copy(dateOpen = action.day == null, draft = if (action.day == null) it.draft else it.draft?.copy(date = action.day.toString())) }
            is InvestmentAction.Archive -> write { gateway.archive(action.value) }
            is InvestmentAction.Reverse -> change { it.copy(reverse = action.value) }
            InvestmentAction.ConfirmReverse -> state.value.reverse?.let { e -> state.value.book.positions.find { it.id == e.investmentId }?.let { p -> write { gateway.reverse(e.sequence,p.version); change { it.copy(reverse = null) } } } }
            InvestmentAction.Save -> state.value.draft?.takeIf { state.value.canSave }?.let { draft -> write {
                gateway.save(draft)
                change { it.copy(draft = null, selected = draft.id, currency = draft.currency, space = if (it.space == null) null else draft.space) }
            } }
            InvestmentAction.Privacy -> {
                val hidden = !state.value.preferences.hideAmounts
                privacy = hidden
                mutable.update { it.copy(preferences = it.preferences.copy(hideAmounts = hidden)) }
                viewModelScope.launch { try { gateway.privacy(hidden) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { privacy = true; mutable.update { it.copy(error = true, preferences = it.preferences.copy(hideAmounts = true)) } } }
            }
        }
    }

    private fun write(block: suspend () -> Unit) {
        if (writing) return
        writing = true
        change { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            try { block(); gateway.changed() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { change { it.copy(error = true) } }
            finally { writing = false; change { it.copy(saving = false) } }
        }
    }
    private fun change(block: (InvestmentUiState) -> InvestmentUiState) {
        controls.update(block)
        saved["investment_draft"] = controls.value.draft?.let(::encode)
    }

    private fun valid(d: InvestmentDraft, w: MoneyWorkspaceData, b: InvestmentBook) = runCatching {
        val amount = requireNotNull(MoneyPresentation.amount(d.amount,d.currency))
        val cost = requireNotNull(MoneyPresentation.amount(d.cost,d.currency))
        val date = LocalDate.parse(d.date)
        require(!date.isAfter(LocalDate.now()) && d.name.isNotBlank())
        require(w.spaces.any { it.id == d.space })
        if (d.kind == null) {
            require(amount.minorUnits >= 0 && cost.minorUnits >= 0)
            if (d.editing) {
                val p = requireNotNull(b.positions.find { it.id == d.id && it.version == d.version && !it.archived })
                require(p.currency == d.currency && p.spaceId == d.space)
                InvestmentRules.progress(p.copy(initialCost = cost,initialValue = amount,opened = date),b.events,LocalDate.now())
            }
        } else {
            val p = requireNotNull(b.positions.find { it.id == d.id && !it.archived })
            require(p.version == d.version)
            InvestmentRules.progress(p,b.events + InvestmentEvent(d.request,d.id,d.kind,amount,date,if(d.kind == InvestmentEventKind.WITHDRAWAL) cost else amount,sequence = Long.MAX_VALUE),LocalDate.now())
            if (d.kind != InvestmentEventKind.VALUATION) {
                val a = requireNotNull(w.ledger.snapshot.accounts.find { it.account.id == d.account && !it.account.archived })
                require(a.total.currencyCode == d.currency)
                val pocket = d.pocket?.let { id -> requireNotNull(w.ledger.snapshot.pockets.find { it.pocket.id == id && it.pocket.accountId == a.account.id }) }
                require((pocket?.pocket?.spaceId ?: a.account.spaceId) == d.space)
                if (d.kind == InvestmentEventKind.CONTRIBUTION && d.linked == null) require((pocket?.balance ?: a.unallocated).minorUnits >= amount.minorUnits)
                if (d.kind == InvestmentEventKind.DIVIDEND) require(w.categories.any { it.id == d.category && it.isIncome })
            }
        }
        true
    }.getOrDefault(false)

    private fun encode(d: InvestmentDraft) = JSONObject().apply {
        put("id",d.id);put("version",d.version);put("kind",d.kind?.name);put("editing",d.editing);put("request",d.request)
        put("name",d.name);put("amount",d.amount);put("cost",d.cost);put("currency",d.currency);put("space",d.space)
        put("date",d.date);put("account",d.account);put("pocket",d.pocket);put("category",d.category);put("note",d.note);put("linked",d.linked)
    }.toString()
    private fun restore(raw: String?): InvestmentDraft? = runCatching { val j = JSONObject(requireNotNull(raw)); InvestmentDraft(j.getString("id"),j.getLong("version"),
        j.optString("kind").takeIf { it.isNotEmpty() }?.let(InvestmentEventKind::valueOf),j.optBoolean("editing"),j.getString("request"),j.getString("name"),j.getString("amount"),j.getString("cost"),j.getString("currency"),j.getLong("space"),j.getString("date"),
        if(j.has("account"))j.getLong("account")else null,if(j.has("pocket"))j.getLong("pocket")else null,if(j.has("category"))j.getLong("category")else null,j.getString("note"),if(j.has("linked"))j.getLong("linked")else null) }.getOrNull()
}
