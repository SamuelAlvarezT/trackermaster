package com.trackermaster.core.domain.money

import java.time.LocalDate

enum class InvestmentEventKind { CONTRIBUTION, WITHDRAWAL, DIVIDEND, VALUATION }

/** Existing holdings start with a cost basis and value; creating them never invents cash. */
data class MoneyInvestment(val id: String, val name: String, val currency: String, val spaceId: Long,
    val opened: LocalDate, val initialCost: Money, val initialValue: Money,
    val note: String = "", val archived: Boolean = false, val version: Long = 0)

data class InvestmentEvent(val id: String, val investmentId: String, val kind: InvestmentEventKind,
    val amount: Money, val date: LocalDate, val cost: Money = amount,
    val movementId: Long? = null, val sequence: Long = 0, val note: String = "")

data class InvestmentBook(val positions: List<MoneyInvestment> = emptyList(), val events: List<InvestmentEvent> = emptyList())
data class InvestmentProgress(val investment: MoneyInvestment, val cost: Money, val value: Money,
    val realized: Money, val unrealized: Money, val valuedOn: LocalDate)

object InvestmentRules {
    fun progress(position: MoneyInvestment, events: List<InvestmentEvent>, through: LocalDate): InvestmentProgress {
        require(position.currency in setOf("COP", "USD"))
        require(position.initialCost.currencyCode == position.currency && position.initialValue.currencyCode == position.currency)
        require(position.initialCost.minorUnits >= 0 && position.initialValue.minorUnits >= 0 && !through.isBefore(position.opened))
        var cost = position.initialCost
        var value = position.initialValue
        var realized = Money(0, position.currency)
        var valuedOn = position.opened
        events.filter { it.investmentId == position.id && !it.date.isAfter(through) }
            .sortedWith(compareBy<InvestmentEvent> { it.date }.thenBy { it.sequence }).forEach { event ->
                require(!event.date.isBefore(position.opened))
                require(event.amount.currencyCode == position.currency && event.cost.currencyCode == position.currency)
                require(event.amount.minorUnits >= 0 && event.cost.minorUnits >= 0)
                when (event.kind) {
                    InvestmentEventKind.CONTRIBUTION -> { require(event.amount.minorUnits > 0); cost += event.amount; value += event.amount }
                    InvestmentEventKind.WITHDRAWAL -> {
                        require(event.amount.minorUnits > 0 && event.cost.minorUnits <= cost.minorUnits)
                        cost -= event.cost
                        value = (value - event.amount).let { it.copy(minorUnits = it.minorUnits.coerceAtLeast(0)) }
                        realized += event.amount - event.cost
                    }
                    InvestmentEventKind.DIVIDEND -> { require(event.amount.minorUnits > 0); realized += event.amount }
                    InvestmentEventKind.VALUATION -> { value = event.amount; valuedOn = event.date }
                }
            }
        return InvestmentProgress(position, cost, value, realized, value - cost, valuedOn)
    }

    /** Goals are already contained in cash. Contributions leave cash before adding the asset. */
    fun assets(cash: Money, positions: List<InvestmentProgress>): Money = positions.fold(cash) { sum, row ->
        require(row.value.currencyCode == cash.currencyCode)
        sum + row.value
    }
}
