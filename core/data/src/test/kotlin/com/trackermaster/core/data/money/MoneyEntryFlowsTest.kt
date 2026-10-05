package com.trackermaster.core.data.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.MoneyDatabaseCallback
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.ExpenseCategoryEntity
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyEntryFlowsTest {
    private lateinit var db: TrackermasterDatabase
    private lateinit var repo: MoneyRepository
    private val day = LocalDate.of(2026, 10, 4)
    @Before fun before() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrackermasterDatabase::class.java)
            .addCallback(MoneyDatabaseCallback).allowMainThreadQueries().build()
        repo = MoneyRepository(db)
    }
    @After fun after() { db.close() }
    private suspend fun account(name: String = "Nequi", initial: Long = 10000, currency: String = "COP", space: Long = 2) =
        repo.createAccount(name, AccountType.CHECKING, Money(initial, currency), space)
    private suspend fun expense(account: Long, amount: Long = 1000, pocket: Long? = null, space: Long = 2, request: String = "entry") : MoneyMovement {
        repo.ensureCategories()
        val category = db.moneyDao().categories().first { !it.isIncome }.id
        return MoneyMovement(accountId = account, amount = Money(amount, "COP"), direction = MoneyDirection.EXPENSE,
            date = day, spaceId = space, categoryId = category, pocketId = pocket, note = "Original", attachmentPath = "receipt.jpg", entryRequestId = request)
    }
    private suspend fun failure(action: suspend () -> Unit): Exception = try { action(); error("Expected a rejected write") } catch (e: IllegalArgumentException) { e }

    @Test fun ordinaryRequestIsIdempotentAndConflictingPayloadIsRejected() = runTest {
        val movement = expense(account())
        val id = repo.record(movement)
        assertEquals(id, repo.record(movement))
        val error = failure { repo.record(movement.copy(amount = Money(2000, "COP"))) }
        assertEquals(MoneyWriteFailure.REQUEST_CONFLICT, (error as MoneyWriteException).reason)
        assertEquals(1, db.moneyDao().readLedger().single().movements.size)
    }
    @Test fun deleteAndUndoRestoreExactRawRecordAndReceipt() = runTest {
        val id = repo.record(expense(account()))
        val original = db.moneyDao().movement(id)
        val token = repo.deleteEntry(id)
        assertNull(db.moneyDao().movement(id))
        repo.restore(token)
        assertEquals(original, db.moneyDao().movement(id))
    }
    @Test fun editingOnlyNotePreservesLegacyDecimalAndOriginalTime() = runTest {
        val movement = expense(account()); val id = repo.record(movement)
        val original = db.moneyDao().movement(id)!!.let { it.copy(amount = 10.005, dateEpoch = it.dateEpoch + 12345678) }
        db.moneyDao().updateMovement(original)
        repo.record(movement.copy(id = id, note = "Updated note"))
        val updated = db.moneyDao().movement(id)!!
        assertEquals(10.005, updated.amount, 0.0); assertEquals(original.dateEpoch, updated.dateEpoch)
        assertEquals("receipt.jpg", updated.imageUri); assertEquals("Updated note", updated.note)
    }
    @Test fun deletingEitherTransferLegDeletesAndRestoresTheCompletePair() = runTest {
        val source = account(); val target = account("Business", 0, space = 3)
        repo.transfer(source, target, Money(2000, "COP"), date = day, requestId = "pair")
        val original = db.moneyDao().transferMovements("pair")
        val token = repo.deleteEntry(original.last().id)
        assertTrue(db.moneyDao().transferMovements("pair").isEmpty())
        assertNull(db.moneyDao().transfer("pair"))
        repo.restore(token)
        assertEquals(original, db.moneyDao().transferMovements("pair"))
        assertEquals(Money(10000, "COP"), repo.snapshot().currencies.single())
    }
    @Test fun deletingAlreadySpentPocketFundingRollsBack() = runTest {
        val source = account(); val pocket = repo.createPocket(source, 4, "Savings")
        repo.transfer(source, source, Money(2000, "COP"), targetPocketId = pocket, date = day, requestId = "reserve")
        repo.record(expense(source, 1000, pocket, 4))
        val rows = db.moneyDao().transferMovements("reserve")
        assertEquals(MoneyWriteFailure.INSUFFICIENT_FUNDS, (failure { repo.deleteEntry(rows.first().id) } as MoneyWriteException).reason)
        assertEquals(rows, db.moneyDao().transferMovements("reserve"))
        assertEquals(1000L, repo.snapshot().pockets.single().balance.minorUnits)
    }
    @Test fun editingMovementMovesAccountAtomicallyAndKeepsReceiptAndRequest() = runTest {
        val source = account(); val target = account("Business", 5000, space = 3)
        val movement = expense(source); val id = repo.record(movement)
        repo.record(movement.copy(id = id, accountId = target, spaceId = 3))
        val saved = db.moneyDao().movement(id)!!
        assertEquals(target, saved.accountId); assertEquals("entry", saved.entryRequestId); assertEquals("receipt.jpg", saved.imageUri)
        assertEquals(10000L, repo.snapshot().accounts.first { it.account.id == source }.total.minorUnits)
        assertEquals(4000L, repo.snapshot().accounts.first { it.account.id == target }.total.minorUnits)
    }
    @Test fun invalidPocketEditKeepsOriginalMovement() = runTest {
        val source = account(); val target = account("Other")
        val pocket = repo.createPocket(target, 4, "Reserve")
        val movement = expense(source); val id = repo.record(movement); val original = db.moneyDao().movement(id)
        failure { repo.record(movement.copy(id = id, pocketId = pocket, spaceId = 4)) }
        assertEquals(original, db.moneyDao().movement(id))
    }
    @Test fun invalidTransferEditRestoresOriginalPair() = runTest {
        val source = account(); val target = account("Other", 0)
        repo.transfer(source, target, Money(2000, "COP"), date = day, requestId = "edit")
        val rows = db.moneyDao().transferMovements("edit")
        failure { repo.editTransfer("edit", source, target, Money(10001, "COP"), Money(10001, "COP"), null, null, day, "Changed") }
        assertEquals(rows, db.moneyDao().transferMovements("edit"))
        repo.editTransfer("edit", source, target, Money(3000, "COP"), Money(3000, "COP"), null, null, day, "Changed")
        assertEquals(listOf(3000L, 3000L), db.moneyDao().transferMovements("edit").map { it.amountMinor })
    }
    @Test fun undoAfterCurrencyChangeRejectsWithoutReinterpretingAmount() = runTest {
        val source = account(); val token = repo.deleteEntry(repo.record(expense(source)))
        repo.updateAccountDetails(source, "Nequi", AccountType.CHECKING, Money(10000, "USD"))
        assertEquals(MoneyWriteFailure.CURRENCY_CHANGED, (failure { repo.restore(token) } as MoneyWriteException).reason)
        assertTrue(db.moneyDao().readLedger().single().movements.isEmpty())
    }
    @Test fun undoUsesCurrentSpaceAfterReclassification() = runTest {
        val source = account(); val id = repo.record(expense(source)); val token = repo.deleteEntry(id)
        repo.classifyAccount(source, 3); repo.restore(token)
        assertEquals(3L, db.moneyDao().movement(id)!!.spaceId)
        assertEquals(3L, repo.snapshot().accounts.single().account.spaceId)
    }
    @Test fun categorySeedingIsStableAndNeverRenamesUserData() = runTest {
        account(); repo.ensureCategories(); repo.ensureCategories()
        assertEquals(6, db.moneyDao().categories().size)
        assertEquals(6, db.moneyDao().categories().mapNotNull { it.builtinKey }.distinct().size)
        val id = repo.createCategory("My category", false)
        repo.ensureCategories()
        assertEquals("My category", db.moneyDao().category(id)!!.name)
        assertNull(db.moneyDao().category(id)!!.builtinKey)
    }
    @Test fun invalidAccountSpaceDoesNotPartiallyRenameAccount() = runTest {
        val id = account()
        failure { repo.saveAccount(MoneyAccount(id, "Renamed", AccountType.CASH, Money(10000, "COP"), 999)) }
        assertEquals("Nequi", db.moneyDao().account(id)!!.name)
        assertEquals(2L, db.moneyDao().account(id)!!.spaceId)
    }
}
