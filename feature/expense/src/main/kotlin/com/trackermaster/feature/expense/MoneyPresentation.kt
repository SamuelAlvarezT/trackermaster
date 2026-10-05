package com.trackermaster.feature.expense

import com.trackermaster.core.domain.money.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** All filtering, grouping and monetary aggregates live outside composition. */
object MoneyPresentation {
    fun range(period: MoneyPeriod, locale: Locale): MoneyDateRange = when (period.kind) {
        MoneyPeriodKind.WEEK -> {
            val start = period.anchor.with(WeekFields.of(locale).dayOfWeek(), 1)
            MoneyDateRange(start, start.plusDays(6))
        }
        MoneyPeriodKind.MONTH -> MoneyDateRange(period.anchor.withDayOfMonth(1), period.anchor.withDayOfMonth(period.anchor.lengthOfMonth()))
        MoneyPeriodKind.YEAR -> MoneyDateRange(period.anchor.withDayOfYear(1), period.anchor.withDayOfYear(period.anchor.lengthOfYear()))
        MoneyPeriodKind.RANGE -> MoneyDateRange(period.from, period.through).also { require(!it.through.isBefore(it.from)) }
    }
    fun shift(period: MoneyPeriod, direction: Int): MoneyPeriod = when (period.kind) {
        MoneyPeriodKind.WEEK -> period.copy(anchor = period.anchor.plusWeeks(direction.toLong()))
        MoneyPeriodKind.MONTH -> period.copy(anchor = period.anchor.plusMonths(direction.toLong()))
        MoneyPeriodKind.YEAR -> period.copy(anchor = period.anchor.plusYears(direction.toLong()))
        MoneyPeriodKind.RANGE -> {
            val days = period.through.toEpochDay() - period.from.toEpochDay() + 1
            period.copy(from = period.from.plusDays(days * direction), through = period.through.plusDays(days * direction))
        }
    }
    fun rows(data: MoneyLedgerData, categories: List<MoneyCategory>, spaceId: Long?, currency: String,
             range: MoneyDateRange, filters: MoneyFilters = MoneyFilters()): List<MoneyHistoryRow> {
        val accounts = data.snapshot.accounts.associateBy { it.account.id }
        val pockets = data.snapshot.pockets.associateBy { it.pocket.id }
        val categoryMap = categories.associateBy { it.id }
        val transfers = data.movements.filter { it.transferId != null }.groupBy { it.transferId }
        val result = data.movements.filter { it.transferId == null }.map { movement ->
            MoneyHistoryRow("m:${movement.id}", movement, category = categoryMap[movement.categoryId],
                accountName = accounts.getValue(movement.accountId).account.name,
                pocketName = pockets[movement.pocketId]?.pocket?.name)
        } + transfers.mapNotNull { (id, legs) ->
            val out = legs.singleOrNull { it.direction == MoneyDirection.EXPENSE } ?: return@mapNotNull null
            val incoming = legs.singleOrNull { it.direction == MoneyDirection.INCOME } ?: return@mapNotNull null
            MoneyHistoryRow("t:$id", out, incoming,
                accountName = accounts.getValue(out.accountId).account.name,
                targetName = accounts.getValue(incoming.accountId).account.name,
                pocketName = pockets[out.pocketId]?.pocket?.name, targetPocketName = pockets[incoming.pocketId]?.pocket?.name)
        }
        return filterRows(result, spaceId, currency, range, filters)
    }
    fun filterRows(result: List<MoneyHistoryRow>, spaceId: Long?, currency: String,
                   range: MoneyDateRange, filters: MoneyFilters): List<MoneyHistoryRow> = result.filter { row ->
            val legs = listOfNotNull(row.movement, row.incoming)
            val kind = if (row.incoming != null) MoneyEntryKind.TRANSFER else
                if (row.movement.direction == MoneyDirection.INCOME) MoneyEntryKind.INCOME else MoneyEntryKind.EXPENSE
            // Scope predicates must match the same leg of a transfer.
            legs.any { it.amount.currencyCode == currency && (spaceId == null || it.spaceId == spaceId) &&
                (filters.accountId == null || it.accountId == filters.accountId) } &&
                !row.movement.date.isBefore(range.from) && !row.movement.date.isAfter(range.through) &&
                (filters.categoryId == null || row.category?.id == filters.categoryId) &&
                (filters.kind == null || kind == filters.kind) &&
                (filters.query.isBlank() || listOfNotNull(row.movement.note, row.accountName, row.targetName, row.pocketName, row.targetPocketName)
                    .any { it.contains(filters.query.trim(), ignoreCase = true) })
        }.sortedWith(compareByDescending<MoneyHistoryRow> { it.movement.date }.thenByDescending { it.movement.id })
    fun groups(rows: List<MoneyHistoryRow>, currency: String): List<MoneyDayGroup> = rows.groupBy { it.movement.date }
        .map { (day, entries) ->
            val net = entries.filter { it.incoming == null }.fold(Money(0, currency)) { total, row ->
                if (row.movement.direction == MoneyDirection.INCOME) total + row.movement.amount else total - row.movement.amount
            }
            MoneyDayGroup(day, entries, net)
        }
    fun editorError(editor: MoneyEditorUiState, data: MoneyLedgerData, categories: List<MoneyCategory>): MoneyUiError? {
        val source = data.snapshot.accounts.find { it.account.id == editor.accountId }?.account
            ?: return MoneyUiError.ACCOUNT
        if (source.archived) return MoneyUiError.ACCOUNT
        if (editor.pocketId != null && data.snapshot.pockets.none { it.pocket.id == editor.pocketId && it.pocket.accountId == source.id })
            return MoneyUiError.ACCOUNT
        if (amount(editor.amount, source.initialBalance.currencyCode)?.minorUnits?.let { it > 0 } != true) return MoneyUiError.AMOUNT
        if (editor.kind == MoneyEntryKind.TRANSFER) {
            val target = data.snapshot.accounts.find { it.account.id == editor.targetAccountId }?.account
                ?: return MoneyUiError.ACCOUNT
            if (target.archived || (target.id == source.id && editor.targetPocketId == editor.pocketId)) return MoneyUiError.ACCOUNT
            if (editor.targetPocketId != null && data.snapshot.pockets.none { it.pocket.id == editor.targetPocketId && it.pocket.accountId == target.id })
                return MoneyUiError.ACCOUNT
            if (source.initialBalance.currencyCode != target.initialBalance.currencyCode &&
                amount(editor.receivedAmount, target.initialBalance.currencyCode)?.minorUnits?.let { it > 0 } != true) return MoneyUiError.AMOUNT
        } else if (categories.none { it.id == editor.categoryId && it.isIncome == (editor.kind == MoneyEntryKind.INCOME) })
            return MoneyUiError.CATEGORY
        return null
    }
    fun amount(value: String, currency: String): Money? = try { Money.fromDecimal(BigDecimal(value), currency) } catch (_: Exception) { null }

