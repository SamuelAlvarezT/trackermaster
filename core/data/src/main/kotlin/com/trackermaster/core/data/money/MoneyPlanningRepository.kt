package com.trackermaster.core.data.money

import androidx.room.withTransaction
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyPlanningRepository @Inject constructor(private val db: TrackermasterDatabase, private val money: MoneyRepository) {
    private val dao get() = db.moneyPlanningDao()
    val occurrences = dao.observeOccurrences().map { rows -> rows.map {
        MoneyPlanOccurrence(it.planId, LocalDate.ofEpochDay(it.dueEpochDay), it.status, it.movementId, it.fingerprint)
    } }
    suspend fun saveBudget(value: MoneyBudget): Long = db.withTransaction {
        BudgetRules.validate(value)
        if (value.categoryId != 0L) require(db.moneyDao().category(value.categoryId)?.isIncome == false)
        value.spaceId?.let { require(db.moneyDao().space(it) != null) }
        val old = if (value.id == 0L) null else requireNotNull(dao.budget(value.id))
        val limit = requireNotNull(value.limit)
        val row = (old ?: BudgetEntity(categoryId = value.categoryId, limitAmount = limit.decimal.toDouble())).copy(
            categoryId = value.categoryId, limitAmount = limit.decimal.toDouble(), limitAmountMinor = limit.minorUnits,
            currencyCode = limit.currencyCode, spaceId = value.spaceId, period = value.period,
            alertThresholdPercent = value.alertThreshold, startDateEpochDay = value.startDate?.toEpochDay(), endDateEpochDay = value.endDate?.toEpochDay())
        if (old == null) dao.insertBudget(row) else { dao.updateBudget(row); old.id }
    }
    suspend fun deleteBudget(id: Long) = db.withTransaction { requireNotNull(dao.budget(id)); dao.deleteBudget(id) }
    suspend fun savePlan(value: MoneyPlan) = db.withTransaction {
        PlanRules.validate(value)
        funding(value.accountId, value.pocketId, value.categoryId, value.currency, value.direction)
        val space=value.pocketId?.let { requireNotNull(db.moneyDao().pocket(it)).spaceId }
            ?: value.accountId?.let { requireNotNull(db.moneyDao().account(it)).spaceId } ?: value.spaceId
        space?.let { require(db.moneyDao().space(it)!=null) }
        val old = dao.plan(value.id)
        if (old == null) {
            if (value.version != 0L) throw PlanningException(PlanningFailure.STALE)
            dao.insertPlan(value.copy(name = value.name.trim(), version = 1, spaceId = space).entity())
        } else {
            if (old.archived || old.version != value.version) throw PlanningException(PlanningFailure.STALE)
            dao.updatePlan(value.copy(name = value.name.trim(), version = old.version + 1, spaceId = space).entity())
        }
    }
    private suspend fun funding(account: Long?, pocket: Long?, category: Long?, currency: String, direction: MoneyDirection) {
        account?.let {
            val row = requireNotNull(db.moneyDao().account(it))
            require(!row.archived && row.currencyCode == currency)
        }
        pocket?.let { require(db.moneyDao().pocket(it)?.accountId == account && account != null) }
        category?.let { require(db.moneyDao().category(it)?.isIncome == (direction == MoneyDirection.INCOME)) }
    }
    suspend fun status(id: String, version: Long, active: Boolean, archive: Boolean = false) = db.withTransaction {
        val row = requireNotNull(dao.plan(id))
        if (row.version != version || row.archived) throw PlanningException(PlanningFailure.STALE)
        dao.updatePlan(row.copy(active = active, archived = archive, version = version + 1))
    }
    suspend fun snooze(id: String, version: Long, remindOn: LocalDate) = db.withTransaction {
        val row = requireNotNull(dao.plan(id))
        if (row.version != version || !row.active || row.archived) throw PlanningException(PlanningFailure.STALE)
        dao.updatePlan(row.copy(remindOnEpochDay = remindOn.toEpochDay(), version = version + 1))
    }
    private suspend fun advance(plan: MoneyPlan) {
        val next = PlanRules.following(plan)
        dao.updatePlan(plan.copy(nextDue = next ?: plan.nextDue, active = next != null, remindOn = null, version = plan.version + 1).entity())
    }
    suspend fun skip(id: String, due: LocalDate, version: Long) = db.withTransaction {
        if (dao.occurrence(id, due.toEpochDay()) != null) return@withTransaction
        val plan = requireNotNull(dao.plan(id)).moneyPlan()
        if (!plan.active || plan.archived || plan.nextDue != due || plan.version != version) throw PlanningException(PlanningFailure.STALE)
        dao.insertOccurrence(MoneyPlanOccurrenceEntity(id, due.toEpochDay(), "SKIPPED", null, ""))
        advance(plan)
    }
    /** Stable occurrence key, ledger entry and schedule advancement are committed atomically. */
    suspend fun confirm(id: String, due: LocalDate, version: Long, movement: MoneyMovement, linkedId: Long? = null): Long = db.withTransaction {
        require(movement.id == 0L && movement.transferId == null && !movement.principal && movement.amount.minorUnits > 0)
        val fingerprint = listOf(movement.accountId, movement.pocketId, movement.categoryId, movement.amount.minorUnits,
            movement.amount.currencyCode, movement.direction, movement.date, movement.note, linkedId).joinToString("\u001f")
        dao.occurrence(id, due.toEpochDay())?.let { old ->
            if (old.status != "COMPLETED" || old.fingerprint != fingerprint) throw PlanningException(PlanningFailure.CONFLICT)
            val existing = old.movementId?.let { db.moneyDao().movement(it) } ?: throw PlanningException(PlanningFailure.STALE)
            return@withTransaction existing.id
        }
        val plan = requireNotNull(dao.plan(id)).moneyPlan()
        if (plan.archived || !plan.active || plan.nextDue != due || plan.version != version) throw PlanningException(PlanningFailure.STALE)
        require(plan.currency == movement.amount.currencyCode && plan.direction == movement.direction)
        funding(movement.accountId, movement.pocketId, requireNotNull(movement.categoryId), movement.amount.currencyCode, movement.direction)
        val account = requireNotNull(db.moneyDao().account(movement.accountId))
        val space = movement.pocketId?.let { requireNotNull(db.moneyDao().pocket(it)).spaceId } ?: account.spaceId
        require(movement.spaceId == space)
        require(plan.spaceId==null || plan.spaceId==space)
        val ledgerId = if (linkedId != null) {
            if (dao.linkedMovement(linkedId) != null) throw PlanningException(PlanningFailure.ALREADY_LINKED)
            val row = requireNotNull(db.moneyDao().movement(linkedId))
            require(!row.principal && row.transferId == null && row.accountId == movement.accountId && row.pocketId == movement.pocketId &&
                row.categoryId == movement.categoryId && row.type == movement.direction.name && row.amountMinor == movement.amount.minorUnits &&
                row.dateEpochDay == movement.date.toEpochDay())
            linkedId
        } else money.record(movement.copy(entryRequestId = "plan:$id:${due.toEpochDay()}"))
        dao.insertOccurrence(MoneyPlanOccurrenceEntity(id, due.toEpochDay(), "COMPLETED", ledgerId, fingerprint))
        advance(plan)
        ledgerId
    }
    suspend fun budgetSnapshot() = dao.budgets().map { it.moneyBudget() }
    suspend fun planSnapshot() = dao.plans().map { it.moneyPlan() }
    /** A short lease permits recovery if a process dies before posting the notification. */
    suspend fun claimAlert(key: String, now: Long): String? = db.withTransaction {
        val old = dao.alert(key)
        if (old != null && (old.delivered || (now >= old.claimedAtEpoch && now - old.claimedAtEpoch < 600_000))) return@withTransaction null
        val token = UUID.randomUUID().toString()
        dao.saveAlert(MoneyPlanningAlertEntity(key, token, now, false)); token
    }
    suspend fun alertDelivered(key: String, token: String) = dao.deliverAlert(key, token)
    suspend fun releaseAlert(key: String, token: String) = dao.releaseAlert(key, token)
}
