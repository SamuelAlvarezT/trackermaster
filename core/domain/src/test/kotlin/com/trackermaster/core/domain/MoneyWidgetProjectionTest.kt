package com.trackermaster.core.domain

import com.trackermaster.core.domain.money.*
import com.trackermaster.core.domain.model.AccountType
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class MoneyWidgetProjectionTest {
    private val today=LocalDate.of(2026,10,18)
    private val accounts=listOf(MoneyAccount(1,"Personal",AccountType.CASH,Money(10000,"COP"),2),
        MoneyAccount(2,"Business",AccountType.CASH,Money(20000,"COP"),3),
        MoneyAccount(3,"Work & Travel",AccountType.CASH,Money(5000,"USD"),5))
    private fun movement(id: Long,amount: Long,principal: Boolean=false,capital: Boolean=false,transfer: String?=null)=
        MoneyMovement(id,1,Money(amount,"COP"),MoneyDirection.EXPENSE,today,2,principal=principal,investmentCapital=capital,transferId=transfer)
    private fun data() : MoneyWorkspaceData {
        val movements=listOf(movement(1,1000),movement(2,500,principal=true),movement(3,700,capital=true),movement(4,100,transfer="transfer"))
        return MoneyWorkspaceData(MoneyLedgerData(MoneyLedger.snapshot(accounts,emptyList(),movements),movements),emptyList(),
            listOf(MoneySpace(2,MoneySpaceKind.PERSONAL),MoneySpace(3,MoneySpaceKind.BUSINESS),MoneySpace(5,MoneySpaceKind.TRAVEL)),emptyList())
    }
    @Test fun currenciesStaySeparateAndCashIncludesPrincipalAndInvestmentMovements() {
        val summary=MoneyWidgetProjection.build(data(),today)
        assertEquals(listOf(Money(27700,"COP"),Money(5000,"USD")),summary.balances)
        assertEquals(Money(1000,"COP"),summary.month.first { it.expenses.currencyCode=="COP" }.expenses)
        assertEquals(3,summary.scopes.size)
    }
    @Test fun spaceSelectionUsesAllocatedBalanceWithoutOtherSpaces() {
        val summary=MoneyWidgetProjection.build(data(),today,MoneyWidgetSelection("COP",3))
        assertEquals(listOf(Money(20000,"COP")),summary.balances);assertEquals(Money(0,"COP"),summary.month.single().expenses)
    }
    @Test fun accountSelectionUsesItsOwnCashAndTransactionsOnly() {
        val summary=MoneyWidgetProjection.build(data(),today,MoneyWidgetSelection(accountId=3))
        assertEquals(listOf(Money(5000,"USD")),summary.balances);assertTrue(summary.scopes.isEmpty())
        assertEquals("USD",summary.month.single().income.currencyCode)
    }
    @Test fun missingSelectionNeverFallsBackToRevealingGlobalBalances() {
        assertTrue(MoneyWidgetProjection.build(data(),today,MoneyWidgetSelection(accountId=999)).balances.isEmpty())
    }
}