    fun present(data: MoneyLedgerData, categories: List<MoneyCategory>, spaces: List<MoneySpace>,
                budgets: List<MoneyBudgetItem>, controls: MoneyControls): MoneyHomeUiState {
        val locale = Locale.forLanguageTag(controls.localeTag)
        val range = range(controls.period, locale)
        val snapshot = data.snapshot
        val code = controls.currency
        val balance = if (controls.spaceId == null) snapshot.currencies.find { it.currencyCode == code } ?: Money(0, code)
            else snapshot.spaces.find { it.spaceId == controls.spaceId && it.balance.currencyCode == code }?.balance ?: Money(0, code)
        val allRows = rows(data, categories, controls.spaceId, code, range)
        val history = rows(data, categories, controls.spaceId, code, range, controls.filters)
        val totals = MoneyLedger.periodTotals(data.movements, code, range.from, range.through, controls.spaceId)
        val expenses = data.movements.filter { it.transferId == null && it.direction == MoneyDirection.EXPENSE &&
            it.amount.currencyCode == code && (controls.spaceId == null || it.spaceId == controls.spaceId) &&
            !it.date.isBefore(range.from) && !it.date.isAfter(range.through) }
        val categoryTotals = expenses.groupBy { it.categoryId }.map { (id, rows) ->
            MoneyCategoryTotal(categories.find { it.id == id }, rows.fold(Money(0, code)) { total, row -> total + row.amount })
        }.sortedByDescending { it.amount.minorUnits }
        val months = ((if (controls.period.kind == MoneyPeriodKind.YEAR) 11 else 5) downTo 0).map { offset ->
            val start = range.through.withDayOfMonth(1).minusMonths(offset.toLong())
            val total = MoneyLedger.periodTotals(data.movements, code, start, start.withDayOfMonth(start.lengthOfMonth()), controls.spaceId)
            MoneyMonthTotal(start, total.income, total.expenses)
        }
        val editor = controls.editor?.let {
            val source = snapshot.accounts.find { row -> row.account.id == it.accountId }?.account
            val target = snapshot.accounts.find { row -> row.account.id == it.targetAccountId }?.account
            it.copy(currency = source?.initialBalance?.currencyCode ?: code,
                targetCurrency = target?.initialBalance?.currencyCode ?: code,
                canSave = editorError(it, data, categories) == null && !controls.saving, saving = controls.saving)
        }
        return MoneyHomeUiState(load = MoneyLoadState.CONTENT, controls = controls.copy(editor = editor), spaces = spaces,
            currencies = (listOf("COP", "USD") + snapshot.currencies.map { it.currencyCode }).distinct(), categories = categories,
            range = range, overview = MoneyOverviewUiState(balance, totals, allRows.take(5), snapshot.accounts.any { !it.account.archived }),
            history = MoneyHistoryUiState(groups(history, code), controls.filters, allRows.size, snapshot.accounts.any { !it.account.archived }),
            accounts = MoneyAccountsUiState(snapshot.accounts, snapshot.pockets, controls.showArchived),
            budgets = MoneyBudgetsUiState(budgets.filter { (it.spaceId == null || controls.spaceId == null || it.spaceId == controls.spaceId) &&
                (it.limit == null || it.limit.currencyCode == code) }), reports = MoneyReportsUiState(categoryTotals, months, totals))
    }
}

