package com.trackermaster.core.data.money

import androidx.room.withTransaction
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyInvestmentRepository @Inject constructor(private val db: TrackermasterDatabase, private val money: MoneyRepository) {
    private val dao get() = db.moneyInvestmentDao()
    val data = dao.observeBook().map { rows ->
        InvestmentBook(rows.map { it.position.domain() },rows.flatMap { row -> row.events.map { it.domain(row.position.currency) } })
    }

    suspend fun create(value: MoneyInvestment) = db.withTransaction {
        require(value.id.isNotBlank() && value.name.isNotBlank() && value.version == 0L && !value.archived)
        require(!value.opened.isAfter(LocalDate.now()))
        requireNotNull(db.moneyDao().space(value.spaceId))
        InvestmentRules.progress(value, emptyList(), LocalDate.now())
        val entity = value.entity()
        dao.position(value.id)?.let { require(it == entity); return@withTransaction }
        dao.insert(entity)
    }

    suspend fun details(id: String, version: Long, name: String, note: String, archived: Boolean,
        initialCost: Money? = null, initialValue: Money? = null, opened: LocalDate? = null) = db.withTransaction {
        val old = requireNotNull(dao.position(id))
        require(old.version == version && name.isNotBlank())
        require(initialCost == null || initialCost.currencyCode == old.currency)
        require(initialValue == null || initialValue.currencyCode == old.currency)
        val updated = old.copy(name = name.trim(), note = note, archived = archived,
            initialCostMinor = initialCost?.minorUnits ?: old.initialCostMinor,
            initialValueMinor = initialValue?.minorUnits ?: old.initialValueMinor,
            openedEpochDay = opened?.toEpochDay() ?: old.openedEpochDay, version = Math.incrementExact(version))
        val progress = InvestmentRules.progress(updated.domain(), dao.events(id).map { it.domain(old.currency) }, LocalDate.now())
        if (archived) {
            require(progress.cost.minorUnits == 0L && progress.value.minorUnits == 0L)
        }
        dao.update(updated)
    }

    /** Event, classification, cash and optimistic version are committed together. */
    suspend fun record(id: String, version: Long, event: InvestmentEvent, movement: MoneyMovement? = null, linked: Long? = null) = db.withTransaction {
        val position = requireNotNull(dao.position(id))
        require(event.id.isNotBlank() && event.investmentId == id && !event.date.isAfter(LocalDate.now()))
        require(event.amount.currencyCode == position.currency && event.cost.currencyCode == position.currency)
        val capital = event.kind != InvestmentEventKind.DIVIDEND
        val canonical = movement?.copy(id = 0, principal = false, investmentId = id, investmentCapital = capital,
            categoryId = if (capital) null else movement.categoryId, entryRequestId = "investment:${event.id}")
        dao.request(event.id)?.let { old ->
            require(old.investmentId == id && old.kind == event.kind.name && old.amountMinor == event.amount.minorUnits &&
                old.costMinor == event.cost.minorUnits && old.dateEpochDay == event.date.toEpochDay() && old.note == event.note)
            require(old.createdMovement == (movement != null && linked == null))
            if (canonical != null) {
                val row = requireNotNull(db.moneyDao().movement(requireNotNull(old.movementId)))
                require(row.investmentId == id && row.investmentCapital == capital)
                require(cashMatches(row.moneyMovement(position.currency), requireNotNull(movement)))
                if (!capital) require(row.categoryId == requireNotNull(movement).categoryId)
                require(linked == null || linked == old.movementId)
            } else require(old.movementId == null)
            return@withTransaction
        }
        require(position.version == version && !position.archived)
        val domain = position.domain()
        val events = dao.events(id).map { it.domain(position.currency) }
        InvestmentRules.progress(domain, events + event.copy(sequence = Long.MAX_VALUE), LocalDate.now())
        val movementId = if (event.kind == InvestmentEventKind.VALUATION) {
            require(movement == null && linked == null)
            null
        } else {
            val cash = requireNotNull(canonical)
            require(cash.amount == event.amount && cash.date == event.date && cash.transferId == null)
            val direction = if (event.kind == InvestmentEventKind.CONTRIBUTION) MoneyDirection.EXPENSE else MoneyDirection.INCOME
            require(cash.direction == direction && cash.spaceId == position.spaceId)
            val account = requireNotNull(db.moneyDao().account(cash.accountId))
            require(!account.archived && account.currencyCode.equals(position.currency, true))
            val pocket = cash.pocketId?.let { requireNotNull(db.moneyDao().pocket(it)).also { p -> require(p.accountId == cash.accountId) } }
            require((pocket?.spaceId ?: account.spaceId) == position.spaceId)
            if (!capital) requireNotNull(cash.categoryId?.let { db.moneyDao().category(it) }).also { require(it.isIncome) }
            if (linked != null) {
                val row = requireNotNull(db.moneyDao().movement(linked))
                require(!row.principal && row.investmentId == null && row.transferId == null)
                require(db.moneyPlanningDao().linkedMovement(linked) == null && db.moneyCommitmentDao().linked(linked) == null)
                require(cashMatches(row.moneyMovement(position.currency), requireNotNull(movement)))
                if (!capital) require(row.categoryId == cash.categoryId)
                db.moneyDao().updateMovement(row.copy(investmentId = id, investmentCapital = capital))
                linked
            } else {
                if (event.kind == InvestmentEventKind.CONTRIBUTION) {
                    val snap = money.snapshot()
                    val available = cash.pocketId?.let { p -> snap.pockets.single { it.pocket.id == p }.balance }
                        ?: snap.accounts.single { it.account.id == cash.accountId }.unallocated
                    if (available.minorUnits < cash.amount.minorUnits) throw MoneyWriteException(MoneyWriteFailure.INSUFFICIENT_FUNDS)
                }
                money.recordInvestment(cash)
            }
        }
        dao.insertEvent(MoneyInvestmentEventEntity(requestId = event.id, investmentId = id, kind = event.kind.name,
            amountMinor = event.amount.minorUnits, costMinor = event.cost.minorUnits, dateEpochDay = event.date.toEpochDay(),
            movementId = movementId, createdMovement = movement != null && linked == null, note = event.note))
        dao.update(position.copy(version = Math.incrementExact(version)))
    }

    /** Reversal restores imported receipts instead of deleting them. Later events are revalidated. */
    suspend fun reverse(sequence: Long, version: Long) = db.withTransaction {
        val event = requireNotNull(dao.event(sequence))
        val position = requireNotNull(dao.position(event.investmentId))
        require(position.version == version && !position.archived)
        val remaining = dao.events(position.id).filter { it.sequence != sequence }.map { it.domain(position.currency) }
        InvestmentRules.progress(position.domain(), remaining, LocalDate.now())
        event.movementId?.let { id ->
            val row = requireNotNull(db.moneyDao().movement(id))
            require(row.investmentId == position.id)
            if (event.createdMovement) db.moneyDao().deleteMovement(id)
            else db.moneyDao().updateMovement(row.copy(investmentId = null, investmentCapital = false))
        }
        val snapshot = money.snapshot()
        require(snapshot.pockets.none { it.balance.minorUnits < 0 })
        val reserved = snapshot.pockets.map { it.pocket.accountId }.toSet()
        require(snapshot.accounts.none { it.account.id in reserved && it.unallocated.minorUnits < 0 })
        dao.deleteEvent(sequence)
        dao.update(position.copy(version = Math.incrementExact(version)))
    }

    private fun cashMatches(a: MoneyMovement, b: MoneyMovement) = a.accountId == b.accountId && a.pocketId == b.pocketId &&
        a.amount == b.amount && a.direction == b.direction && a.date == b.date && a.spaceId == b.spaceId && a.note == b.note
}

private fun MoneyInvestmentEntity.domain() = MoneyInvestment(id, name, currency, spaceId, LocalDate.ofEpochDay(openedEpochDay),
    Money(initialCostMinor, currency), Money(initialValueMinor, currency), note, archived, version)
private fun MoneyInvestment.entity() = MoneyInvestmentEntity(id, name.trim(), currency, spaceId, opened.toEpochDay(),
    initialCost.minorUnits, initialValue.minorUnits, note, archived, version)
private fun MoneyInvestmentEventEntity.domain(currency: String) = InvestmentEvent(requestId, investmentId,
    InvestmentEventKind.valueOf(kind), Money(amountMinor, currency), LocalDate.ofEpochDay(dateEpochDay), Money(costMinor, currency), movementId, sequence, note)
