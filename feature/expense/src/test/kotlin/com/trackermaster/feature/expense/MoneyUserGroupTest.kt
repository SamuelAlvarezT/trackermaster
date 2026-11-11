package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.ReceiptDraftCodec
import com.trackermaster.core.domain.money.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyUserGroupTest {
    @Test fun groupCountsDraftsSeparatelyAndTotalsConfirmedByCurrency() {
        val old=MoneyPreviewData.workspace(LocalDate.of(2026,10,10))
        val extra=MoneyMovement(8,3,Money(10000,"USD"),MoneyDirection.EXPENSE,LocalDate.of(2026,9,1),5,categoryId=1)
        val movements=old.ledger.movements+extra
        val snapshot=MoneyLedger.snapshot(old.ledger.snapshot.accounts.map { it.account },old.ledger.snapshot.pockets.map { it.pocket },movements)
        val data=old.copy(ledger=MoneyLedgerData(snapshot,movements))
        val draft=MoneyReceipt("draft","path","image/png","hash","draft",draftJson=ReceiptDraftCodec.encode(ReceiptDraft(amount="500")))
        val group=MoneyGroup("g","Trip",0,listOf("movement:1","movement:8","transfer:business","receipt:draft"))
        val view=moneyUserGroups(data,listOf(group),listOf(draft)).single()
        assertEquals(3,view.rows.size);assertEquals(1,view.drafts.size)
        assertEquals(listOf(Money(-1890000,"COP"),Money(-10000,"USD")),view.totals)
    }
}
