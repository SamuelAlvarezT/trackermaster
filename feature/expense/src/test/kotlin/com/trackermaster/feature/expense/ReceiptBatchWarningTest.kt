package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.ReceiptDraftCodec
import com.trackermaster.core.domain.money.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReceiptBatchWarningTest {
    @Test fun sameFileOrEquivalentAmountProducesWarning() {
        val draft=ReceiptDraft(kind=ReceiptEntryKind.EXPENSE,amount="10",currency="COP",date="2026-10-04",accountId=1)
        fun row(id:String,hash:String,d:ReceiptDraft)=MoneyReceipt(id,"path","image/png",hash,"receipt",draftJson=ReceiptDraftCodec.encode(d))
        val a=row("a","one",draft)
        assertTrue(batchPotentialDuplicates(listOf(a,row("b","one",draft.copy(amount="20")))))
        assertTrue(batchPotentialDuplicates(listOf(a,row("b","two",draft.copy(amount="10.00")))))
        assertFalse(batchPotentialDuplicates(listOf(a,row("b","two",draft.copy(amount="20")))))
    }
}
