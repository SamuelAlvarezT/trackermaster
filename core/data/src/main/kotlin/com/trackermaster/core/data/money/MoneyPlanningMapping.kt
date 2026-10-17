package com.trackermaster.core.data.money

import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.money.*
import java.time.LocalDate

internal fun BudgetEntity.moneyBudget(): MoneyBudget {
    val minor = limitAmountMinor
    val code = currencyCode
    return MoneyBudget(id, categoryId, limitAmount, if (minor != null && code != null) Money(minor, code) else null,
        spaceId, period, alertThresholdPercent, startDateEpochDay?.let(LocalDate::ofEpochDay), endDateEpochDay?.let(LocalDate::ofEpochDay))
}
internal fun MoneyPlanEntity.moneyPlan() = MoneyPlan(id, name, MoneyPlanKind.valueOf(kind), MoneyDirection.valueOf(direction),
    amountMinor?.let { Money(it, currencyCode) }, currencyCode, accountId, pocketId, categoryId, LocalDate.ofEpochDay(anchorEpochDay),
    LocalDate.ofEpochDay(nextDueEpochDay), MoneyFrequency.valueOf(frequency), reminderDays, remindOnEpochDay?.let(LocalDate::ofEpochDay),
    note, active, archived, version, plannedSpaceId)
internal fun MoneyPlan.entity() = MoneyPlanEntity(id, name, kind.name, direction.name, amount?.minorUnits, currency, accountId,
    pocketId, categoryId, anchor.toEpochDay(), nextDue.toEpochDay(), frequency.name, reminderDays, remindOn?.toEpochDay(),
    note, active, archived, version, spaceId)
