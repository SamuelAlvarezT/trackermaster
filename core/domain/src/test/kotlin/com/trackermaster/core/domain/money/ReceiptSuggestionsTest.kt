package com.trackermaster.core.domain.money

import com.trackermaster.core.domain.model.AccountType
import org.junit.Assert.*
import org.junit.Test

class ReceiptSuggestionsTest {
    private fun workspace(vararg names: String): MoneyWorkspaceData {
        val accounts=names.mapIndexed { index,name -> MoneyAccount(index.toLong()+1,name,AccountType.CHECKING,Money(10000,"COP"),2) }
        return MoneyWorkspaceData(MoneyLedgerData(MoneyLedger.snapshot(accounts,emptyList(),emptyList()),emptyList()),emptyList(),emptyList(),emptyList())
    }
    private fun suggest(text: String, data: MoneyWorkspaceData): Long? {
        val analysis=ReceiptParser.parse(text)
        return ReceiptSuggestions.account(analysis,text,data,ReceiptDraft.from(analysis))
    }
    @Test fun uniqueBrandedAccountCanBeSuggestedWithoutConfirming() {
        assertEquals(1L,suggest("Nequi\nEnviaste\nMonto COP 10,00",workspace("Nequi personal")))
        assertEquals(1L,suggest("Nu\nEnviaste\nMonto COP 10,00\nPara: cuenta Nequi",workspace("Nu","Nequi")))
    }
    @Test fun ambiguousOrRecipientOnlyBankNeverSelectsAnAccount() {
        assertNull(suggest("Nequi\nEnviaste\nMonto COP 10,00",workspace("Nequi uno","Nequi dos")))
        assertNull(suggest("Transferencia\nEnviaste\nMonto COP 10,00\nBanco destino: Nequi",workspace("Nequi")))
        assertNull(suggest("Nequi\nTransferencia\nMonto COP 10,00",workspace("Nequi")))
    }
}
