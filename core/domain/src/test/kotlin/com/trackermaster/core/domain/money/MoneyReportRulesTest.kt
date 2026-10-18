package com.trackermaster.core.domain.money

import com.trackermaster.core.domain.model.AccountType
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter
import java.time.LocalDate

class MoneyReportRulesTest {
    private val day=LocalDate.of(2026,10,1)
    private val accounts=listOf(MoneyAccount(1,"=Nequi",AccountType.CHECKING,Money(100000,"COP"),2),MoneyAccount(2,"USD",AccountType.CASH,Money(50000,"USD"),5))
    private fun row(id:Long,amount:Long=100,code:String="COP",space:Long=2,date:LocalDate=day)=MoneyMovement(id,if(code=="COP")1 else 2,Money(amount,code),MoneyDirection.EXPENSE,date,space,categoryId=1,note="A, \"note\"\nline")
    private fun data(rows:List<MoneyMovement>)=MoneyWorkspaceData(MoneyLedgerData(MoneyLedger.snapshot(accounts,emptyList(),rows),rows),listOf(MoneyCategory(1,"Food",false)),emptyList(),emptyList())
    private val scope=MoneyReportScope("COP",day,day.plusDays(30),spaceId=2)
    @Test fun exportScopeExcludesOtherCurrencySpaceAndDates(){val report=MoneyReportRules.build(data(listOf(row(1),row(2,code="USD"),row(3,space=3),row(4,date=day.minusDays(1)))),scope);assertEquals(listOf(1L),report.entries.map{it.movement.id});assertEquals(100L,report.totals.expenses.minorUnits)}
    @Test fun cashCapitalAndTransfersAreListedButNotOperatingExpenses(){val rows=listOf(row(1),row(2).copy(principal=true),row(3).copy(investmentId="i",investmentCapital=true),row(4).copy(transferId="t"));val report=MoneyReportRules.build(data(rows),scope);assertEquals(4,report.entries.size);assertEquals(100L,report.totals.expenses.minorUnits);assertEquals(100L,report.categories[1L]?.minorUnits)}
    @Test fun partialMonthsUseOnlySelectedDates(){val report=MoneyReportRules.build(data(listOf(row(1,date=day),row(2,date=day.plusDays(20)),row(3,date=day.plusMonths(1)))),scope.copy(from=day.plusDays(15),through=day.plusMonths(1)));assertEquals(2,report.months.size);assertEquals(100L,report.months.first().totals.expenses.minorUnits)}
    @Test fun allReportAggregatesHonorAccountCategoryTypeAndSearch(){val report=MoneyReportRules.build(data(listOf(row(1),row(2).copy(categoryId=2),row(3).copy(direction=MoneyDirection.INCOME))),scope.copy(accountId=1,categoryId=1,direction=MoneyDirection.EXPENSE,query="note"));assertEquals(listOf(1L),report.entries.map{it.movement.id});assertEquals(100L,report.totals.expenses.minorUnits)}
    @Test fun csvEscapesTextAndPreservesExactAmounts(){val report=MoneyReportRules.build(data(listOf(row(1,amount=9007199254740993))),scope);val writer=StringWriter();MoneyCsv.write(report,writer);val csv=writer.toString();assertTrue(csv.contains("\"9007199254740993\""));assertTrue(csv.contains("\"90071992547409.93\""));assertTrue(csv.contains("\"'=Nequi\""));assertTrue(csv.contains("\"A, \"\"note\"\"\nline\""));assertTrue(csv.endsWith("\r\n"))}
    @Test fun emptyReportIsExportableAndContainsZeroMonths(){val report=MoneyReportRules.build(data(emptyList()),scope);assertEquals(1,report.months.size);assertEquals(0L,report.totals.net.minorUnits);val writer=StringWriter();MoneyCsv.write(report,writer);assertEquals(1,writer.toString().lineSequence().filter{it.isNotEmpty()}.count())}
    @Test fun transferAccountFilterMatchesThatLeg(){val rows=listOf(row(1).copy(transferId="pair"),row(2,code="USD",space=5).copy(transferId="pair",direction=MoneyDirection.INCOME));val report=MoneyReportRules.build(data(rows),scope.copy(currency="USD",spaceId=5,accountId=2,transfersOnly=true));assertEquals(2L,report.entries.single().movement.id);assertEquals("USD",report.entries.single().movement.amount.currencyCode)}
    @Test(expected=IllegalArgumentException::class) fun backwardsRangeIsRejected(){MoneyReportRules.build(data(emptyList()),scope.copy(from=day.plusDays(50)))}
}
