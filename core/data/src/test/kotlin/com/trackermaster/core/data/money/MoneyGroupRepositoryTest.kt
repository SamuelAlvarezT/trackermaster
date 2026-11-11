package com.trackermaster.core.data.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.MoneyDatabaseCallback
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class MoneyGroupRepositoryTest {
    private lateinit var db: TrackermasterDatabase
    private lateinit var money: MoneyRepository
    private lateinit var receipts: MoneyReceiptRepository
    private lateinit var groups: MoneyGroupRepository
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),TrackermasterDatabase::class.java)
            .addCallback(MoneyDatabaseCallback).allowMainThreadQueries().build()
        money=MoneyRepository(db);receipts=MoneyReceiptRepository(db,money);groups=MoneyGroupRepository(db)
    }
    @After fun close() { db.close() }
    @Test fun groupAcrossDatesAndAccountsKeepsRecordsWhenDeleted()=runTest {
        val one=money.createAccount("One",AccountType.CHECKING,Money(100000,"COP"),2)
        val two=money.createAccount("Two",AccountType.CHECKING,Money(100000,"COP"),2)
        val category=money.createCategory("Food",false)
        val first=receipts.create(ReceiptFile("money/receipts/one.png","image/png","one","one.png"))
        val second=receipts.create(ReceiptFile("money/receipts/two.png","image/png","two","two.png"))
        val a=ReceiptDraft(ReceiptEntryKind.EXPENSE,"10","COP","2026-10-01",one,categoryId=category,confirmed=true,currencyVerified=true)
        val b=ReceiptDraft(ReceiptEntryKind.EXPENSE,"20","COP","2026-10-02",two,categoryId=category,confirmed=true,currencyVerified=true)
        receipts.updateDraft(first.id,a);receipts.updateDraft(second.id,b)
        val id=groups.create("Trip",listOf("receipt:${first.id}","receipt:${second.id}"),MoneyGroupRules())
        assertEquals(2,groups.groups.first().single().memberKeys.size)
        receipts.confirmBatch(listOf(first.id to a,second.id to b))
        val before=money.snapshot().accounts.map { it.total }
        groups.delete(id)
        assertTrue(groups.groups.first().isEmpty());assertEquals(before,money.snapshot().accounts.map { it.total })
        assertEquals("CONFIRMED",receipts.get(first.id).stage);assertEquals("CONFIRMED",receipts.get(second.id).stage)
    }
    @Test fun confirmedMovementCanJoinAnExistingDraftGroup()=runTest {
        val a=money.createAccount("One",AccountType.CHECKING,Money(100000,"COP"),2)
        val b=money.createAccount("Two",AccountType.CHECKING,Money(100000,"COP"),2)
        val category=money.createCategory("Food",false)
        val first=receipts.create(ReceiptFile("money/receipts/first.png","image/png","first","first.png"))
        val second=receipts.create(ReceiptFile("money/receipts/second.png","image/png","second","second.png"))
        val form=ReceiptDraft(ReceiptEntryKind.EXPENSE,"10","COP","2026-10-01",a,categoryId=category,confirmed=true,currencyVerified=true)
        receipts.updateDraft(first.id,form); val movement=receipts.confirm(first.id,form)
        receipts.updateDraft(second.id,ReceiptDraft(date="2026-10-02",accountId=b))
        val group=groups.create("Mixed",listOf("receipt:${second.id}"),MoneyGroupRules())
        groups.add(group,listOf("movement:$movement"),MoneyGroupRules())
        assertEquals(setOf("receipt:${first.id}","receipt:${second.id}"),groups.groups.first().single().memberKeys.toSet())
        assertTrue(runCatching { groups.add(group,listOf("movement:$movement"),MoneyGroupRules()) }.isFailure)
        groups.delete(group)
        assertEquals("CONFIRMED",receipts.get(first.id).stage);assertEquals("READING",receipts.get(second.id).stage)
        assertEquals(1,db.moneyDao().readLedger().sumOf { it.movements.size })
    }
    @Test fun rulesAreIndependentAndDoNotAlterExistingGroups()=runTest {
        val a=money.createAccount("One",AccountType.CHECKING,Money(100000,"COP"),2)
        val one=receipts.create(ReceiptFile("money/receipts/a.png","image/png","a","a.png"))
        val two=receipts.create(ReceiptFile("money/receipts/b.png","image/png","b","b.png"))
        receipts.updateDraft(one.id,ReceiptDraft(date="2026-10-01",accountId=a))
        receipts.updateDraft(two.id,ReceiptDraft(date="2026-10-02",accountId=a))
        val keys=listOf("receipt:${one.id}","receipt:${two.id}")
        assertTrue(runCatching { groups.create("Rejected",keys,MoneyGroupRules(sameDate=true)) }.isFailure)
        val group=groups.create("Allowed",keys,MoneyGroupRules(sameAccount=true))
        assertEquals(2,groups.groups.first().single().memberKeys.size)
        val third=receipts.create(ReceiptFile("money/receipts/c.png","image/png","c","c.png"))
        receipts.updateDraft(third.id,ReceiptDraft(date="2026-10-01",accountId=a))
        assertTrue(runCatching { groups.add(group,listOf("receipt:${third.id}"),MoneyGroupRules(sameDate=true)) }.isFailure)
        assertEquals(2,groups.groups.first().single().memberKeys.size)
        assertNotNull(db.moneyGroupDao().get(group))
        receipts.discard(one.id)
        assertEquals(1,groups.groups.first().single().memberKeys.size)
    }
}
