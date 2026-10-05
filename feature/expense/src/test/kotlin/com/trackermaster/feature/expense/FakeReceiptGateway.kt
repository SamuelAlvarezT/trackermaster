package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.*
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.domain.model.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeReceiptGateway:ReceiptGateway {
    override val workspace=MutableStateFlow(MoneyPreviewData.workspace())
    override val drafts=MutableStateFlow<List<MoneyReceipt>>(emptyList())
    override val preferences=MutableStateFlow(MoneyPreferences())
    override val settings=MutableStateFlow(AppSettings(localeTag="en"))
    var text="Nequi\nEnviaste\nMonto COP 50.000,00\nFecha 04/10/2026\nReferencia R123"
    var unreadable=false
    var failDuplicates=false
    var failInitialize=false
    var failSave=false
    var hold:CompletableDeferred<Unit>?=null
    var matches=emptyList<ReceiptDuplicate>()
    val confirmed=mutableListOf<Pair<String,ReceiptDraft>>()
    private val rows=linkedMapOf<String,MoneyReceipt>()
    private var serial=0
    override suspend fun initialize(){if(failInitialize) error("Load failed")}
    override suspend fun import(source:ReceiptSource):MoneyReceipt {
        val id="receipt-${++serial}"
        val row=MoneyReceipt(id,"money/receipts/a.txt","text/plain","hash","proof.txt")
        rows[id]=row;publish();return row
    }
    override suspend fun read(id:String):MoneyReceipt {
        val body=if(unreadable) "" else text
        val row=rows.getValue(id).copy(text=body,stage=if(unreadable) "UNREADABLE" else "REVIEW",draftJson=ReceiptDraftCodec.encode(ReceiptDraft.from(ReceiptParser.parse(body))))
        rows[id]=row;publish();return row
    }
    override suspend fun get(id:String)=rows.getValue(id)
    override suspend fun persist(id:String,draft:ReceiptDraft) {
        val old=rows.getValue(id);val changed=old.copy(draftJson=ReceiptDraftCodec.encode(draft))
        if(changed!=old){rows[id]=changed;publish()}
    }
    override suspend fun duplicates(id:String,draft:ReceiptDraft):List<ReceiptDuplicate> {
        if(failDuplicates) error("Read failed")
        return matches
    }
    override suspend fun confirm(id:String,draft:ReceiptDraft) {
        confirmed+=id to draft;hold?.await();if(failSave) error("Write failed")
        rows[id]=rows.getValue(id).copy(stage="CONFIRMED",draftJson=ReceiptDraftCodec.encode(draft));publish()
    }
    override suspend fun discard(id:String){rows.remove(id);publish()}
    override suspend fun privacy(hidden:Boolean){preferences.value=preferences.value.copy(hideAmounts=hidden)}
    private fun publish(){drafts.value=rows.values.filter{it.stage!="CONFIRMED"}}
}
