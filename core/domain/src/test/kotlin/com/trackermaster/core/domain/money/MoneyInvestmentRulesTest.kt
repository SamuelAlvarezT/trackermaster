package com.trackermaster.core.domain.money

import com.trackermaster.core.domain.model.AccountType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MoneyInvestmentRulesTest {
    private val day=LocalDate.of(2026,10,1)
    private val position=MoneyInvestment("asset","Fund","USD",5,day,Money(10000,"USD"),Money(12000,"USD"))
    private fun event(kind:InvestmentEventKind, amount:Long, cost:Long=amount, offset:Long=1)=InvestmentEvent("$kind:$offset","asset",kind,Money(amount,"USD"),day.plusDays(offset),Money(cost,"USD"),sequence=offset)
    @Test fun existingHoldingHasNoCashAndIsCountedOnce(){val p=InvestmentRules.progress(position,emptyList(),day);assertEquals(12000L,p.value.minorUnits);assertEquals(2000L,p.unrealized.minorUnits);assertEquals(17000L,InvestmentRules.assets(Money(5000,"USD"),listOf(p)).minorUnits)}
    @Test fun contributionAddsCostAndValueWithoutInventingReturn(){val p=InvestmentRules.progress(position,listOf(event(InvestmentEventKind.CONTRIBUTION,3000)),day.plusDays(3));assertEquals(13000L,p.cost.minorUnits);assertEquals(15000L,p.value.minorUnits);assertEquals(0L,p.realized.minorUnits)}
    @Test fun withdrawalSeparatesProceedsFromRecoveredCost(){val p=InvestmentRules.progress(position,listOf(event(InvestmentEventKind.WITHDRAWAL,6000,4000)),day.plusDays(3));assertEquals(6000L,p.cost.minorUnits);assertEquals(6000L,p.value.minorUnits);assertEquals(2000L,p.realized.minorUnits);assertEquals(0L,p.unrealized.minorUnits)}
    @Test fun realizedLossCanBeRecorded(){val p=InvestmentRules.progress(position,listOf(event(InvestmentEventKind.WITHDRAWAL,3000,5000)),day.plusDays(3));assertEquals(-2000L,p.realized.minorUnits)}
    @Test fun valuationChangesNeitherCostNorRealizedReturns(){val p=InvestmentRules.progress(position,listOf(event(InvestmentEventKind.VALUATION,9000)),day.plusDays(3));assertEquals(10000L,p.cost.minorUnits);assertEquals(9000L,p.value.minorUnits);assertEquals(0L,p.realized.minorUnits);assertEquals(day.plusDays(1),p.valuedOn)}
    @Test fun dividendsDoNotIncreaseTheHolding(){val p=InvestmentRules.progress(position,listOf(event(InvestmentEventKind.DIVIDEND,1000)),day.plusDays(3));assertEquals(12000L,p.value.minorUnits);assertEquals(1000L,p.realized.minorUnits)}
    @Test fun historicalSnapshotExcludesLaterValuations(){val p=InvestmentRules.progress(position,listOf(event(InvestmentEventKind.VALUATION,20000,offset=3)),day.plusDays(2));assertEquals(12000L,p.value.minorUnits)}
    @Test fun sameDayOrderingUsesPersistentSequence(){val p=InvestmentRules.progress(position,listOf(event(InvestmentEventKind.VALUATION,17000).copy(sequence=2),event(InvestmentEventKind.CONTRIBUTION,3000).copy(sequence=1)),day.plusDays(2));assertEquals(17000L,p.value.minorUnits)}
    @Test(expected=IllegalArgumentException::class) fun costCannotBeWithdrawnTwice(){InvestmentRules.progress(position,listOf(event(InvestmentEventKind.WITHDRAWAL,9000,11000)),day.plusDays(3))}
    @Test(expected=IllegalArgumentException::class) fun currenciesCannotBeCombined(){InvestmentRules.assets(Money(0,"COP"),listOf(InvestmentRules.progress(position,emptyList(),day)))}
    @Test(expected=ArithmeticException::class) fun overflowIsRejected(){InvestmentRules.progress(position.copy(initialCost=Money(Long.MAX_VALUE,"USD")),listOf(event(InvestmentEventKind.CONTRIBUTION,1)),day.plusDays(2))}
    @Test fun investmentCapitalDoesNotConsumeBudgetsOrOperatingTotals(){val m=MoneyMovement(accountId=1,amount=Money(3000,"USD"),direction=MoneyDirection.EXPENSE,date=day,spaceId=5,investmentId="asset",investmentCapital=true)
        assertEquals(0L,MoneyLedger.periodTotals(listOf(m),"USD",day,day).expenses.minorUnits)
        val budget=MoneyBudget(1,0,0.0,Money(1000,"USD"),5,"MONTHLY")
        assertEquals(0L,BudgetRules.progress(budget,listOf(m),day,java.util.Locale.US)!!.spent.minorUnits)
    }
}
