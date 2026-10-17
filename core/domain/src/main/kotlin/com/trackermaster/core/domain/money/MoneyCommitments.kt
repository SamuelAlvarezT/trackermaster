package com.trackermaster.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class DebtKind { PAYABLE, RECEIVABLE }
enum class PrincipalEvent { DISBURSEMENT, REPAYMENT }
data class MoneyDebt(val id: String, val person: String, val kind: DebtKind, val principal: Money,
    val spaceId: Long, val due: LocalDate? = null, val note: String = "", val archived: Boolean = false,
    val version: Long = 0)
data class DebtPayment(val id: String, val debtId: String, val movementId: Long, val event: PrincipalEvent,
    val createdMovement: Boolean)
data class SavingsGoal(val id: String, val name: String, val target: Money, val pocketId: Long,
    val due: LocalDate? = null, val archived: Boolean = false, val version: Long = 0)
data class MoneyCommitments(val debts: List<MoneyDebt> = emptyList(), val payments: List<DebtPayment> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(), val plannedMovementIds: Set<Long> = emptySet())
data class DebtProgress(val debt: MoneyDebt, val paid: Money, val outstanding: Money, val overdue: Boolean)
data class GoalProgress(val goal: SavingsGoal, val reserved: Money, val remaining: Money, val percent: Int)

object CommitmentRules {
    fun debt(value: MoneyDebt, payments: List<DebtPayment>, movements: List<MoneyMovement>, today: LocalDate): DebtProgress {
        require(value.person.isNotBlank() && value.principal.minorUnits > 0)
        val byId = movements.associateBy { it.id }
        val paid = payments.filter { it.debtId == value.id && it.event == PrincipalEvent.REPAYMENT }
            .fold(Money(0, value.principal.currencyCode)) { total, payment ->
                val movement = requireNotNull(byId[payment.movementId])
                require(movement.principal && movement.direction == direction(value.kind, PrincipalEvent.REPAYMENT))
                total + movement.amount
            }
        val outstanding = value.principal - paid
        require(outstanding.minorUnits >= 0)
        return DebtProgress(value, paid, outstanding, outstanding.minorUnits > 0 && value.due?.isBefore(today) == true)
    }
    fun direction(kind: DebtKind, event: PrincipalEvent): MoneyDirection =
        if ((kind == DebtKind.PAYABLE) == (event == PrincipalEvent.DISBURSEMENT)) MoneyDirection.INCOME else MoneyDirection.EXPENSE

    fun goal(value: SavingsGoal, pocket: MoneyPocketBalance): GoalProgress {
        require(value.name.isNotBlank() && value.target.minorUnits > 0 && value.pocketId == pocket.pocket.id)
        require(value.target.currencyCode == pocket.balance.currencyCode && pocket.balance.minorUnits >= 0)
        val remaining = (value.target - pocket.balance).let { it.copy(minorUnits = it.minorUnits.coerceAtLeast(0)) }
        val percent = BigDecimal.valueOf(pocket.balance.minorUnits).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(value.target.minorUnits), 0, RoundingMode.DOWN).min(BigDecimal(100)).toInt()
        return GoalProgress(value, pocket.balance, remaining, percent)
    }
}
