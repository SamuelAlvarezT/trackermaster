package com.trackermaster.core.domain.money

import com.trackermaster.core.domain.model.AccountType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MoneyLedgerTest {
    private val today = LocalDate.of(2026, 10, 4)
    private fun account(id: Long, amount: Long, currency: String = "COP", space: Long = 2) =
        MoneyAccount(id, "Account $id", AccountType.CHECKING, Money(amount, currency), space)
    private fun movement(id: Long, account: Long, amount: Long, type: MoneyDirection, currency: String = "COP",
                         space: Long = 2, pocket: Long? = null, transfer: String? = null, date: LocalDate = today) =
        MoneyMovement(id, account, Money(amount, currency), type, date, space, pocketId = pocket, transferId = transfer)

    @Test fun balanceIncludesInitialIncomeAndExpensesWithoutMixingCurrencies() {
        val snapshot = MoneyLedger.snapshot(listOf(account(1, 10000), account(2, 500, "USD")), emptyList(),
            listOf(movement(1, 1, 1500, MoneyDirection.INCOME), movement(2, 1, 400, MoneyDirection.EXPENSE)))
        assertEquals(listOf(Money(11100, "COP"), Money(500, "USD")), snapshot.currencies)
    }
    @Test fun reservingPocketDoesNotDuplicateGlobalMoney() {
        val movements = listOf(movement(1, 1, 3000, MoneyDirection.EXPENSE, transfer = "reserve"),
            movement(2, 1, 3000, MoneyDirection.INCOME, space = 3, pocket = 1, transfer = "reserve"))
        val snapshot = MoneyLedger.snapshot(listOf(account(1, 10000)), listOf(MoneyPocket(1, 1, 3, "Business")), movements)
        assertEquals(listOf(Money(10000, "COP")), snapshot.currencies)
        assertEquals(7000L, snapshot.accounts.single().unallocated.minorUnits)
        assertEquals(3000L, snapshot.pockets.single().balance.minorUnits)
        assertEquals(listOf(MoneySpaceBalance(2, Money(7000, "COP")), MoneySpaceBalance(3, Money(3000, "COP"))), snapshot.spaces)
    }
    @Test fun transfersDoNotInflateOperationalIncomeOrExpenses() {
        val rows = listOf(movement(1, 1, 3000, MoneyDirection.EXPENSE, transfer = "pair"),
            movement(2, 2, 3000, MoneyDirection.INCOME, transfer = "pair"), movement(3, 1, 200, MoneyDirection.EXPENSE))
        val totals = MoneyLedger.periodTotals(rows, "COP", today, today)
        assertEquals(0L, totals.income.minorUnits)
        assertEquals(200L, totals.expenses.minorUnits)
        assertEquals(-200L, totals.net.minorUnits)
    }
    @Test fun periodAndSpaceFilteringIncludesBothEndpoints() {
        val rows = listOf(movement(1, 1, 100, MoneyDirection.INCOME, date = today.minusDays(1)),
            movement(2, 1, 200, MoneyDirection.INCOME), movement(3, 1, 900, MoneyDirection.INCOME, space = 3),
            movement(4, 1, 700, MoneyDirection.INCOME, date = today.plusDays(1)))
        assertEquals(300L, MoneyLedger.periodTotals(rows, "COP", today.minusDays(1), today, 2).income.minorUnits)
    }
    @Test fun reversedRangeIsRejected() { assertThrows(IllegalArgumentException::class.java) {
        MoneyLedger.periodTotals(emptyList(), "COP", today, today.minusDays(1)) } }
    @Test fun accountOverflowNeverWrapsAround() { assertThrows(ArithmeticException::class.java) {
        MoneyLedger.snapshot(listOf(account(1, Long.MAX_VALUE)), emptyList(), listOf(movement(1, 1, 1, MoneyDirection.INCOME))) } }
    @Test fun archivedAccountStillCountsInNetAssets() {
        assertEquals(Money(100, "COP"), MoneyLedger.snapshot(listOf(account(1, 100).copy(archived = true)), emptyList(), emptyList()).currencies.single())
    }
}
