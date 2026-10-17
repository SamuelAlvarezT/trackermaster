package com.trackermaster.core.domain.money

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MoneyPlanningRulesTest {
    private val day = LocalDate.of(2026, 10, 6)
    private fun budget(limit: Long = 10000) = MoneyBudget(1, 1, 100.0, Money(limit, "COP"), 2, "MONTHLY")
    private fun expense(amount: Long, code: String = "COP", space: Long = 2, category: Long = 1) =
        MoneyMovement(accountId = 1, amount = Money(amount, code), direction = MoneyDirection.EXPENSE, date = day, spaceId = space, categoryId = category)
    private fun plan(due: String = "2026-01-31", frequency: MoneyFrequency = MoneyFrequency.MONTHLY) = MoneyPlan("p", "Data", MoneyPlanKind.PAYMENT,
        MoneyDirection.EXPENSE, Money(100, "COP"), "COP", null, null, null, LocalDate.parse(due), LocalDate.parse(due), frequency)
    @Test fun spendingFiltersCurrencySpaceCategoryDateIncomeAndTransfers() {
        val row = expense(7999)
        val data = listOf(row, expense(3000,"USD"), expense(3000,space=3), expense(3000,category=2),
            row.copy(date=day.minusMonths(1)), row.copy(direction=MoneyDirection.INCOME), row.copy(transferId="transfer"))
        val p=BudgetRules.progress(budget(),data,day,Locale.US)!!
        assertEquals(7999L,p.spent.minorUnits);assertEquals(BudgetStatus.OK,p.status)
        assertEquals(2001L,p.remaining.minorUnits)
    }
    @Test fun thresholdsUseExactRatioAndDistinguishLimitFromExceeded() {
        assertEquals(BudgetStatus.OK,BudgetRules.progress(budget(),listOf(expense(7999)),day,Locale.US)!!.status)
        assertEquals(BudgetStatus.NEAR,BudgetRules.progress(budget(),listOf(expense(8000)),day,Locale.US)!!.status)
        assertEquals(BudgetStatus.LIMIT,BudgetRules.progress(budget(),listOf(expense(10000)),day,Locale.US)!!.status)
        assertEquals(BudgetStatus.EXCEEDED,BudgetRules.progress(budget(),listOf(expense(10001)),day,Locale.US)!!.status)
        assertEquals(-1L,BudgetRules.progress(budget(),listOf(expense(10001)),day,Locale.US)!!.remaining.minorUnits)
    }
    @Test fun percentDoesNotOverflowWhenMultiplyingLongAmounts() {
        val p=BudgetRules.progress(budget(Long.MAX_VALUE),listOf(expense(Long.MAX_VALUE)),day,Locale.US)!!
        assertEquals(100,p.percent);assertEquals(BudgetStatus.LIMIT,p.status)
    }
    @Test fun customRangeUsesInclusiveDatesAndInactivePastBudgetsDoNotAlert() {
        val b=budget().copy(period="CUSTOM",startDate=day.minusDays(1),endDate=day)
        assertEquals(200L,BudgetRules.progress(b,listOf(expense(100).copy(date=day.minusDays(1)),expense(100),expense(100).copy(date=day.plusDays(1))),day,Locale.US)!!.spent.minorUnits)
        assertFalse(BudgetRules.progress(b,emptyList(),day.plusDays(1),Locale.US)!!.inPeriod)
    }
    @Test fun weeklyBoundariesFollowRegionAndYearlyHandlesLeapDay() {
        val b=budget().copy(period="WEEKLY")
        assertEquals(LocalDate.of(2026,10,4),BudgetRules.dates(b,day,Locale.US).first)
        assertEquals(LocalDate.of(2026,10,5),BudgetRules.dates(b,day,Locale.UK).first)
        assertEquals(LocalDate.of(2024,12,31),BudgetRules.dates(b.copy(period="YEARLY"),LocalDate.of(2024,2,29),Locale.UK).second)
    }
    @Test fun legacyCurrencyIsNeverInferredAndAllCategoryBudgetsAreExplicit() {
        assertNull(BudgetRules.progress(budget().copy(limit=null),listOf(expense(500)),day,Locale.US))
        assertEquals(500L,BudgetRules.progress(budget().copy(categoryId=0,spaceId=null),listOf(expense(500,space=3,category=2)),day,Locale.US)!!.spent.minorUnits)
    }
    @Test fun monthlyRecurrenceRestoresThirtyFirstAfterFebruary() {
        val first=plan();val feb=PlanRules.following(first)!!
        assertEquals(LocalDate.of(2026,2,28),feb)
        assertEquals(LocalDate.of(2026,3,31),PlanRules.following(first.copy(nextDue=feb)))
    }
    @Test fun yearlyRecurrenceRestoresLeapDayAndOnceStops() {
        var p=plan("2024-02-29",MoneyFrequency.YEARLY)
        repeat(4){p=p.copy(nextDue=PlanRules.following(p)!!)}
        assertEquals(LocalDate.of(2028,2,29),p.nextDue)
        assertNull(PlanRules.following(plan(frequency=MoneyFrequency.ONCE)))
    }
    @Test fun forecastsExcludePurchasePausedAndOtherCurrencies() {
        val start=LocalDate.of(2026,1,1);val end=LocalDate.of(2026,3,31)
        val p=plan();val data=listOf(p,p.copy(id="off",active=false),p.copy(id="purchase",kind=MoneyPlanKind.PURCHASE,amount=null),p.copy(id="usd",currency="USD",amount=Money(100,"USD")))
        assertEquals(300L,PlanRules.forecast(data,start,end,"COP",MoneyDirection.EXPENSE).minorUnits)
    }
    @Test fun snoozeChangesReminderNotPaymentDateAndPurchaseMayOmitAmount() {
        val p=plan().copy(reminderDays=3)
        assertEquals(LocalDate.of(2026,1,28),PlanRules.reminderDate(p))
        assertEquals(LocalDate.of(2026,2,1),PlanRules.reminderDate(p.copy(remindOn=LocalDate.of(2026,2,1))))
        PlanRules.validate(p.copy(kind=MoneyPlanKind.PURCHASE,amount=null))
    }
    @Test fun invalidBudgetsAndPaymentAmountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java){BudgetRules.validate(budget(0))}
        assertThrows(IllegalArgumentException::class.java){BudgetRules.validate(budget().copy(alertThreshold=101))}
        assertThrows(IllegalArgumentException::class.java){BudgetRules.validate(budget().copy(period="CUSTOM",startDate=day,endDate=day.minusDays(1)))}
        assertThrows(IllegalArgumentException::class.java){PlanRules.validate(plan().copy(amount=null))}
    }
    @Test fun longOverdueDailyPlansForecastOnlyCurrentRangeWithoutWalkingEveryMissedDay() {
        val p=plan("1900-01-01",MoneyFrequency.DAILY)
        val from=LocalDate.of(2026,10,1)
        assertEquals(3100L,PlanRules.forecast(listOf(p),from,from.withDayOfMonth(31),"COP",MoneyDirection.EXPENSE).minorUnits)
    }
    @Test fun aPurchaseWithKnownAmountCanBeIncludedInTheForecast() {
        val p=plan().copy(kind=MoneyPlanKind.PURCHASE,frequency=MoneyFrequency.ONCE)
        assertEquals(100L,PlanRules.forecast(listOf(p),LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),"COP",MoneyDirection.EXPENSE).minorUnits)
    }
}
