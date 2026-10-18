package com.trackermaster.core.data.money

import androidx.room.withTransaction
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyCommitmentRepository @Inject constructor(private val db: TrackermasterDatabase, private val money: MoneyRepository) {
    private val dao get() = db.moneyCommitmentDao()
    suspend fun dueDebts(today: LocalDate): List<DebtProgress> = db.withTransaction {
        val movements=db.moneyDao().readLedger().moneyMovements()
        dao.debts().filter{!it.archived && it.dueEpochDay?.let{day->day<=today.toEpochDay()}==true}.map{row->
            CommitmentRules.debt(row.domain(),dao.payments(row.id).map{it.domain()},movements,today)
        }.filter{it.outstanding.minorUnits>0}
    }
    val data = combine(dao.observeDebts(), dao.observePayments(), dao.observeGoals(), db.moneyPlanningDao().observeOccurrences()) { debts, payments, goals, occurrences ->
        MoneyCommitments(debts.map { it.domain() }, payments.map { it.domain() }, goals.map { it.domain() }, occurrences.mapNotNull { it.movementId }.toSet())
    }

    /** Existing balances do not create cash. A new loan may include its actual disbursement. */
    suspend fun createDebt(value: MoneyDebt, disbursement: MoneyMovement? = null) = db.withTransaction {
        require(value.id.isNotBlank() && value.version == 0L && !value.archived)
        require(value.person.isNotBlank() && value.principal.minorUnits > 0 && value.principal.currencyCode in setOf("COP", "USD"))
        requireNotNull(db.moneyDao().space(value.spaceId))
        val old = dao.debt(value.id)
        if (old != null) {
            require(old == value.entity())
            val opening = dao.payment("opening:${value.id}")
            require((opening != null) == (disbursement != null))
            if (opening != null) require(same(requireNotNull(disbursement), opening.movementId))
            return@withTransaction
        }
        dao.insertDebt(value.entity())
        if (disbursement != null) {
            validate(value, PrincipalEvent.DISBURSEMENT, disbursement)
            require(disbursement.amount == value.principal)
            val id = money.recordPrincipal(disbursement.copy(principal = true, categoryId = null, entryRequestId = "debt:opening:${value.id}"))
            dao.insertPayment(DebtPaymentEntity("opening:${value.id}", value.id, id, PrincipalEvent.DISBURSEMENT.name, true))
        }
    }

    suspend fun updateDebt(value: MoneyDebt) = db.withTransaction {
        val old = requireNotNull(dao.debt(value.id))
        require(old.version == value.version && value.person.isNotBlank() && value.principal.minorUnits > 0)
        require(value.principal.currencyCode in setOf("COP","USD"))
        requireNotNull(db.moneyDao().space(value.spaceId))
        val payments=dao.payments(old.id)
        if(payments.isNotEmpty()) require(old.currency == value.principal.currencyCode && old.spaceId == value.spaceId && old.kind == value.kind.name)
        val movements=db.moneyDao().readLedger().moneyMovements()
        val progress=CommitmentRules.debt(value,payments.map{it.domain()},movements,LocalDate.now())
        if(value.archived && !old.archived) require(progress.outstanding.minorUnits==0L)
        if(value.principal.minorUnits!=old.principalMinor) payments.singleOrNull{it.event==PrincipalEvent.DISBURSEMENT.name}?.let { opening ->
            require(opening.createdMovement)
            val row=requireNotNull(db.moneyDao().movement(opening.movementId))
            require(row.principal)
            db.moneyDao().updateMovement(row.copy(amountMinor=value.principal.minorUnits,amount=value.principal.decimal.toDouble()))
            validateReserves()
        }
        dao.updateDebt(value.entity().copy(version = Math.incrementExact(value.version)))
    }

    suspend fun repay(debtId: String, version: Long, request: String, movement: MoneyMovement, linkedId: Long? = null): Long = db.withTransaction {
        require(request.isNotBlank() && !request.startsWith("opening:"))
        val debt = requireNotNull(dao.debt(debtId)).domain()
        dao.payment(request)?.let { old ->
            require(old.debtId == debtId && old.event == PrincipalEvent.REPAYMENT.name && same(movement, old.movementId))
            require(old.createdMovement == (linkedId == null) && (linkedId == null || linkedId == old.movementId))
            return@withTransaction old.movementId
        }
        require(!debt.archived && debt.version == version)
        validate(debt, PrincipalEvent.REPAYMENT, movement)
        val progress = CommitmentRules.debt(debt, dao.payments(debtId).map { it.domain() },
            db.moneyDao().readLedger().moneyMovements(), LocalDate.now())
        require(movement.amount.minorUnits <= progress.outstanding.minorUnits) { "Overpayment" }
        val id = if (linkedId == null) money.recordPrincipal(movement.copy(principal = true, categoryId = null, entryRequestId = "debt:$request")) else {
            require(dao.linked(linkedId) == null && db.moneyPlanningDao().linkedMovement(linkedId) == null)
            val row = requireNotNull(db.moneyDao().movement(linkedId))
            require(!row.principal && row.investmentId == null && same(movement, linkedId))
            db.moneyDao().updateMovement(row.copy(principal = true))
            linkedId
        }
        dao.insertPayment(DebtPaymentEntity(request, debtId, id, PrincipalEvent.REPAYMENT.name, linkedId == null))
        dao.updateDebt(debt.copy(version = Math.incrementExact(version)).entity())
        id
    }

    /** Undo a repayment atomically. Imported movements are unlinked; newly created ones are removed. */
    suspend fun reversePayment(id: String, version: Long) = db.withTransaction {
        val payment = requireNotNull(dao.payment(id))
        require(payment.event == PrincipalEvent.REPAYMENT.name)
        val debt = requireNotNull(dao.debt(payment.debtId))
        require(debt.version == version && !debt.archived)
        val row = requireNotNull(db.moneyDao().movement(payment.movementId))
        if (payment.createdMovement) db.moneyDao().deleteMovement(row.id)
        else db.moneyDao().updateMovement(row.copy(principal = false))
        dao.deletePayment(id)
        // Removing an incoming repayment cannot overdraw another reservation.
        validateReserves()
        dao.updateDebt(debt.copy(version = Math.incrementExact(version)))
    }

    suspend fun createGoalWithPocket(value: SavingsGoal, accountId: Long, spaceId: Long) = db.withTransaction {
        require(value.pocketId == 0L && value.version == 0L && value.name.isNotBlank() && value.target.minorUnits > 0)
        dao.goal(value.id)?.let { existing ->
            require(existing.name == value.name.trim() && existing.targetMinor == value.target.minorUnits && existing.currency == value.target.currencyCode)
            val pocket = requireNotNull(db.moneyDao().pocket(existing.pocketId))
            require(pocket.accountId == accountId && pocket.spaceId == spaceId)
            return@withTransaction
        }
        val account = requireNotNull(db.moneyDao().account(accountId))
        require(account.currencyCode.equals(value.target.currencyCode,ignoreCase=true))
        val pocket = money.createPocket(accountId, spaceId, value.name)
        saveGoal(value.copy(pocketId = pocket))
    }

    suspend fun saveGoal(value: SavingsGoal) = db.withTransaction {
        require(value.id.isNotBlank() && value.name.isNotBlank() && value.target.minorUnits > 0 && value.target.currencyCode in setOf("COP","USD"))
        val pocket = money.snapshot().pockets.single { it.pocket.id == value.pocketId }
        CommitmentRules.goal(value, pocket)
        val account = requireNotNull(db.moneyDao().account(pocket.pocket.accountId))
        val old = dao.goal(value.id)
        if (old == null) {
            require(value.version == 0L && !value.archived && !account.archived)
            dao.insertGoal(value.entity())
        } else {
            require(old.version == value.version && old.pocketId == value.pocketId && old.currency == value.target.currencyCode)
            dao.updateGoal(value.entity().copy(version = Math.incrementExact(value.version)))
        }
    }

    suspend fun allocate(goalId: String, version: Long, amount: Money, sourceAccount: Long, withdraw: Boolean,
        date: LocalDate, request: String) = db.withTransaction {
        require(request.isNotBlank())
        val goal = requireNotNull(dao.goal(goalId)).domain()
        require(!goal.archived && goal.target.currencyCode == amount.currencyCode && amount.minorUnits > 0)
        val pocket = requireNotNull(db.moneyDao().pocket(goal.pocketId))
        val token = "goal:$goalId:$request"
        // Retry is checked by the transfer's complete immutable fingerprint.
        if (db.moneyDao().transfer(token) == null) require(goal.version == version)
        if (withdraw) money.transfer(pocket.accountId, sourceAccount, amount, sourcePocketId = pocket.id,
            date = date, requestId = token)
        else money.transfer(sourceAccount, pocket.accountId, amount, targetPocketId = pocket.id,
            date = date, requestId = token)
    }

    private fun validate(debt: MoneyDebt, event: PrincipalEvent, movement: MoneyMovement) {
        require(movement.id == 0L && movement.transferId == null && movement.amount.minorUnits > 0)
        require(movement.spaceId == debt.spaceId && movement.amount.currencyCode == debt.principal.currencyCode && movement.direction == CommitmentRules.direction(debt.kind, event))
    }
    private suspend fun validateReserves() {
        val snapshot=money.snapshot()
        val reserved=snapshot.pockets.map{it.pocket.accountId}.toSet()
        if(snapshot.pockets.any{it.balance.minorUnits<0} || snapshot.accounts.any{it.account.id in reserved && it.unallocated.minorUnits<0})
            throw MoneyWriteException(MoneyWriteFailure.INSUFFICIENT_FUNDS)
    }
    private suspend fun same(value: MoneyMovement, id: Long): Boolean {
        val row = db.moneyDao().movement(id) ?: return false
        val account = db.moneyDao().account(row.accountId) ?: return false
        val current = row.moneyMovement(account.currencyCode)
        return current.transferId == null && current.amount == value.amount && current.direction == value.direction && current.accountId == value.accountId && current.pocketId == value.pocketId && current.spaceId == value.spaceId && current.date == value.date && current.note == value.note
    }
}

internal fun MoneyDebtEntity.domain() = MoneyDebt(id, person, DebtKind.valueOf(kind), Money(principalMinor, currency), spaceId, dueEpochDay?.let(LocalDate::ofEpochDay), note, archived, version)
internal fun MoneyDebt.entity() = MoneyDebtEntity(id, person.trim(), kind.name, principal.minorUnits, principal.currencyCode, spaceId, due?.toEpochDay(), note, archived, version)
internal fun DebtPaymentEntity.domain() = DebtPayment(id, debtId, movementId, PrincipalEvent.valueOf(event), createdMovement)
internal fun SavingsGoalEntity.domain() = SavingsGoal(id, name, Money(targetMinor, currency), pocketId, dueEpochDay?.let(LocalDate::ofEpochDay), archived, version)
internal fun SavingsGoal.entity() = SavingsGoalEntity(id, name.trim(), target.minorUnits, target.currencyCode, pocketId, due?.toEpochDay(), archived, version)
