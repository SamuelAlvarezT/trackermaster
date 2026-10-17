package com.trackermaster.core.domain.money

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MoneyCommitmentRulesTest {
    private val day=LocalDate.of(2026,10,15)
    private val debt=MoneyDebt("sister","Sister",DebtKind.PAYABLE,Money(10000,"USD"),5,day.minusDays(1))
    private fun movement(amount:Long=2500)=MoneyMovement(1,1,Money(amount,"USD"),MoneyDirection.EXPENSE,day,5,principal=true)
    private fun progress(amount:Long)=CommitmentRules.debt(debt,listOf(DebtPayment("p",debt.id,1,PrincipalEvent.REPAYMENT,true)),listOf(movement(amount)),day)
    @Test fun existingDebtHasNoCashMovementAndStartsFullyOutstanding(){val p=CommitmentRules.debt(debt,emptyList(),emptyList(),day);assertEquals(10000L,p.outstanding.minorUnits);assertTrue(p.overdue)}
    @Test fun repaymentsReducePrincipalExactly(){val p=progress(2500);assertEquals(2500L,p.paid.minorUnits);assertEquals(7500L,p.outstanding.minorUnits)}
    @Test fun fullyPaidDebtIsNotOverdue(){val p=progress(10000);assertEquals(0L,p.outstanding.minorUnits);assertFalse(p.overdue)}
    @Test fun allFourCashDirectionsAreExplicit(){assertEquals(MoneyDirection.INCOME,CommitmentRules.direction(DebtKind.PAYABLE,PrincipalEvent.DISBURSEMENT));assertEquals(MoneyDirection.EXPENSE,CommitmentRules.direction(DebtKind.PAYABLE,PrincipalEvent.REPAYMENT));assertEquals(MoneyDirection.EXPENSE,CommitmentRules.direction(DebtKind.RECEIVABLE,PrincipalEvent.DISBURSEMENT));assertEquals(MoneyDirection.INCOME,CommitmentRules.direction(DebtKind.RECEIVABLE,PrincipalEvent.REPAYMENT))}
    @Test(expected=IllegalArgumentException::class)fun overpaymentIsRejected(){progress(10001)}
    @Test(expected=IllegalArgumentException::class)fun repaymentCurrencyCannotBeMixed(){CommitmentRules.debt(debt,listOf(DebtPayment("p",debt.id,1,PrincipalEvent.REPAYMENT,true)),listOf(movement().copy(amount=Money(2500,"COP"))),day)}
    @Test fun principalIsExcludedFromOperatingTotalsAndBudget(){val principal=movement();val ordinary=principal.copy(id=2,principal=false,amount=Money(500,"USD"),categoryId=1)
        val totals=MoneyLedger.periodTotals(listOf(principal,ordinary),"USD",day,day);assertEquals(500L,totals.expenses.minorUnits)
        val b=MoneyBudget(1,0,100.0,Money(10000,"USD"),5,"MONTHLY");assertEquals(500L,BudgetRules.progress(b,listOf(principal,ordinary),day,Locale.US)!!.spent.minorUnits)}
    @Test fun goalTracksActualPocketMoneyAndAllowsSavingPastTarget(){val goal=SavingsGoal("g","Travel",Money(10000,"USD"),1);val p=CommitmentRules.goal(goal,MoneyPocketBalance(MoneyPocket(1,1,5,"Travel"),Money(12500,"USD")));assertEquals(12500L,p.reserved.minorUnits);assertEquals(0L,p.remaining.minorUnits);assertEquals(100,p.percent)}
    @Test fun goalPercentDoesNotOverflow(){val p=CommitmentRules.goal(SavingsGoal("g","Goal",Money(1,"COP"),1),MoneyPocketBalance(MoneyPocket(1,1,4,"Goal"),Money(Long.MAX_VALUE,"COP")));assertEquals(100,p.percent)}
    @Test(expected=IllegalArgumentException::class)fun goalCannotUseDifferentCurrency(){CommitmentRules.goal(SavingsGoal("g","Goal",Money(100,"COP"),1),MoneyPocketBalance(MoneyPocket(1,1,4,"Goal"),Money(100,"USD")))}
}
