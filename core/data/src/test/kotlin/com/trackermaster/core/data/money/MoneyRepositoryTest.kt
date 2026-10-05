package com.trackermaster.core.data.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.MoneyDatabaseCallback
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.ExpenseCategoryEntity
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyRepositoryTest {
    private lateinit var db: TrackermasterDatabase
    private lateinit var repo: MoneyRepository
    private val date = LocalDate.of(2026, 10, 4)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrackermasterDatabase::class.java)
            .addCallback(MoneyDatabaseCallback).allowMainThreadQueries().build()
        repo = MoneyRepository(db)
    }
    @After fun tearDown() { db.close() }
    private suspend fun account(name: String = "Nequi", amount: Long = 10000, currency: String = "COP", space: Long = 2) =
        repo.createAccount(name, AccountType.CHECKING, Money(amount, currency), space)

    @Test fun freshDatabaseSeedsSpacesWithoutCreatingFakeMoney() = runTest {
        val id = account()
        assertNotNull(db.moneyDao().space(5))
        assertEquals(Money(10000, "COP"), repo.snapshot().accounts.single { it.account.id == id }.total)
    }
    @Test fun transferUpdatesBothAccountsAndRepeatedRequestDoesNotDuplicate() = runTest {
        val source = account()
        val target = account("Savings", 0, space = 4)
        repeat(2) { repo.transfer(source, target, Money(2500, "COP"), date = date, requestId = "request-1") }
        val snapshot = repo.snapshot()
        assertEquals(7500L, snapshot.accounts.single { it.account.id == source }.total.minorUnits)
        assertEquals(2500L, snapshot.accounts.single { it.account.id == target }.total.minorUnits)
        assertEquals(Money(10000, "COP"), snapshot.currencies.single())
        assertEquals(2, db.moneyDao().transferMovements("request-1").size)
    }
    @Test fun reusedTransferRequestWithDifferentAmountIsRejected() = runTest {
        val source = account(); val target = account("Savings", 0)
        repo.transfer(source, target, Money(1000, "COP"), date = date, requestId = "same")
        assertFailure { repo.transfer(source, target, Money(2000, "COP"), date = date, requestId = "same") }
        assertEquals(2, db.moneyDao().transferMovements("same").size)
    }
    @Test fun insufficientTransferLeavesNoPartialMovement() = runTest {
        val source = account(amount = 100); val target = account("Savings", 0)
        assertFailure { repo.transfer(source, target, Money(101, "COP"), requestId = "failed") }
        assertNull(db.moneyDao().transfer("failed"))
        assertTrue(db.moneyDao().transferMovements("failed").isEmpty())
        assertEquals(Money(100, "COP"), repo.snapshot().currencies.single())
    }
    @Test fun crossCurrencyTransferRequiresExplicitDestinationAmount() = runTest {
        val source = account(); val target = account("Travel", 0, "USD", 5)
        assertFailure { repo.transfer(source, target, Money(4000, "COP")) }
        repo.transfer(source, target, Money(4000, "COP"), Money(100, "USD"), requestId = "fx")
        assertEquals(listOf(Money(6000, "COP"), Money(100, "USD")), repo.snapshot().currencies)
    }
    @Test fun pocketReserveMovesSpaceAllocationWithoutChangingAccountTotal() = runTest {
        val source = account()
        val pocket = repo.createPocket(source, 3, "Business")
        repo.transfer(source, source, Money(3000, "COP"), targetPocketId = pocket, requestId = "reserve")
        val snapshot = repo.snapshot()
        assertEquals(Money(10000, "COP"), snapshot.accounts.single().total)
        assertEquals(7000L, snapshot.accounts.single().unallocated.minorUnits)
        assertEquals(3000L, snapshot.pockets.single().balance.minorUnits)
    }
    @Test fun spendingReservedMoneyFromUnallocatedAccountRollsBack() = runTest {
        val source = account()
        val pocket = repo.createPocket(source, 3, "Business")
        repo.transfer(source, source, Money(9000, "COP"), targetPocketId = pocket)
        val category = db.expenseDao().insertCategory(ExpenseCategoryEntity(name = "Food", isIncome = false, colorArgb = 0))
        assertFailure { repo.record(MoneyMovement(accountId = source, amount = Money(2000, "COP"),
            direction = MoneyDirection.EXPENSE, date = date, spaceId = 2, categoryId = category)) }
        assertEquals(10000L, repo.snapshot().accounts.single().total.minorUnits)
        assertEquals(2, db.moneyDao().readLedger().single().movements.size)
    }
    @Test fun deletingTransferRestoresBothBalances() = runTest {
        val source = account(); val target = account("Savings", 0)
        repo.transfer(source, target, Money(2500, "COP"), requestId = "delete")
        repo.deleteTransfer("delete")
        assertEquals(10000L, repo.snapshot().accounts.single { it.account.id == source }.total.minorUnits)
        assertEquals(0L, repo.snapshot().accounts.single { it.account.id == target }.total.minorUnits)
    }
    @Test fun currencyAndOpeningBalanceAreImmutableAfterMovements() = runTest {
        val source = account(); val target = account("Savings", 0)
        repo.transfer(source, target, Money(1000, "COP"))
        assertFailure { repo.updateAccountDetails(source, "Nequi", AccountType.CHECKING, Money(10000, "USD")) }
        assertFailure { repo.updateAccountDetails(source, "Nequi", AccountType.CHECKING, Money(20000, "COP")) }
        assertEquals("COP", db.moneyDao().account(source)?.currencyCode)
    }
    @Test fun classificationKeepsMoneyAndArchiveKeepsHistory() = runTest {
        val source = account()
        repo.classifyAccount(source, 3)
        repo.archiveAccount(source, true)
        assertEquals(MoneySpaceBalance(3, Money(10000, "COP")), repo.snapshot().spaces.single())
        assertFailure { repo.createPocket(source, 2, "Cannot use archived account") }
        assertEquals(Money(10000, "COP"), repo.snapshot().currencies.single())
    }
    @Test fun mismatchedCategoryDoesNotInsertAnything() = runTest {
        val source = account()
        val category = db.expenseDao().insertCategory(ExpenseCategoryEntity(name = "Salary", isIncome = true, colorArgb = 0))
        assertFailure { repo.record(MoneyMovement(accountId = source, amount = Money(100, "COP"),
            direction = MoneyDirection.EXPENSE, date = date, spaceId = 2, categoryId = category)) }
        assertTrue(db.moneyDao().readLedger().single().movements.isEmpty())
    }
    @Test fun editingNameOrNoteRetainsOriginalLegacyMoneyTrace() = runTest {
        val source = account(amount = 10000)
        db.openHelper.writableDatabase.execSQL("UPDATE accounts SET initialBalance = 100.005 WHERE id = ?", arrayOf(source))
        repo.updateAccountDetails(source, "Renamed", AccountType.CHECKING, Money(10000, "COP"))
        assertEquals(100.005, requireNotNull(db.moneyDao().account(source)).initialBalance, 0.0)
        val category = db.expenseDao().insertCategory(ExpenseCategoryEntity(name = "Food", isIncome = false, colorArgb = 0))
        val movement = MoneyMovement(accountId = source, amount = Money(100, "COP"), direction = MoneyDirection.EXPENSE,
            date = date, spaceId = 2, categoryId = category)
        val id = repo.record(movement)
        db.openHelper.writableDatabase.execSQL("UPDATE transactions SET amount = 1.005 WHERE id = ?", arrayOf(id))
        repo.record(movement.copy(id = id, note = "New note"))
        assertEquals(1.005, requireNotNull(db.moneyDao().movement(id)).amount, 0.0)
    }
    @Test fun overflowAfterWriteRollsBackWholeTransfer() = runTest {
        val source = account(amount = 1); val target = account("Maximum", Long.MAX_VALUE, "USD")
        assertFailure { repo.transfer(source, target, Money(1, "COP"), Money(1, "USD"), requestId = "overflow") }
        assertNull(db.moneyDao().transfer("overflow"))
        assertTrue(db.moneyDao().transferMovements("overflow").isEmpty())
        assertEquals(1L, db.moneyDao().account(source)?.initialBalanceMinor)
    }
    private suspend fun assertFailure(block: suspend () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return } catch (_: ArithmeticException) { return }
        fail("Expected the operation to be rejected")
    }
}
