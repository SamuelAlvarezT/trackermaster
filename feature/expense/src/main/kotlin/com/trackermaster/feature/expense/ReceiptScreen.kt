package com.trackermaster.feature.expense

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReceiptScreen(state: ReceiptUiState, onEvent: (ReceiptEvent)->Unit, onPick: ()->Unit,
    onClose: ()->Unit, onNewAccount: ()->Unit = {}, onView: (String)->Unit = {}, preview: File? = null) {
    var showText by remember(state.receipt?.id) { mutableStateOf(false) }
    var dateOpen by remember(state.receipt?.id) { mutableStateOf(false) }
    var discard by remember { mutableStateOf<String?>(null) }
    var duplicateDetail by remember { mutableStateOf<Long?>(null) }
    var groupDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var confirmBatch by remember { mutableStateOf(false) }
    var batchDistinct by remember(state.selectedDrafts, state.drafts) { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    val form=state.form
    fun update(d: ReceiptDraft) = onEvent(ReceiptEvent.Update(d))
    Scaffold(topBar = { TopAppBar(title={ Text(stringResource(R.string.r_title)) }, navigationIcon={
        IconButton({ if(state.receipt != null) onEvent(ReceiptEvent.Back) else onClose() },enabled=!state.saving) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.r_back)) }
    }, actions={ IconButton({onEvent(ReceiptEvent.Privacy)}) {
        Icon(if(state.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            stringResource(if(state.hidden) R.string.m_show else R.string.m_hide)) } }) },
        bottomBar = { if(state.receipt != null && !state.reading && !state.saved) Surface(Modifier.navigationBarsPadding().imePadding(),shadowElevation=8.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({onEvent(ReceiptEvent.SaveDraft)}, Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("receipt_save_draft"), enabled=!state.saving) {
                    Text(stringResource(R.string.r_save_draft))
                }
                FilledTonalButton({onEvent(ReceiptEvent.Save)},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("receipt_save"),enabled=state.canSave) {
                    Text(stringResource(if(state.saving) R.string.r_saving else R.string.r_save))
                }
            }
        } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("receipt_content"),contentPadding=PaddingValues(16.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(state.loading || state.reading) item("loading") {
                Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(); Text(stringResource(R.string.r_reading)) }
            }
            if(state.error != null) item("error") {
                ReceiptMessage(stringResource(receiptErrorResource(state.error)),error=true)
                val choose=state.receipt==null && state.workspace!=null
                MoneyTextButton({if(choose) onPick() else onEvent(ReceiptEvent.Retry)}) {Text(stringResource(if(choose) R.string.r_choose else R.string.m_retry))}
            }
            if(state.saveError) item("save_error") {ReceiptMessage(stringResource(R.string.r_save_error),error=true)}
            if(state.saved) item("saved") {
                MoneyEmpty(stringResource(R.string.r_saved),stringResource(R.string.r_saved_detail),stringResource(R.string.r_done),
                    Icons.Default.CheckCircle,onClose)
                MoneyOutlinedButton({onEvent(ReceiptEvent.Back)},Modifier.fillMaxWidth()) { Text(stringResource(R.string.r_another)) }
            } else if(!state.loading && !state.reading && state.receipt == null) {
                item("choose") {
                    MoneyEmpty(stringResource(R.string.r_import),stringResource(R.string.r_intro),stringResource(R.string.r_choose),
                        Icons.AutoMirrored.Filled.ReceiptLong,onPick)
                    Text(stringResource(R.string.r_limits),style=MaterialTheme.typography.bodySmall)
                }
                if(state.drafts.isNotEmpty()) item("pending_title") { Text(stringResource(R.string.r_pending_drafts),style=MaterialTheme.typography.titleMedium) }
                if(state.groupError) item("group_error") { ReceiptMessage(stringResource(R.string.group_create_error), true) }
                if(state.batchError) item("batch_error") {
                    val failed = state.drafts.find { it.id == state.batchErrorId }
                    Column {
                        ReceiptMessage(if(failed == null) stringResource(R.string.group_batch_error) else stringResource(R.string.group_batch_item_error,failed.name), true)
                        if (failed != null) MoneyTextButton({ onEvent(ReceiptEvent.Select(failed.id)) }) { Text(stringResource(R.string.group_view_detail)) }
                    }
                }
                if(state.selectedDrafts.isNotEmpty()) item("draft_actions") {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.group_selected,state.selectedDrafts.size),style=MaterialTheme.typography.labelLarge)
                        MoneyOutlinedButton({groupName="";groupDialog=true},Modifier.fillMaxWidth(),enabled=!state.saving) { Text(stringResource(R.string.group_create)) }
                        if (state.groups.isNotEmpty()) Box {
                            MoneyTextButton({groupMenu=true},enabled=!state.saving) { Text(stringResource(R.string.group_add_existing)) }
                            DropdownMenu(groupMenu,{groupMenu=false}) { state.groups.forEach { group ->
                                DropdownMenuItem(text={Text(group.name)},onClick={groupMenu=false;onEvent(ReceiptEvent.AddToGroup(group.id))})
                            } }
                        }
                        val keys=state.selectedDrafts.map { "receipt:$it" }.toSet()
                        val grouped=state.groups.any { it.memberKeys.containsAll(keys) }
                        FilledTonalButton({batchDistinct=false;confirmBatch=true},Modifier.fillMaxWidth(),enabled=grouped && !state.saving) { Text(stringResource(R.string.group_confirm)) }
                        if(!grouped) Text(stringResource(R.string.group_first),style=MaterialTheme.typography.bodySmall)
                    }
                }
                items(state.drafts,key={it.id}) { row ->
                    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(row.id in state.selectedDrafts,{onEvent(ReceiptEvent.ToggleDraft(row.id))},Modifier.testTag("receipt-select:${row.id}"),enabled=!state.saving)
                        Column(Modifier.weight(1f)) {
                            MoneyTextButton({onEvent(ReceiptEvent.Select(row.id))},Modifier.fillMaxWidth(),enabled=!state.saving) { Text(row.name,maxLines=2) }
                            state.groups.firstOrNull { "receipt:${row.id}" in it.memberKeys }?.let { group ->
                                Text(stringResource(R.string.group_draft_in,group.name),style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton({discard=row.id},enabled=!state.saving) {Icon(Icons.Default.Delete,stringResource(R.string.r_discard))}
                    } }
                }
            } else if(!state.loading && !state.reading && state.receipt != null) {
                val receipt=state.receipt
                val data=state.workspace
                item("review_hint") {
                    Text(receipt.name,style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.r_review_hint),style=MaterialTheme.typography.bodyMedium)
                    if(receipt.stage=="UNREADABLE") ReceiptMessage(stringResource(R.string.r_unreadable_review))
                }
                item("source") {
                    if(state.hidden) ReceiptMessage(stringResource(R.string.r_private)) else {
                        ReceiptPreview(preview)
                        MoneyOutlinedButton({onView(receipt.path)},Modifier.fillMaxWidth()) { Text(stringResource(R.string.r_original)) }
                        MoneyTextButton({showText=!showText}) {Text(stringResource(if(showText) R.string.r_hide_text else R.string.r_show_text))}
                        if(showText) Text(receipt.text.ifBlank{stringResource(R.string.r_raw_empty)},style=MaterialTheme.typography.bodySmall)
                        state.analysis.sender?.let {Text(stringResource(R.string.r_sender,it))}
                        state.analysis.recipient?.let {Text(stringResource(R.string.r_recipient,it))}
                    }
                    Text(stringResource(when(state.analysis.status){
                        ReceiptBankStatus.SUCCESS->R.string.r_status_success;ReceiptBankStatus.PENDING->R.string.r_status_pending
                        ReceiptBankStatus.FAILED->R.string.r_status_failed;ReceiptBankStatus.UNKNOWN->R.string.r_status_unknown
                    }),style=MaterialTheme.typography.labelLarge)
                    if(state.analysis.status in listOf(ReceiptBankStatus.PENDING,ReceiptBankStatus.FAILED)) ReceiptMessage(stringResource(R.string.r_bank_blocked),true)
                }
                item("type") {
                    val detectedKind = state.analysis.direction?.let { if (it == MoneyDirection.INCOME) ReceiptEntryKind.INCOME else ReceiptEntryKind.EXPENSE }
                    if (detectedKind != null && detectedKind == form.kind) Text(stringResource(R.string.r_detected),style=MaterialTheme.typography.labelSmall)
                    if(form.kind==null) Text(stringResource(R.string.r_unknown_type),color=MaterialTheme.colorScheme.error)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {ReceiptEntryKind.entries.forEach { kind ->
                        FilterChip(selected=form.kind==kind,onClick={update(form.copy(kind=kind,categoryId=null))},
                            label={Text(stringResource(when(kind){ReceiptEntryKind.INCOME->R.string.m_income;ReceiptEntryKind.EXPENSE->R.string.m_expense;ReceiptEntryKind.TRANSFER->R.string.m_transfer}))},
                            modifier=Modifier.heightIn(min=48.dp).testTag("receipt_kind:${kind.name}"))
                    } }
                }
                item("currency") {
                    if (!form.currencyVerified) Text(stringResource(R.string.r_currency_review),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    else if (state.analysis.amount?.currencyCode == form.currency) Text(stringResource(R.string.r_detected),style=MaterialTheme.typography.labelSmall)
                    MoneyChoice(stringResource(R.string.m_currency),form.currency,listOf("COP" to "COP","USD" to "USD"),
                        {update(form.copy(currency=it,currencyVerified=true,amount=if(it==form.currency) form.amount else "",accountId=if(it==form.currency) form.accountId else null,pocketId=if(it==form.currency) form.pocketId else null,targetAccountId=if(it==form.currency) form.targetAccountId else null,targetPocketId=if(it==form.currency) form.targetPocketId else null,receivedAmount=if(it==form.currency) form.receivedAmount else ""))})
                }
                item("amount") {
                    if(state.analysis.amount?.currencyCode == form.currency && ReceiptDraft.from(state.analysis).amount == form.amount) Text(stringResource(R.string.r_detected),style=MaterialTheme.typography.labelSmall)
                    else if(form.amount.isBlank()) Text(stringResource(R.string.r_missing_or_ambiguous),style=MaterialTheme.typography.labelSmall)
                    ReceiptInput(form.amount,stringResource(R.string.m_amount),"receipt_amount",{update(form.copy(amount=it.take(24)))},KeyboardType.Decimal,state.hidden)
                }
                item("account") {
                    val accounts=data?.ledger?.snapshot?.accounts.orEmpty().filter{!it.account.archived && it.account.initialBalance.currencyCode==form.currency}
                    if (state.detectedAccountId != null && state.detectedAccountId == form.accountId) Text(stringResource(R.string.r_detected),style=MaterialTheme.typography.labelSmall)
                    else if (form.accountId == null) Text(stringResource(R.string.r_missing_or_ambiguous),style=MaterialTheme.typography.labelSmall)
                    MoneyChoice(stringResource(R.string.m_account),accounts.find{it.account.id==form.accountId}?.account?.name ?: stringResource(R.string.r_missing_account),
                        accounts.map{it.account.id.toString() to it.account.name}, {update(form.copy(accountId=it.toLong(),pocketId=null))})
                    MoneyTextButton(onNewAccount){Text(stringResource(R.string.m_create_account))}
                }
                if(data!=null && form.accountId!=null) item("pocket") {
                    val pockets=data.ledger.snapshot.pockets.filter{it.pocket.accountId==form.accountId}
                    MoneyChoice(stringResource(R.string.m_pockets),pockets.find{it.pocket.id==form.pocketId}?.pocket?.name ?: stringResource(R.string.m_unallocated),
                        listOf("" to stringResource(R.string.m_unallocated))+pockets.map{it.pocket.id.toString() to it.pocket.name},
                        {update(form.copy(pocketId=it.toLongOrNull()))})
                    val spaceId=pockets.find{it.pocket.id==form.pocketId}?.pocket?.spaceId ?: data.ledger.snapshot.accounts.find{it.account.id==form.accountId}?.account?.spaceId
                    Text(stringResource(R.string.r_funding_space,spaceTitle(data.spaces.find{it.id==spaceId})),style=MaterialTheme.typography.bodySmall)
                }
                if(form.kind==ReceiptEntryKind.TRANSFER && data!=null) {
                    item("target_account") {
                        val targets=data.ledger.snapshot.accounts.filter{!it.account.archived}
                        MoneyChoice(stringResource(R.string.m_target),targets.find{it.account.id==form.targetAccountId}?.account?.name ?: stringResource(R.string.r_missing_account),
                            targets.map{it.account.id.toString() to "${it.account.name} · ${it.account.initialBalance.currencyCode}"},
                            {update(form.copy(targetAccountId=it.toLong(),targetPocketId=null,receivedAmount=""))})
                    }
                    item("target_pocket") {
                        val pockets=data.ledger.snapshot.pockets.filter{it.pocket.accountId==form.targetAccountId}
                        MoneyChoice(stringResource(R.string.m_pockets),pockets.find{it.pocket.id==form.targetPocketId}?.pocket?.name ?: stringResource(R.string.m_unallocated),
                            listOf("" to stringResource(R.string.m_unallocated))+pockets.map{it.pocket.id.toString() to it.pocket.name},
                            {update(form.copy(targetPocketId=it.toLongOrNull()))})
                    }
                    val target=data.ledger.snapshot.accounts.find{it.account.id==form.targetAccountId}?.account
                    if(target!=null && target.initialBalance.currencyCode!=form.currency) item("received") {
                        ReceiptInput(form.receivedAmount,"${stringResource(R.string.m_received)} · ${target.initialBalance.currencyCode}","receipt_received",
                            {update(form.copy(receivedAmount=it.take(24)))},KeyboardType.Decimal,state.hidden)
                    }
                } else if(form.kind!=null) item("category") {
                    val categories=data?.categories.orEmpty().filter{it.isIncome==(form.kind==ReceiptEntryKind.INCOME)}
                    val titles=categories.map{it.id.toString() to categoryTitle(it)}
                    MoneyChoice(stringResource(R.string.m_category),categories.find{it.id==form.categoryId}?.let{categoryTitle(it)} ?: stringResource(R.string.r_missing_category),
                        titles,{update(form.copy(categoryId=it.toLong()))})
                }
                item("date") {
                    if (form.date.isBlank()) Text(stringResource(R.string.r_missing_or_ambiguous),style=MaterialTheme.typography.labelSmall)
                    else if (state.analysis.date?.toString() == form.date) Text(stringResource(R.string.r_detected),style=MaterialTheme.typography.labelSmall)
                    val day=runCatching{LocalDate.parse(form.date)}.getOrNull()
                    val label=day?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
                        ?: stringResource(R.string.r_date_missing)
                    MoneyOutlinedButton({dateOpen=true},Modifier.fillMaxWidth().testTag("receipt_date")) {Icon(Icons.Default.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(label)}
                }
                item("time") {
                    if (form.time.isBlank()) Text(stringResource(R.string.r_time_unspecified),style=MaterialTheme.typography.labelSmall)
                    else if (state.analysis.time?.toString() == form.time) Text(stringResource(R.string.r_detected),style=MaterialTheme.typography.labelSmall)
                    ReceiptInput(form.time,stringResource(R.string.r_time),"receipt_time",{update(form.copy(time=it.take(5)))},KeyboardType.Text)
                }
                item("reference") {ReceiptInput(form.reference,stringResource(R.string.r_reference),"receipt_reference",{update(form.copy(reference=it.take(160)))})}
                item("note") {ReceiptInput(form.note,stringResource(R.string.m_note),"receipt_note",{update(form.copy(note=it.take(2000)))})}
                if(state.duplicates.isNotEmpty()) item("duplicates") {
                    ReceiptMessage(stringResource(if (state.duplicates.any { it.reason == ReceiptDuplicateReason.FILE }) R.string.r_duplicate_exact else R.string.r_duplicate),true)
                    state.duplicates.map{it.reason}.distinct().forEach { reason -> Text(stringResource(when(reason){
                        ReceiptDuplicateReason.FILE->R.string.r_duplicate_file; ReceiptDuplicateReason.REFERENCE->R.string.r_duplicate_reference
                        ReceiptDuplicateReason.SIMILAR->R.string.r_duplicate_similar })) }
                    state.duplicates.map{it.movementId}.distinct().take(3).forEach { id ->
                        data?.ledger?.movements?.find{it.id==id}?.let{m->
                            Text("${data.ledger.snapshot.accounts.find{it.account.id==m.accountId}?.account?.name.orEmpty()} · ${dayLabel(m.date)} · ${moneyLabel(m.amount,state.hidden)}",style=MaterialTheme.typography.bodySmall)
                            MoneyTextButton({duplicateDetail=id},enabled=!state.hidden) { Text(stringResource(R.string.group_view_detail)) }
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(form.duplicateAcknowledgement==state.duplicates.fingerprint(),{onEvent(ReceiptEvent.Acknowledge)},Modifier.testTag("receipt_duplicate_ack"))
                        Text(stringResource(R.string.r_duplicate_accept),Modifier.weight(1f))
                    }
                }
                item("confirmation") {Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(form.confirmed,{update(form.copy(confirmed=it))},Modifier.testTag("receipt_confirm"),enabled=state.analysis.status !in listOf(ReceiptBankStatus.FAILED,ReceiptBankStatus.PENDING))
                    Text(stringResource(R.string.r_confirm),Modifier.weight(1f))
                } }
                item("discard") { MoneyTextButton({discard=receipt.id}) {Text(stringResource(R.string.r_discard))} }
            }
        }
    }
    duplicateDetail?.let { id ->
        val data = state.workspace
        val movement = data?.ledger?.movements?.find { it.id == id }
        if (movement != null && data != null) AlertDialog(onDismissRequest={duplicateDetail=null},
            title={Text(stringResource(R.string.r_existing_detail))},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(data.ledger.snapshot.accounts.find { it.account.id==movement.accountId }?.account?.name.orEmpty())
                Text(dayLabel(movement.date))
                Text(moneyLabel(movement.amount,state.hidden))
                data.categories.find { it.id==movement.categoryId }?.let { Text(categoryTitle(it)) }
                if (!state.hidden && movement.note.isNotBlank()) Text(movement.note)
                movement.attachmentPath?.takeIf { it.startsWith("money/receipts/") }?.let { path ->
                    MoneyTextButton({onView(path)},enabled=!state.hidden) {Text(stringResource(R.string.r_original))}
                }
            }}, confirmButton={TextButton(onClick={duplicateDetail=null}) {Text(stringResource(R.string.r_close))}})
    }
    if(groupDialog) AlertDialog(onDismissRequest={groupDialog=false},title={Text(stringResource(R.string.group_create))},
        text={OutlinedTextField(groupName,{groupName=it.take(120)},label={Text(stringResource(R.string.group_name))},singleLine=true)},
        confirmButton={TextButton(onClick={onEvent(ReceiptEvent.CreateGroup(groupName));groupDialog=false},enabled=groupName.isNotBlank()) {Text(stringResource(R.string.m_save))}},
        dismissButton={TextButton(onClick={groupDialog=false}) {Text(stringResource(R.string.m_cancel))}})
    if(confirmBatch) {
        val possibleDuplicates = remember(state.selectedDrafts,state.drafts) {
            batchPotentialDuplicates(state.drafts.filter { it.id in state.selectedDrafts })
        }
        AlertDialog(onDismissRequest={confirmBatch=false},title={Text(stringResource(R.string.group_confirm))},
            text={Column { Text(stringResource(R.string.group_confirm_detail,state.selectedDrafts.size))
                if(possibleDuplicates) {
                    Text(stringResource(R.string.group_batch_duplicates),color=MaterialTheme.colorScheme.error)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(batchDistinct,{batchDistinct=it})
                        Text(stringResource(R.string.group_batch_distinct),Modifier.weight(1f))
                    }
                }
            }},
            confirmButton={TextButton(onClick={confirmBatch=false;onEvent(ReceiptEvent.ConfirmBatch(batchDistinct))},enabled=!possibleDuplicates || batchDistinct) {Text(stringResource(R.string.group_confirm))}},
            dismissButton={TextButton(onClick={confirmBatch=false}) {Text(stringResource(R.string.m_cancel))}})
    }
    if(dateOpen) {
        val selected=runCatching{LocalDate.parse(form.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
        val date=rememberDatePickerState(initialSelectedDateMillis=selected)
        DatePickerDialog(onDismissRequest={dateOpen=false},confirmButton={MoneyTextButton({
            date.selectedDateMillis?.let{update(form.copy(date=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()))};dateOpen=false
        },enabled=date.selectedDateMillis!=null){Text(stringResource(R.string.m_save))}},dismissButton={MoneyTextButton({dateOpen=false}){Text(stringResource(R.string.r_close))}}) {DatePicker(date)}
    }
    discard?.let { id -> AlertDialog(onDismissRequest={discard=null},title={Text(stringResource(R.string.r_discard))},
        confirmButton={MoneyTextButton({onEvent(ReceiptEvent.Discard(id));discard=null}){Text(stringResource(R.string.m_delete))}},
        dismissButton={MoneyTextButton({discard=null}){Text(stringResource(R.string.r_close))}}) }
}
@Composable private fun ReceiptInput(value:String,label:String,tag:String,onChange:(String)->Unit,type:KeyboardType=KeyboardType.Text,hidden:Boolean=false) {
    OutlinedTextField(value,onChange,Modifier.fillMaxWidth().testTag(tag),label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=type),
        visualTransformation=if(hidden) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable private fun ReceiptMessage(text:String,error:Boolean=false) {
    Surface(shape=MaterialTheme.shapes.medium,color=if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor=if(error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer) {Text(text,Modifier.fillMaxWidth().padding(14.dp))}
}
@Composable private fun ReceiptPreview(file:File?) {
    val image by produceState<ImageBitmap?>(null,file) { value=withContext(Dispatchers.IO) {
        file?.takeIf{it.isFile}?.let{runCatching{BitmapFactory.decodeFile(it.path,BitmapFactory.Options().apply{inSampleSize=4})?.asImageBitmap()}.getOrNull()}
    } }
    image?.let{Image(it,stringResource(R.string.r_preview),Modifier.fillMaxWidth().heightIn(max=220.dp))}
}
internal fun receiptErrorResource(reason:ReceiptFailure)=when(reason) {
    ReceiptFailure.UNSUPPORTED->R.string.r_error_unsupported;ReceiptFailure.TOO_LARGE->R.string.r_error_large
    ReceiptFailure.MULTIPLE_FILES->R.string.r_error_multiple;ReceiptFailure.DUPLICATE_CHANGED->R.string.r_error_duplicate
    ReceiptFailure.BANK_NOT_CONFIRMED->R.string.r_bank_blocked;ReceiptFailure.INVALID_DRAFT->R.string.r_error_draft
    ReceiptFailure.UNREADABLE->R.string.r_error_read
}


internal fun batchPotentialDuplicates(rows: List<MoneyReceipt>): Boolean {
    val drafts = rows.map { com.trackermaster.core.data.money.ReceiptDraftCodec.decode(it.draftJson) }
    for (i in rows.indices) for (j in i + 1 until rows.size) {
        val a = drafts[i]; val b = drafts[j]
        if (rows[i].sha256 == rows[j].sha256) return true
        if (a != null && b != null) {
            val refA = ReceiptParser.normalize(a.reference).replace(Regex("[^a-z0-9]"), "")
            val refB = ReceiptParser.normalize(b.reference).replace(Regex("[^a-z0-9]"), "")
            val provider = ReceiptParser.parse(rows[i].text).provider
            if (refA.isNotEmpty() && refA == refB && provider != null && provider == ReceiptParser.parse(rows[j].text).provider) return true
            val amountA = runCatching { ReceiptValidation.amount(a.amount, a.currency) }.getOrNull()
            val amountB = runCatching { ReceiptValidation.amount(b.amount, b.currency) }.getOrNull()
            if (amountA != null && amountA == amountB && a.date.isNotBlank() && a.accountId != null &&
                a.date == b.date && a.accountId == b.accountId && a.kind != null && b.kind != null &&
                (a.kind == ReceiptEntryKind.INCOME) == (b.kind == ReceiptEntryKind.INCOME)) return true
        }
    }
    return false
}
