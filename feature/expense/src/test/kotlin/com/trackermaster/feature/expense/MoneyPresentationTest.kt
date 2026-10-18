package com.trackermaster.feature.expense

import com.trackermaster.core.domain.money.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MoneyPresentationTest {
    private val day = LocalDate.of(2026, 10, 4)
    private val data = MoneyPreviewData.workspace(day)
    private fun controls() = MoneyControls(period = MoneyPeriod(anchor = day), localeTag = "es-CO")

    @Test fun weekFollowsLocaleAndIncludesBothEndpoints() {
        val period = MoneyPeriod(MoneyPeriodKind.WEEK, day)
        assertEquals(MoneyDateRange(day, day.plusDays(6)), MoneyPresentation.range(period, Locale.US))
        assertEquals(MoneyDateRange(day.minusDays(6), day), MoneyPresentation.range(period, Locale.forLanguageTag("es-ES")))
    }
    @Test fun leapMonthAndCustomRangeShiftAreExact() {
        val feb = LocalDate.of(2024, 2, 17)
        assertEquals(LocalDate.of(2024, 2, 29), MoneyPresentation.range(MoneyPeriod(anchor = feb), Locale.US).through)
        val period = MoneyPeriod(MoneyPeriodKind.RANGE, day, day, day.plusDays(2))
        assertEquals(day.plusDays(3), MoneyPresentation.shift(period, 1).from)
        assertEquals(period, MoneyPresentation.shift(MoneyPresentation.shift(period, 1), -1))
    }
    @Test fun transfersAppearOnceAndDoNotCountAsSpendingOrIncome() {
        val state = MoneyPresenter().build(data, controls())
        assertEquals(5, state.history.totalRows)
        assertEquals(2, state.history.groups.flatMap { it.rows }.count { it.incoming != null })
        assertEquals(22000000L, state.overview.totals.income.minorUnits)
        assertEquals(2210000L, state.overview.totals.expenses.minorUnits)
        assertEquals(19790000L, state.history.groups.sumOf { it.net.minorUnits })
        assertEquals(57790000L, state.overview.balance.minorUnits)
    }
    @Test fun filtersCombineAccountCategoryTypeAndCaseInsensitiveSearch() {
        val controls = controls().copy(filters = MoneyFilters("ALMUER", 1, 1, MoneyEntryKind.EXPENSE))
        val rows = MoneyPresenter().build(data, controls).history.groups.flatMap { it.rows }
        assertEquals(listOf(1L), rows.map { it.movement.id })
        assertTrue(MoneyPresenter().build(data, controls.copy(filters = controls.filters.copy(accountId = 2))).history.groups.isEmpty())
    }
    @Test fun fxScopePredicatesMustMatchTheSameTransferLeg() {
        val original = data.ledger.movements.filter { it.transferId == "business" }
        val rows = original.map { if (it.direction == MoneyDirection.INCOME) it.copy(accountId = 3, spaceId = 5, amount = Money(100, "USD")) else it }
        val fx = data.ledger.copy(movements = rows)
        val range = MoneyDateRange(day, day)
        assertEquals(1, MoneyPresentation.rows(fx, data.categories, 5, "USD", range, MoneyFilters(accountId = 3)).size)
        assertTrue(MoneyPresentation.rows(fx, data.categories, 5, "COP", range).isEmpty())
        assertTrue(MoneyPresentation.rows(fx, data.categories, null, "COP", range, MoneyFilters(accountId = 3)).isEmpty())
    }
    @Test fun balanceDoesNotChangeWhenThePeriodOrSearchChanges() {
        val state = MoneyPresenter().build(data, controls())
        val previous = MoneyPresenter().build(data, controls().copy(period = MoneyPeriod(anchor = day.minusMonths(1)), filters = MoneyFilters("none")))
        assertEquals(state.overview.balance, previous.overview.balance)
        assertEquals(0L, previous.overview.totals.expenses.minorUnits)
        assertEquals(Money(150000, "USD"), MoneyPresenter().build(data, controls().copy(currency = "USD", spaceId = 5)).overview.balance)
    }
    @Test fun editorRejectsPrecisionMismatchCategoryAndMissingFxAmount() {
        val valid = MoneyEditorUiState(accountId = 1, categoryId = 1, amount = "100")
        assertNull(MoneyPresentation.editorError(valid, data.ledger, data.categories))
        assertEquals(MoneyUiError.AMOUNT, MoneyPresentation.editorError(valid.copy(amount = "1.001"), data.ledger, data.categories))
        assertEquals(MoneyUiError.CATEGORY, MoneyPresentation.editorError(valid.copy(categoryId = 6), data.ledger, data.categories))
        val fx = valid.copy(kind = MoneyEntryKind.TRANSFER, targetAccountId = 3)
        assertEquals(MoneyUiError.AMOUNT, MoneyPresentation.editorError(fx, data.ledger, data.categories))
        assertNull(MoneyPresentation.editorError(fx.copy(receivedAmount = "0.50"), data.ledger, data.categories))
    }
    @Test fun sameFundingEndpointIsInvalidButPocketAllocationIsValid() {
        val transfer = MoneyEditorUiState(kind = MoneyEntryKind.TRANSFER, accountId = 1, targetAccountId = 1, amount = "10")
        assertEquals(MoneyUiError.ACCOUNT, MoneyPresentation.editorError(transfer, data.ledger, data.categories))
        assertNull(MoneyPresentation.editorError(transfer.copy(targetPocketId = 1), data.ledger, data.categories))
    }
    @Test fun typingReusesReportsWhileFiltersAndScopeRefreshTheirTotals() {
        val presenter = MoneyPresenter()
        val initial = presenter.build(data, controls())
        val typed = presenter.build(data, controls().copy(editor = MoneyEditorUiState(amount = "10", accountId = 1, categoryId = 1)))
        assertSame(initial.reports, typed.reports)
        val filtered = presenter.build(data, controls().copy(filters = MoneyFilters("none")))
        assertNotSame(initial.reports, filtered.reports)
        assertEquals(0L,filtered.reports.totals.expenses.minorUnits)
        assertTrue(filtered.history.groups.isEmpty())
        val usd = presenter.build(data, controls().copy(currency = "USD"))
        assertNotSame(initial.reports, usd.reports)
        assertTrue(usd.history.groups.isEmpty())
    }
}