/** Typing in the editor never recomputes six months of totals or rebuilds the complete ledger. */
class MoneyPresenter {
    private data class Scope(val space: Long?, val currency: String, val period: MoneyPeriod, val locale: String)
    private var source: MoneyWorkspaceData? = null
    private var scope: Scope? = null
    private var base: MoneyHomeUiState? = null
    private var previousFilters: MoneyFilters? = null
    private var filtered: MoneyHistoryUiState? = null

    @Synchronized
    fun build(data: MoneyWorkspaceData, controls: MoneyControls): MoneyHomeUiState {
        val current = Scope(controls.spaceId, controls.currency, controls.period, controls.localeTag)
        if (source !== data || current != scope) {
            val budgets = data.budgets.map { budget -> MoneyBudgetItem(budget.id,
                data.categories.find { it.id == budget.categoryId }, budget.legacyAmount, budget.limit, budget.spaceId, budget.period) }
            base = MoneyPresentation.present(data.ledger, data.categories, data.spaces, budgets,
                controls.copy(filters = MoneyFilters(), editor = null))
            source = data; scope = current; previousFilters = null
        }
        val content = requireNotNull(base)
        if (filtered == null || previousFilters != controls.filters) {
            val all = content.history.groups.flatMap { it.rows }
            val rows = MoneyPresentation.filterRows(all, controls.spaceId, controls.currency, content.range, controls.filters)
            filtered = MoneyHistoryUiState(MoneyPresentation.groups(rows, controls.currency), controls.filters,
                all.size, content.overview.hasAccounts)
            previousFilters = controls.filters
        }
        val editor = controls.editor?.let { value ->
            val from = data.ledger.snapshot.accounts.find { it.account.id == value.accountId }?.account
            val target = data.ledger.snapshot.accounts.find { it.account.id == value.targetAccountId }?.account
            value.copy(currency = from?.initialBalance?.currencyCode ?: controls.currency,
                targetCurrency = target?.initialBalance?.currencyCode ?: controls.currency,
                canSave = !controls.saving && MoneyPresentation.editorError(value, data.ledger, data.categories) == null,
                saving = controls.saving)
        }
        return content.copy(controls = controls.copy(editor = editor), history = requireNotNull(filtered),
            accounts = content.accounts.copy(showArchived = controls.showArchived))
    }
}
