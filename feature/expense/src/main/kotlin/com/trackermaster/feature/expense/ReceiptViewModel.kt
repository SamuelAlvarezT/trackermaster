package com.trackermaster.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.money.ReceiptSource
import com.trackermaster.core.data.money.ReceiptDraftCodec
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ReceiptUiState(val workspace: MoneyWorkspaceData? = null, val drafts: List<MoneyReceipt> = emptyList(),
    val receipt: MoneyReceipt? = null, val form: ReceiptDraft = ReceiptDraft(), val analysis: ReceiptAnalysis = ReceiptAnalysis(),
    val settings: AppSettings = AppSettings(), val hidden: Boolean = true, val loading: Boolean = true,
    val reading: Boolean = false, val saving: Boolean = false, val saved: Boolean = false,
    val checking: Boolean = false, val duplicates: List<ReceiptDuplicate> = emptyList(),
    val error: ReceiptFailure? = null, val saveError: Boolean = false, val canSave: Boolean = false)
sealed interface ReceiptEvent {
    data class Update(val form: ReceiptDraft) : ReceiptEvent
    data class Select(val id: String) : ReceiptEvent
    data class Discard(val id: String) : ReceiptEvent
    data class Failure(val reason: ReceiptFailure) : ReceiptEvent
    data object Acknowledge : ReceiptEvent
    data object Save : ReceiptEvent
    data object Back : ReceiptEvent
    data object Retry : ReceiptEvent
    data object Privacy : ReceiptEvent
}
@HiltViewModel
class ReceiptViewModel @Inject constructor(private val gateway: ReceiptGateway, private val handle: SavedStateHandle,
    @MoneyComputation private val dispatcher: CoroutineDispatcher) : ViewModel() {
    private val mutable = MutableStateFlow(ReceiptUiState())
    val state = mutable.asStateFlow()
    private var observation: Job? = null
    private var scanning: Job? = null
    private var checking: Job? = null
    private var privateOverride: Boolean? = null
    init {
        observe()
        handle.get<String>("receipt_id")?.let { select(it) }
    }
    private fun change(block: (ReceiptUiState)->ReceiptUiState) { mutable.update { validate(block(it)) } }
    private fun validate(s: ReceiptUiState): ReceiptUiState {
        val data = s.workspace
        val valid = data != null && s.receipt != null && s.error == null && !s.loading && !s.reading && !s.saving && !s.saved && !s.checking && s.form.confirmed &&
            s.analysis.status !in listOf(ReceiptBankStatus.PENDING,ReceiptBankStatus.FAILED) && runCatching {
                val movement = ReceiptValidation.movement(s.form,data,s.receipt.id,s.receipt.path)
                if (s.form.kind == ReceiptEntryKind.TRANSFER) {
                    val target = data.ledger.snapshot.accounts.single { it.account.id == s.form.targetAccountId }.account
                    require(!target.archived && (target.id != s.form.accountId || s.form.pocketId != s.form.targetPocketId))
                    s.form.targetPocketId?.let { id -> require(data.ledger.snapshot.pockets.any { it.pocket.id == id && it.pocket.accountId == target.id }) }
                    if (target.initialBalance.currencyCode != movement.amount.currencyCode)
                        require(ReceiptValidation.amount(s.form.receivedAmount,target.initialBalance.currencyCode).minorUnits > 0)
                }
            }.isSuccess && (s.duplicates.isEmpty() || s.form.duplicateAcknowledgement == s.duplicates.fingerprint())
        return s.copy(canSave = valid)
    }
    private fun observe() {
        observation?.cancel()
        change {it.copy(loading=true,error=null)}
        observation = viewModelScope.launch(dispatcher) {
            try {
                gateway.initialize()
                combine(gateway.workspace,gateway.drafts,gateway.preferences,gateway.settings) { data,drafts,prefs,settings ->
                    if(privateOverride==prefs.hideAmounts) privateOverride=null
                    change { it.copy(workspace=data,drafts=drafts,settings=settings,hidden=privateOverride ?: prefs.hideAmounts,loading=false) }
                }.collect { if (state.value.receipt != null && !state.value.reading && !state.value.saving && !state.value.saved) check() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { change { it.copy(workspace=null,loading=false,error=ReceiptFailure.UNREADABLE,hidden=true) } }
        }
    }
    fun receive(source: ReceiptSource) {
        if (state.value.reading || state.value.saving) return
        checking?.cancel()
        change { it.copy(reading=true,saved=false,error=null,receipt=null) }
        scanning = viewModelScope.launch(dispatcher) {
            try {
                val receipt = gateway.import(source); handle["receipt_id"] = receipt.id; handle["receipt_form"] = null
                change { it.copy(receipt=receipt) }
                loaded(gateway.read(receipt.id))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { change { it.copy(reading=false,error=(e as? ReceiptException)?.reason ?: ReceiptFailure.UNREADABLE) } }
        }
    }
    private fun select(id: String) {
        if (state.value.saving || state.value.reading) return
        handle["receipt_id"] = id
        change { it.copy(reading=true,saved=false,error=null) }
        scanning = viewModelScope.launch(dispatcher) {
            try {
                val row = gateway.get(id)
                loaded(if(row.stage == "READING") gateway.read(id) else row)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { change { it.copy(reading=false,error=ReceiptFailure.UNREADABLE) } }
        }
    }
    private fun loaded(row: MoneyReceipt) {
        val form = handle.get<String>("receipt_form")?.let(ReceiptDraftCodec::decode)
            ?: ReceiptDraftCodec.decode(row.draftJson) ?: ReceiptDraft.from(ReceiptParser.parse(row.text))
        change { it.copy(receipt=row,form=form,analysis=ReceiptParser.parse(row.text),reading=false,
            saved=row.stage == "CONFIRMED",error=null,duplicates=emptyList()) }
        check()
    }
    private fun fingerprint(d: ReceiptDraft) = listOf(d.kind,d.amount,d.currency,d.date,d.accountId,d.pocketId,d.targetAccountId,d.targetPocketId,d.receivedAmount,d.reference)
    fun onEvent(event: ReceiptEvent) {
        when(event) {
            is ReceiptEvent.Update -> if (!state.value.saving && !state.value.reading && !state.value.saved) {
                val old=state.value.form
                val form=event.form.copy(duplicateAcknowledgement=if(fingerprint(old)==fingerprint(event.form)) old.duplicateAcknowledgement else null)
                change { it.copy(form=form,error=null,saveError=false) }; handle["receipt_form"]=ReceiptDraftCodec.encode(form); check()
            }
            is ReceiptEvent.Select -> { handle["receipt_form"]=null; select(event.id) }
            is ReceiptEvent.Failure -> change { it.copy(error=event.reason) }
            is ReceiptEvent.Discard -> if(!state.value.saving) viewModelScope.launch(dispatcher) {
                try { gateway.discard(event.id); if(state.value.receipt?.id==event.id) onEvent(ReceiptEvent.Back) }
                catch(e:CancellationException){throw e} catch(_:Exception){change{it.copy(error=ReceiptFailure.UNREADABLE)}}
            }
            ReceiptEvent.Acknowledge -> {
                change { it.copy(error=null,form=it.form.copy(duplicateAcknowledgement=if(it.form.duplicateAcknowledgement==it.duplicates.fingerprint()) null else it.duplicates.fingerprint())) }
                handle["receipt_form"]=ReceiptDraftCodec.encode(state.value.form)
                check()
            }
            ReceiptEvent.Save -> save()
            ReceiptEvent.Back -> if(!state.value.saving) {
                scanning?.cancel(); checking?.cancel(); handle["receipt_id"]=null; handle["receipt_form"]=null
                change { it.copy(receipt=null,form=ReceiptDraft(),reading=false,saved=false,error=null,saveError=false,duplicates=emptyList(),checking=false) }
            }
            ReceiptEvent.Retry -> if(state.value.workspace==null) observe() else state.value.receipt?.id?.let { select(it) }
            ReceiptEvent.Privacy -> {
                privateOverride=!state.value.hidden; change{it.copy(hidden=requireNotNull(privateOverride))}
                viewModelScope.launch(dispatcher) { try { gateway.privacy(requireNotNull(privateOverride)) }
                    catch(e:CancellationException){throw e} catch(_:Exception){change{it.copy(saveError=true)}} }
            }
        }
    }
    private fun check() {
        checking?.cancel()
        val receipt=state.value.receipt ?: return
        if (state.value.saved || state.value.reading || state.value.saving) return
        val form=state.value.form
        change { it.copy(checking=true) }
        checking=viewModelScope.launch(dispatcher) {
            try {
                delay(150); gateway.persist(receipt.id,form)
                val matches=gateway.duplicates(receipt.id,form)
                if(state.value.receipt?.id==receipt.id && state.value.form==form) change { it.copy(duplicates=matches,checking=false) }
            } catch(e:CancellationException){throw e}
            catch(_:Exception){change{it.copy(checking=false,error=ReceiptFailure.UNREADABLE)}}
        }
    }
    private fun save() {
        val current=state.value
        if(!current.canSave || current.saving) return
        val receipt=current.receipt ?: return
        change{it.copy(saving=true,saveError=false)}
        viewModelScope.launch(dispatcher) {
            try {
                gateway.confirm(receipt.id,current.form)
                handle["receipt_id"]=null; handle["receipt_form"]=null
                change{it.copy(saving=false,saved=true)}
            } catch(e:CancellationException){throw e}
            catch(e:Exception){change{it.copy(saving=false,saveError=true,error=(e as? ReceiptException)?.reason)};check()}
        }
    }
}
