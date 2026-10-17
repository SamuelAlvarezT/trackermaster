package com.trackermaster.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

enum class BudgetStatus { OK, NEAR, LIMIT, EXCEEDED }
data class BudgetProgress(val spent: Money, val remaining: Money, val percent: Int,
    val status: BudgetStatus, val from: LocalDate, val through: LocalDate, val inPeriod: Boolean)

object BudgetRules {
    fun dates(budget: MoneyBudget, day: LocalDate, locale: Locale): Pair<LocalDate, LocalDate> = when (budget.period) {
        "WEEKLY" -> day.with(WeekFields.of(locale).dayOfWeek(), 1).let { it to it.plusDays(6) }
        "MONTHLY" -> day.withDayOfMonth(1) to day.withDayOfMonth(day.lengthOfMonth())
        "YEARLY" -> day.withDayOfYear(1) to day.withDayOfYear(day.lengthOfYear())
        "CUSTOM" -> requireNotNull(budget.startDate) to requireNotNull(budget.endDate)
        else -> throw IllegalArgumentException("Invalid budget period")
    }
    fun validate(budget: MoneyBudget) {
        require(requireNotNull(budget.limit).minorUnits > 0)
        require(budget.limit.currencyCode in setOf("COP", "USD"))
        require(budget.alertThreshold in 1..100)
        require(budget.categoryId >= 0)
        val range = dates(budget, LocalDate.of(2026, 1, 1), Locale.ROOT)
        require(!range.second.isBefore(range.first))
    }
    fun progress(budget: MoneyBudget, movements: List<MoneyMovement>, day: LocalDate, locale: Locale): BudgetProgress? {
        val limit = budget.limit ?: return null // Legacy currency must be explicitly configured.
        validate(budget)
        val (from, through) = dates(budget, day, locale)
        val spent = movements.asSequence().filter { it.transferId == null && !it.principal && it.direction == MoneyDirection.EXPENSE &&
            it.amount.currencyCode == limit.currencyCode && (budget.spaceId == null || it.spaceId == budget.spaceId) &&
            (budget.categoryId == 0L || it.categoryId == budget.categoryId) && !it.date.isBefore(from) && !it.date.isAfter(through) }
            .fold(Money(0, limit.currencyCode)) { total, row -> total + row.amount }
        val ratio = BigDecimal.valueOf(spent.minorUnits).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(limit.minorUnits), 8, RoundingMode.DOWN)
        val status = when {
            spent.minorUnits > limit.minorUnits -> BudgetStatus.EXCEEDED
            spent.minorUnits == limit.minorUnits -> BudgetStatus.LIMIT
            ratio >= BigDecimal(budget.alertThreshold) -> BudgetStatus.NEAR
            else -> BudgetStatus.OK
        }
        return BudgetProgress(spent, limit - spent, ratio.min(BigDecimal(Int.MAX_VALUE)).toInt(), status, from, through,
            !day.isBefore(from) && !day.isAfter(through))
    }
}

enum class MoneyPlanKind { PAYMENT, PURCHASE, ESTIMATE }
enum class MoneyFrequency { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }
data class MoneyPlan(val id: String, val name: String, val kind: MoneyPlanKind, val direction: MoneyDirection,
    val amount: Money?, val currency: String, val accountId: Long?, val pocketId: Long?, val categoryId: Long?,
    val anchor: LocalDate, val nextDue: LocalDate, val frequency: MoneyFrequency, val reminderDays: Int = 0,
    val remindOn: LocalDate? = null, val note: String = "", val active: Boolean = true,
    val archived: Boolean = false, val version: Long = 0, val spaceId: Long? = null)
data class MoneyPlanOccurrence(val planId: String, val due: LocalDate, val status: String,
    val movementId: Long?, val fingerprint: String)
enum class PlanningFailure { INVALID, STALE, FUNDS, ALREADY_LINKED, CONFLICT }
class PlanningException(val reason: PlanningFailure) : IllegalArgumentException(reason.name)

object PlanRules {
    fun validate(plan: MoneyPlan) {
        require(plan.id.isNotBlank() && plan.name.isNotBlank() && plan.name.length <= 120)
        require(plan.currency in setOf("COP", "USD"))
        require(plan.reminderDays in 0..30 && plan.note.length <= 2000)
        require(plan.kind == MoneyPlanKind.PAYMENT || plan.direction == MoneyDirection.EXPENSE)
        if (plan.kind != MoneyPlanKind.PURCHASE) require(plan.amount != null)
        plan.amount?.let { require(it.currencyCode == plan.currency && it.minorUnits > 0) }
    }
    /** Clamp short months without losing the original 29th/30th/31st or leap-year anchor. */
    fun following(plan: MoneyPlan): LocalDate? = when (plan.frequency) {
        MoneyFrequency.ONCE -> null
        MoneyFrequency.DAILY -> plan.nextDue.plusDays(1)
        MoneyFrequency.WEEKLY -> plan.nextDue.plusWeeks(1)
        MoneyFrequency.MONTHLY -> YearMonth.from(plan.nextDue).plusMonths(1).let { it.atDay(minOf(plan.anchor.dayOfMonth, it.lengthOfMonth())) }
        MoneyFrequency.YEARLY -> YearMonth.of(plan.nextDue.year + 1, plan.anchor.monthValue)
            .let { it.atDay(minOf(plan.anchor.dayOfMonth, it.lengthOfMonth())) }
    }
    fun reminderDate(plan: MoneyPlan): LocalDate = plan.remindOn ?: plan.nextDue.minusDays(plan.reminderDays.toLong())
    fun forecast(plans: List<MoneyPlan>, from: LocalDate, through: LocalDate, currency: String, direction: MoneyDirection): Money {
        require(!through.isBefore(from) && through.toEpochDay() - from.toEpochDay() <= 366)
        return plans.filter { it.active && !it.archived && it.currency == currency && it.direction == direction && it.amount != null }
            .fold(Money(0, currency)) { total, original ->
                var plan = onOrAfter(original, from)
                var sum = total
                while (!plan.nextDue.isAfter(through)) {
                    if (!plan.nextDue.isBefore(from)) sum += requireNotNull(plan.amount)
                    val next = following(plan) ?: break
                    plan = plan.copy(nextDue = next)
                }
                sum
            }
    }
    private fun onOrAfter(plan: MoneyPlan, from: LocalDate): MoneyPlan {
        if (!plan.nextDue.isBefore(from) || plan.frequency == MoneyFrequency.ONCE) return plan
        val days = java.time.temporal.ChronoUnit.DAYS.between(plan.nextDue, from)
        val candidate = when (plan.frequency) {
            MoneyFrequency.DAILY -> plan.nextDue.plusDays(days)
            MoneyFrequency.WEEKLY -> plan.nextDue.plusWeeks((days + 6) / 7)
            MoneyFrequency.MONTHLY -> YearMonth.from(from).let { it.atDay(minOf(plan.anchor.dayOfMonth, it.lengthOfMonth())) }
            MoneyFrequency.YEARLY -> YearMonth.of(from.year, plan.anchor.monthValue).let { it.atDay(minOf(plan.anchor.dayOfMonth, it.lengthOfMonth())) }
            MoneyFrequency.ONCE -> plan.nextDue
        }
        val updated = plan.copy(nextDue = candidate)
        return if (candidate.isBefore(from)) updated.copy(nextDue = requireNotNull(following(updated))) else updated
    }
}
