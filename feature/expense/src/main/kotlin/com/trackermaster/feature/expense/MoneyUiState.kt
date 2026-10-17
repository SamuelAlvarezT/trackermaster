package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import java.time.LocalDate
import java.util.Locale

enum class MoneyDestination { SUMMARY, MOVEMENTS, BUDGETS, REPORTS, ACCOUNTS }
enum class MoneyLoadState { LOADING, CONTENT, ERROR }
enum class MoneyPeriodKind { WEEK, MONTH, YEAR, RANGE }
enum class MoneyEntryKind { EXPENSE, INCOME, TRANSFER }
enum class MoneyUiError { LOAD, SAVE, AMOUNT, CATEGORY, ACCOUNT, DATE, FUNDS, CURRENCY, CONFLICT, NAME }
enum class MoneyNoticeKind { SAVED, DELETED, RESTORED, PREFERENCES_FAILED }
data class MoneyNotice(val id: Long, val kind: MoneyNoticeKind)

data class MoneyPeriod(
    val kind: MoneyPeriodKind = MoneyPeriodKind.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val from: LocalDate = anchor,
    val through: LocalDate = anchor,
)
data class MoneyDateRange(val from: LocalDate, val through: LocalDate)
data class MoneyFilters(val query: String = "", val accountId: Long? = null,
                        val categoryId: Long? = null, val kind: MoneyEntryKind? = null)
data class MoneyHistoryRow(
    val key: String,
    val movement: MoneyMovement,
    val incoming: MoneyMovement? = null,
    val category: MoneyCategory? = null,
    val accountName: String,
    val targetName: String? = null,
    val pocketName: String? = null,
    val targetPocketName: String? = null,
)
data class MoneyDayGroup(val day: LocalDate, val rows: List<MoneyHistoryRow>, val net: Money)
data class MoneyOverviewUiState(
    val balance: Money = Money(0, "COP"),
    val totals: MoneyPeriodTotals = MoneyPeriodTotals(Money(0, "COP"), Money(0, "COP")),
    val recent: List<MoneyHistoryRow> = emptyList(),
    val hasAccounts: Boolean = false,
    val upcoming: List<MoneyPlan> = emptyList(),
)
data class MoneyHistoryUiState(val groups: List<MoneyDayGroup> = emptyList(),
    val filters: MoneyFilters = MoneyFilters(), val totalRows: Int = 0, val hasAccounts: Boolean = false)
data class MoneyAccountsUiState(val accounts: List<MoneyAccountBalance> = emptyList(),
    val pockets: List<MoneyPocketBalance> = emptyList(), val showArchived: Boolean = false)
data class MoneyBudgetItem(val id: Long, val category: MoneyCategory?, val legacyAmount: Double,
    val limit: Money?, val spaceId: Long?, val period: String, val progress: BudgetProgress? = null, val allCategories: Boolean = false)
data class MoneyBudgetsUiState(val items: List<MoneyBudgetItem> = emptyList())
data class MoneyCategoryTotal(val category: MoneyCategory?, val amount: Money)
data class MoneyMonthTotal(val day: LocalDate, val income: Money, val expense: Money) {
    val net: Money get() = income - expense
}
data class MoneyReportsUiState(val categories: List<MoneyCategoryTotal> = emptyList(),
    val months: List<MoneyMonthTotal> = emptyList(), val totals: MoneyPeriodTotals = MoneyPeriodTotals(Money(0, "COP"), Money(0, "COP")))

data class MoneyEditorUiState(
    val id: Long = 0,
    val requestId: String = "",
    val transferId: String? = null,
    val kind: MoneyEntryKind = MoneyEntryKind.EXPENSE,
    val amount: String = "",
    val receivedAmount: String = "",
    val accountId: Long? = null,
    val pocketId: Long? = null,
    val targetAccountId: Long? = null,
    val targetPocketId: Long? = null,
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val attachmentPath: String? = null,
    val expanded: Boolean = false,
    val error: MoneyUiError? = null,
    val saving: Boolean = false,
    val canSave: Boolean = false,
    val currency: String = "COP",
    val targetCurrency: String = "COP",
    val fundingLocked: Boolean = false,
)
data class MoneyAccountEditorUiState(val id: Long = 0, val name: String = "",
    val type: AccountType = AccountType.CHECKING, val currency: String = "COP",
    val initialAmount: String = "0", val spaceId: Long = 2, val hasMovements: Boolean = false,
    val error: MoneyUiError? = null)
data class MoneyPocketEditorUiState(val accountId: Long, val name: String = "", val spaceId: Long = 4,
    val error: MoneyUiError? = null)
