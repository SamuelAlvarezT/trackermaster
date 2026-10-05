package com.trackermaster.core.data.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.MoneyDatabaseCallback
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyReceiptRepositoryTest {
    private lateinit var db:TrackermasterDatabase
    private lateinit var money:MoneyRepository
    private lateinit var receipts:MoneyReceiptRepository
    @Before fun before() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),TrackermasterDatabase::class.java)
            .addCallback(MoneyDatabaseCallback).allowMainThreadQueries().build()
        money=MoneyRepository(db);receipts=MoneyReceiptRepository(db,money)
    }
    @After fun after(){db.close()}
    private suspend fun account(currency:String="COP",space:Long=2)=money.createAccount("Account",AccountType.CHECKING,Money(100000,currency),space)
    private suspend fun source(hash:String="a",text:String="Nequi\nEnviaste\nMonto COP 10,00\nFecha 04/10/2026\nReferencia R123"):MoneyReceipt {
        val row=receipts.create(ReceiptFile("money/receipts/$hash.png","image/png",hash,"proof.png"))
        return receipts.analyzed(row.id,text,null,false)
    }
    private suspend fun form(account:Long)=ReceiptDraft(ReceiptEntryKind.EXPENSE,"10","COP","2026-10-04",account,
        categoryId=money.createCategory("Expense",false),reference="R123",confirmed=true)
    private suspend fun failure(action:suspend()->Unit):IllegalArgumentException=try{action();error("Expected rejected write")}catch(e:IllegalArgumentException){e}
    @Test fun importingAndEditingDraftNeverChangesBalance()=runTest {
        val a=account();val row=source();receipts.updateDraft(row.id,form(a))
        assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits)
        assertTrue(db.moneyDao().readLedger().single().movements.isEmpty())
        assertEquals("REVIEW",receipts.get(row.id).stage)
    }
    @Test fun confirmedReceiptIsAtomicIdempotentAndKeepsPortableAttachment()=runTest {
        val a=account();val row=source();val form=form(a)
        val id=receipts.confirm(row.id,form);assertEquals(id,receipts.confirm(row.id,form))
        assertEquals("CONFIRMED",receipts.get(row.id).stage)
        assertEquals(99000L,money.snapshot().accounts.single().total.minorUnits)
        assertEquals(row.path,db.moneyDao().movement(id)!!.imageUri)
        assertEquals(MoneyWriteFailure.REQUEST_CONFLICT,(failure{receipts.confirm(row.id,form.copy(amount="20"))} as MoneyWriteException).reason)
    }
    @Test fun fileReferenceAndSimilarMatchesRequireSpecificAcknowledgement()=runTest {
        val a=account();val form=form(a);val first=source();receipts.confirm(first.id,form)
        val second=source();val matches=receipts.duplicates(second.id,form)
        assertEquals(ReceiptDuplicateReason.entries.toSet(),matches.map{it.reason}.toSet())
        assertEquals(ReceiptFailure.DUPLICATE_CHANGED,(failure{receipts.confirm(second.id,form)} as ReceiptException).reason)
        receipts.confirm(second.id,form.copy(duplicateAcknowledgement=matches.fingerprint()))
        assertEquals(98000L,money.snapshot().accounts.single().total.minorUnits)
    }
    @Test fun duplicateWarningIsRecheckedInsideCommitTransaction()=runTest {
        val a=account();val form=form(a);val first=source();receipts.confirm(first.id,form)
        val pending=source("b");val acknowledgement=receipts.duplicates(pending.id,form).fingerprint()
        val third=source("c");val thirdMatches=receipts.duplicates(third.id,form)
        receipts.confirm(third.id,form.copy(duplicateAcknowledgement=thirdMatches.fingerprint()))
        assertEquals(ReceiptFailure.DUPLICATE_CHANGED,(failure{receipts.confirm(pending.id,form.copy(duplicateAcknowledgement=acknowledgement))} as ReceiptException).reason)
        assertEquals("REVIEW",receipts.get(pending.id).stage)
        assertEquals(2,db.moneyDao().readLedger().single().movements.size)
    }
    @Test fun pendingFailedAndUnconfirmedReceiptsCannotChangeLedger()=runTest {
        val a=account();val form=form(a)
        listOf("Nequi\nPendiente", "Nequi\nFallida").forEach { text ->
            val row=source(text=text)
            assertEquals(ReceiptFailure.BANK_NOT_CONFIRMED,(failure{receipts.confirm(row.id,form)} as ReceiptException).reason)
        }
        val row=source();failure{receipts.confirm(row.id,form.copy(confirmed=false))}
        assertEquals(100000L,money.snapshot().accounts.single().total.minorUnits)
    }
    @Test fun invalidFundingRollsBackAndRetainsRecoverableDraft()=runTest {
        val a=account();val other=account();val pocket=money.createPocket(other,4,"Reserve")
        val row=source();val draft=form(a).copy(pocketId=pocket);receipts.updateDraft(row.id,draft)
        failure{receipts.confirm(row.id,draft)}
        assertEquals(draft,ReceiptDraftCodec.decode(receipts.get(row.id).draftJson))
        assertEquals("REVIEW",receipts.get(row.id).stage)
        assertTrue(db.moneyDao().readLedger().all{it.movements.isEmpty()})
    }
    @Test fun transferReceiptKeepsBothAttachmentsThroughEditDeleteAndUndo()=runTest {
        val a=account();val b=account(space=3);val row=source()
        val form=form(a).copy(kind=ReceiptEntryKind.TRANSFER,targetAccountId=b)
        val id=receipts.confirm(row.id,form)
        assertEquals(2,db.moneyDao().transferMovements(row.id).size)
        assertTrue(db.moneyDao().transferMovements(row.id).all{it.imageUri==row.path})
        money.editTransfer(row.id,a,b,Money(2000,"COP"),Money(2000,"COP"),null,null,LocalDate.parse(form.date),"Edit")
        assertTrue(db.moneyDao().transferMovements(row.id).all{it.imageUri==row.path})
        val candidate=source();assertTrue(receipts.duplicates(candidate.id,form).any{it.reason==ReceiptDuplicateReason.FILE})
        val currentId=db.moneyDao().transferMovements(row.id).first().id
        val token=money.deleteEntry(currentId);assertTrue(receipts.duplicates(candidate.id,form).isEmpty())
        money.restore(token);assertTrue(receipts.duplicates(candidate.id,form).any{it.reason==ReceiptDuplicateReason.FILE})
        assertEquals("CONFIRMED",receipts.get(row.id).stage)
        assertTrue(id>0)
    }
    @Test fun crossCurrencyTransferRequiresExplicitReceivedAmountWithoutMixingBalances()=runTest {
        val a=account();val b=account("USD",5);val row=source()
        val draft=form(a).copy(kind=ReceiptEntryKind.TRANSFER,targetAccountId=b)
        failure{receipts.confirm(row.id,draft)}
        receipts.confirm(row.id,draft.copy(receivedAmount="2.50"))
        assertEquals(99000L,money.snapshot().accounts.first{it.account.id==a}.total.minorUnits)
        assertEquals(100250L,money.snapshot().accounts.first{it.account.id==b}.total.minorUnits)
        assertEquals(listOf("COP","USD"),db.moneyDao().readLedger().moneyMovements().filter{it.transferId==row.id}.map{it.amount.currencyCode}.sorted())
    }
    @Test fun incomeUpdatesChosenAccountAndDiscardDoesNotDeleteConfirmedEvidence()=runTest {
        val a=account();val row=source(text="Nequi\nRecibiste\nMonto COP 10,00")
        val draft=form(a).copy(kind=ReceiptEntryKind.INCOME,categoryId=money.createCategory("Income",true))
        receipts.confirm(row.id,draft);receipts.discard(row.id)
        assertEquals(101000L,money.snapshot().accounts.single().total.minorUnits)
        assertEquals("CONFIRMED",receipts.get(row.id).stage)
    }
}
