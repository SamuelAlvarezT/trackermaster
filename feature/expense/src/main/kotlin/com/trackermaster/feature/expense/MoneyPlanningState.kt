package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import java.time.LocalDate
import java.util.UUID

data class BudgetForm(val id: Long = 0, val amount: String = "", val currency: String = "COP", val category: Long = 0,
    val space: Long? = null, val period: String = "MONTHLY", val threshold: String = "80",
    val from: String = LocalDate.now().toString(), val through: String = LocalDate.now().plusMonths(1).toString())
data class PlanForm(val id: String = UUID.randomUUID().toString(), val name: String = "", val kind: MoneyPlanKind = MoneyPlanKind.PAYMENT,
    val direction: MoneyDirection = MoneyDirection.EXPENSE, val amount: String = "", val currency: String = "COP",
    val account: Long? = null, val pocket: Long? = null, val category: Long? = null, val due: String = LocalDate.now().toString(),
    val frequency: MoneyFrequency = MoneyFrequency.MONTHLY, val reminderDays: String = "0", val note: String = "", val version: Long = 0,
    val anchor: String? = null, val originalDue: String? = null, val active: Boolean = true, val space: Long? = null)
data class SettlementForm(val planId: String, val due: String, val version: Long, val name: String, val currency: String,
    val direction: MoneyDirection, val amount: String, val account: Long?, val pocket: Long?, val category: Long?,
    val date: String = LocalDate.now().toString(), val note: String = "", val linked: Long? = null, val space: Long? = null)
enum class PlanningUiError { LOAD, INVALID, SAVE, STALE, FUNDS, LINKED }
data class PlanningControls(val tab: String = "budgets", val currency: String = "COP", val space: Long? = null,
    val today: LocalDate = LocalDate.now(), val budget: BudgetForm? = null, val plan: PlanForm? = null,
    val settlement: SettlementForm? = null, val dateField: String? = null, val error: PlanningUiError? = null,
    val saving: Boolean = false, val deleteBudget: Long? = null, val deletePlan: MoneyPlan? = null,
    val notice: Long = 0, val privacyOverride: Boolean? = null)
data class MoneyPlanningUiState(val loading: Boolean = true, val workspace: MoneyWorkspaceData? = null,
    val settings: AppSettings = AppSettings(), val preferences: MoneyPreferences = MoneyPreferences(hideAmounts=true),
    val controls: PlanningControls = PlanningControls(), val budgets: List<MoneyBudgetItem> = emptyList(),
    val plans: List<MoneyPlan> = emptyList(), val occurrences: List<MoneyPlanOccurrence> = emptyList(),
    val candidates: List<MoneyMovement> = emptyList(), val canSave: Boolean = false, val notificationsAllowed: Boolean = false,
    val forecast: Money = Money(0,"COP"))

sealed interface PlanningEvent {
    data object Retry : PlanningEvent
    data object Privacy : PlanningEvent
    data object Refresh : PlanningEvent
    data class Tab(val value: String) : PlanningEvent
    data class Currency(val value: String) : PlanningEvent
    data class Space(val value: Long?) : PlanningEvent
    data class OpenBudget(val id: Long? = null) : PlanningEvent
    data class Budget(val value: BudgetForm) : PlanningEvent
    data class OpenPlan(val value: MoneyPlan? = null) : PlanningEvent
    data class Plan(val value: PlanForm) : PlanningEvent
    data class OpenSettlement(val plan: MoneyPlan) : PlanningEvent
    data class Settlement(val value: SettlementForm) : PlanningEvent
    data class Link(val id: Long?) : PlanningEvent
    data object Save : PlanningEvent
    data object CloseEditor : PlanningEvent
    data class DateField(val field: String?) : PlanningEvent
    data class Date(val value: LocalDate) : PlanningEvent
    data class Toggle(val plan: MoneyPlan) : PlanningEvent
    data class Skip(val plan: MoneyPlan) : PlanningEvent
    data class Snooze(val plan: MoneyPlan) : PlanningEvent
    data class DeleteBudget(val id: Long?) : PlanningEvent
    data class DeletePlan(val plan: MoneyPlan?) : PlanningEvent
    data object ConfirmDelete : PlanningEvent
}

internal fun planningBudgetItem(budget: MoneyBudget, data: MoneyWorkspaceData, day: LocalDate, locale: java.util.Locale) = MoneyBudgetItem(
    budget.id, data.categories.find { it.id == budget.categoryId }, budget.legacyAmount, budget.limit, budget.spaceId, budget.period,
    BudgetRules.progress(budget, data.ledger.movements, day, locale), budget.categoryId == 0L)