data class MoneyControls(
    val destination: MoneyDestination = MoneyDestination.SUMMARY,
    val spaceId: Long? = null, val currency: String = "COP", val period: MoneyPeriod = MoneyPeriod(),
    val filters: MoneyFilters = MoneyFilters(), val localeTag: String = Locale.getDefault().toLanguageTag(),
    val showArchived: Boolean = false, val settingsOpen: Boolean = false, val filtersOpen: Boolean = false,
    val rangeOpen: Boolean = false, val editorDateOpen: Boolean = false,
    val editor: MoneyEditorUiState? = null, val accountEditor: MoneyAccountEditorUiState? = null,
    val pocketEditor: MoneyPocketEditorUiState? = null, val categoryEditor: Boolean = false,
    val spaceEditor: Boolean = false, val customName: String = "", val customError: MoneyUiError? = null,
    val saving: Boolean = false, val notice: MoneyNotice? = null, val error: MoneyUiError? = null,
    val privacyOverride: Boolean? = null,
)
data class MoneyHomeUiState(
    val load: MoneyLoadState = MoneyLoadState.LOADING,
    val preferences: MoneyPreferences = MoneyPreferences(hideAmounts = true),
    val controls: MoneyControls = MoneyControls(),
    val spaces: List<MoneySpace> = emptyList(), val currencies: List<String> = listOf("COP", "USD"),
    val categories: List<MoneyCategory> = emptyList(),
    val range: MoneyDateRange = MoneyDateRange(LocalDate.now(), LocalDate.now()),
    val overview: MoneyOverviewUiState = MoneyOverviewUiState(),
    val history: MoneyHistoryUiState = MoneyHistoryUiState(),
    val accounts: MoneyAccountsUiState = MoneyAccountsUiState(),
    val budgets: MoneyBudgetsUiState = MoneyBudgetsUiState(),
    val reports: MoneyReportsUiState = MoneyReportsUiState(),
)

sealed interface MoneyEvent {
    data class Commitments(val tab: String = "debts") : MoneyEvent
    data class Planning(val tab: String = "budgets", val budgetId: Long? = null) : MoneyEvent
    data object ImportReceipt : MoneyEvent
    data class ViewReceipt(val path: String) : MoneyEvent
    data class Navigate(val destination: MoneyDestination) : MoneyEvent
    data class Space(val id: Long?) : MoneyEvent
    data class Currency(val code: String) : MoneyEvent
    data class Period(val kind: MoneyPeriodKind) : MoneyEvent
    data class ShiftPeriod(val direction: Int) : MoneyEvent
    data class Range(val from: LocalDate, val through: LocalDate) : MoneyEvent
    data class LocaleChanged(val tag: String) : MoneyEvent
    data class Filters(val filters: MoneyFilters) : MoneyEvent
    data class ShowArchived(val show: Boolean) : MoneyEvent
    data class Appearance(val detailed: Boolean, val sections: Boolean) : MoneyEvent
    data class OpenPanel(val panel: String) : MoneyEvent
    data object Privacy : MoneyEvent
    data object Retry : MoneyEvent
    data class OpenEntry(val kind: MoneyEntryKind, val row: MoneyHistoryRow? = null,
                         val source: Long? = null, val targetPocket: Long? = null) : MoneyEvent
    data class Editor(val value: MoneyEditorUiState) : MoneyEvent
    data object SaveEntry : MoneyEvent
    data object CloseEditor : MoneyEvent
    data class EntryDate(val day: LocalDate) : MoneyEvent
    data class Delete(val id: Long) : MoneyEvent
    data object Undo : MoneyEvent
    data class DismissNotice(val id: Long) : MoneyEvent
    data class OpenAccount(val account: MoneyAccount? = null) : MoneyEvent
    data class AccountEditor(val value: MoneyAccountEditorUiState) : MoneyEvent
    data object SaveAccount : MoneyEvent
    data class Archive(val id: Long, val archived: Boolean) : MoneyEvent
    data class OpenPocket(val accountId: Long) : MoneyEvent
    data class PocketEditor(val value: MoneyPocketEditorUiState) : MoneyEvent
    data object SavePocket : MoneyEvent
    data class CustomName(val value: String) : MoneyEvent
    data object SaveCategory : MoneyEvent
    data object SaveSpace : MoneyEvent
}

sealed interface MoneyEffect {
    data class Commitments(val tab: String, val currency: String, val space: Long?) : MoneyEffect
    data class Planning(val tab: String, val budgetId: Long?, val currency: String, val space: Long?) : MoneyEffect
    data object ImportReceipt : MoneyEffect
    data class ViewReceipt(val path: String) : MoneyEffect
}
