package com.trackermaster.core.data.money

import androidx.room.withTransaction
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** All ledger writes use one Room transaction; old REAL columns are compatibility copies only. */
@Singleton
class MoneyRepository @Inject constructor(private val database: TrackermasterDatabase) {
    private val dao get() = database.moneyDao()

    fun observeSpaces() = dao.observeSpaces().map { rows -> rows.map { it.moneySpace() } }
    fun observeLedgerData() = dao.observeLedger().map { rows -> MoneyLedgerData(rows.moneySnapshot(), rows.moneyMovements()) }
    fun observeCategories() = dao.observeCategories().map { rows ->
        rows.map { MoneyCategory(it.id, it.name, it.isIncome, it.builtinKey) }
    }
    fun observeWorkspace() = combine(observeLedgerData(), observeCategories(), observeSpaces(), dao.observeBudgets()) {
        ledger, categories, spaces, budgets ->
        MoneyWorkspaceData(ledger, categories, spaces, budgets.map {
            val minor = it.limitAmountMinor
            val code = it.currencyCode
            MoneyBudget(it.id, it.categoryId, it.limitAmount,
                if (minor != null && code != null) Money(minor, code) else null,
                it.spaceId, it.period)
        })
    }
    suspend fun ensureCategories() = database.withTransaction {
        val existing = dao.categories()
        // Existing names are user data. New built-ins have stable keys, never translated database names.
        if (existing.isEmpty()) {
            listOf("food", "transport", "shopping", "bills", "other", "salary").forEach { key ->
                dao.insertCategory(ExpenseCategoryEntity(name = key, isIncome = key == "salary",
                    colorArgb = 0, builtinKey = key))
            }
        } else {
            if (existing.none { it.isIncome }) dao.insertCategory(ExpenseCategoryEntity(
                name = "salary", isIncome = true, colorArgb = 0, builtinKey = "salary"))
            if (existing.all { it.isIncome }) dao.insertCategory(ExpenseCategoryEntity(
                name = "other", isIncome = false, colorArgb = 0, builtinKey = "other"))
        }
    }
    suspend fun createCategory(name: String, income: Boolean): Long {
        require(name.isNotBlank())
        return dao.insertCategory(ExpenseCategoryEntity(name = name.trim(), isIncome = income, colorArgb = 0))
    }
    fun observeSnapshot() = dao.observeLedger().map { it.moneySnapshot() }
    fun observeMovements() = dao.observeLedger().map { rows ->
        rows.moneyMovements().sortedWith(compareByDescending<MoneyMovement> { it.date }.thenByDescending { it.id })
    }
    suspend fun snapshot(): MoneySnapshot = dao.readLedger().moneySnapshot()

    suspend fun createSpace(name: String): Long {
        require(name.isNotBlank())
        return dao.insertSpace(MoneySpaceEntity(kind = MoneySpaceKind.CUSTOM.name, name = name.trim()))
    }

    suspend fun createAccount(name: String, type: AccountType, initialBalance: Money, spaceId: Long): Long =
        database.withTransaction {
            require(name.isNotBlank())
            requireNotNull(dao.space(spaceId))
            dao.insertAccount(AccountEntity(name = name.trim(), type = type.name,
                currencyCode = initialBalance.currencyCode, initialBalance = initialBalance.decimal.toDouble(),
                initialBalanceMinor = initialBalance.minorUnits, spaceId = spaceId))
        }

    suspend fun updateAccountDetails(id: Long, name: String, type: AccountType, initialBalance: Money) =
        database.withTransaction {
            require(name.isNotBlank())
            val account = requireNotNull(dao.account(id))
            val hasMovements = dao.readLedger().first { it.account.id == id }.movements.isNotEmpty()
            require(!hasMovements || account.currencyCode.equals(initialBalance.currencyCode, ignoreCase = true)) { "Currency is immutable after movements" }
            require(!hasMovements || account.initialBalanceMinor == initialBalance.minorUnits) { "Use a balance adjustment after movements" }
            dao.updateAccount(account.copy(name = name.trim(), type = type.name,
                currencyCode = initialBalance.currencyCode, initialBalanceMinor = initialBalance.minorUnits,
                initialBalance = if (account.currencyCode.equals(initialBalance.currencyCode, ignoreCase = true) &&
                    account.initialBalanceMinor == initialBalance.minorUnits) account.initialBalance else initialBalance.decimal.toDouble()))
        }

    suspend fun saveAccount(account: MoneyAccount): Long = database.withTransaction {
        requireNotNull(dao.space(account.spaceId))
        if (account.id == 0L) createAccount(account.name, account.type, account.initialBalance, account.spaceId) else {
            updateAccountDetails(account.id, account.name, account.type, account.initialBalance)
            classifyAccount(account.id, account.spaceId)
            account.id
        }
    }

    suspend fun classifyAccount(accountId: Long, spaceId: Long) = database.withTransaction {
        requireNotNull(dao.space(spaceId))
        val account = requireNotNull(dao.account(accountId))
        dao.updateAccount(account.copy(spaceId = spaceId))
        dao.classifyMovements(accountId, spaceId)
    }

    suspend fun archiveAccount(accountId: Long, archived: Boolean) = database.withTransaction {
        val account = requireNotNull(dao.account(accountId))
        // Archiving affects availability for new entries, never deletes the balance or history.
        dao.updateAccount(account.copy(archived = archived))
    }

    suspend fun createPocket(accountId: Long, spaceId: Long, name: String): Long = database.withTransaction {
        require(name.isNotBlank())
        val account = requireNotNull(dao.account(accountId))
        require(!account.archived)
        requireNotNull(dao.space(spaceId))
        dao.insertPocket(MoneyPocketEntity(accountId = accountId, spaceId = spaceId, name = name.trim()))
    }

    suspend fun record(movement: MoneyMovement): Long = database.withTransaction {
        require(movement.transferId == null) { "Transfers must be saved as a pair" }
        validateMovement(movement)
        movement.entryRequestId?.let { request ->
            require(request.isNotBlank())
            dao.requestedMovement(request)?.let { existing ->
                if (movement.id == 0L) {
                    val current = existing.moneyMovement(requireNotNull(dao.account(existing.accountId)).currencyCode)
                    if (current.copy(id = 0) != movement) throw MoneyWriteException(MoneyWriteFailure.REQUEST_CONFLICT)
                    return@withTransaction existing.id
                }
                if (existing.id != movement.id) throw MoneyWriteException(MoneyWriteFailure.REQUEST_CONFLICT)
            }
        }
        val id = if (movement.id == 0L) dao.insertMovement(movement.entity()) else {
            val previous = requireNotNull(dao.movement(movement.id))
            require(previous.transferId == null)
            val oldCurrency = requireNotNull(dao.account(previous.accountId)).currencyCode
            val updated = movement.entity()
            dao.updateMovement(updated.copy(
                amount = if (oldCurrency.equals(movement.amount.currencyCode, true) && previous.amountMinor == updated.amountMinor) previous.amount else updated.amount,
                dateEpoch = if (previous.dateEpochDay == updated.dateEpochDay) previous.dateEpoch else updated.dateEpoch,
                entryRequestId = previous.entryRequestId ?: movement.entryRequestId))
            movement.id
        }
        validatePocketBalances()
        id
    }

    /** A repeated request id returns the same pair; reuse with different data is rejected. */
    suspend fun transfer(
        sourceAccountId: Long, targetAccountId: Long, sourceAmount: Money,
        targetAmount: Money = sourceAmount, sourcePocketId: Long? = null, targetPocketId: Long? = null,
        date: LocalDate = LocalDate.now(), note: String = "", requestId: String = UUID.randomUUID().toString(),
        attachmentPath: String? = null,
    ): String = database.withTransaction {
        require(requestId.isNotBlank())
        require(sourceAmount.minorUnits > 0 && targetAmount.minorUnits > 0)
        require(sourceAccountId != targetAccountId || sourcePocketId != targetPocketId)
        val source = requireNotNull(dao.account(sourceAccountId))
        val target = requireNotNull(dao.account(targetAccountId))
        require(!source.archived && !target.archived)
        require(source.currencyCode.equals(sourceAmount.currencyCode, ignoreCase = true) &&
            target.currencyCode.equals(targetAmount.currencyCode, ignoreCase = true))
        require(sourceAmount.currencyCode != targetAmount.currencyCode || sourceAmount == targetAmount) {
            "Same-currency transfers must conserve money; record fees separately"
        }
        val sourceSpace = fundingSpace(source, sourcePocketId)
        val targetSpace = fundingSpace(target, targetPocketId)
        val outgoing = MoneyMovement(accountId = source.id, amount = sourceAmount, direction = MoneyDirection.EXPENSE,
            date = date, spaceId = sourceSpace, pocketId = sourcePocketId, transferId = requestId, note = note, attachmentPath = attachmentPath)
        val incoming = MoneyMovement(accountId = target.id, amount = targetAmount, direction = MoneyDirection.INCOME,
            date = date, spaceId = targetSpace, pocketId = targetPocketId, transferId = requestId, note = note, attachmentPath = attachmentPath)
        val existing = dao.transfer(requestId)
        if (existing != null) {
            val rows = dao.transferMovements(requestId)
            val oldOut = rows.single { it.type == MoneyDirection.EXPENSE.name }.moneyMovement(source.currencyCode)
            val oldIn = rows.single { it.type == MoneyDirection.INCOME.name }.moneyMovement(target.currencyCode)
            require(oldOut.copy(id = 0) == outgoing && oldIn.copy(id = 0) == incoming) { "Transfer request id already used" }
            return@withTransaction requestId
        }
        val current = snapshot()
        val available = sourcePocketId?.let { id -> current.pockets.single { it.pocket.id == id }.balance }
            ?: current.accounts.single { it.account.id == source.id }.unallocated
        if (available.minorUnits < sourceAmount.minorUnits) throw MoneyWriteException(MoneyWriteFailure.INSUFFICIENT_FUNDS)
        dao.insertTransfer(MoneyTransferEntity(requestId, sourceAccountId, targetAccountId))
        dao.insertMovement(outgoing.entity())
        dao.insertMovement(incoming.entity())
        validatePocketBalances()
        requestId
    }

    suspend fun deleteTransfer(requestId: String) = database.withTransaction {
        requireNotNull(dao.transfer(requestId))
        dao.deleteTransferMovements(requestId)
        dao.deleteTransfer(requestId)
        validatePocketBalances()
    }

    suspend fun editTransfer(id: String, source: Long, target: Long, amount: Money, received: Money,
                             sourcePocket: Long?, targetPocket: Long?, date: LocalDate, note: String) = database.withTransaction {
        requireNotNull(dao.transfer(id))
        val attachment = dao.transferMovements(id).firstOrNull()?.imageUri
        dao.deleteTransferMovements(id)
        dao.deleteTransfer(id)
        transfer(source, target, amount, received, sourcePocket, targetPocket, date, note, id, attachment)
    }

    suspend fun deleteEntry(id: Long): MoneyUndoToken = database.withTransaction {
        val movement = dao.movement(id) ?: throw MoneyWriteException(MoneyWriteFailure.STALE_ENTRY)
        val transfer = movement.transferId?.let { requireNotNull(dao.transfer(it)) }
        val rows = if (transfer == null) listOf(movement) else dao.transferMovements(transfer.id)
        val currencies = rows.associate { row -> row.accountId to requireNotNull(dao.account(row.accountId)).currencyCode }
        if (transfer == null) dao.deleteMovement(id) else {
            dao.deleteTransferMovements(transfer.id)
            dao.deleteTransfer(transfer.id)
        }
        validatePocketBalances()
        LocalMoneyUndoToken(rows, transfer, currencies)
    }

    suspend fun restore(token: MoneyUndoToken) = database.withTransaction {
        require(token is LocalMoneyUndoToken)
        token.rows.forEach { row ->
            if (dao.movement(row.id) != null) throw MoneyWriteException(MoneyWriteFailure.STALE_ENTRY)
            val account = requireNotNull(dao.account(row.accountId))
            if (!account.currencyCode.equals(token.currencies.getValue(row.accountId), true))
                throw MoneyWriteException(MoneyWriteFailure.CURRENCY_CHANGED)
        }
        token.transfer?.let { dao.insertTransfer(it) }
        token.rows.forEach { row ->
            val account = requireNotNull(dao.account(row.accountId))
            val space = fundingSpace(account, row.pocketId)
            dao.insertMovement(row.copy(spaceId = space))
        }
        validatePocketBalances()
    }

    private suspend fun fundingSpace(account: AccountEntity, pocketId: Long?): Long {
        if (pocketId == null) return account.spaceId
        val pocket = requireNotNull(dao.pocket(pocketId))
        require(pocket.accountId == account.id)
        return pocket.spaceId
    }

    private suspend fun validateMovement(movement: MoneyMovement) {
        require(movement.amount.minorUnits > 0)
        val account = requireNotNull(dao.account(movement.accountId))
        require(!account.archived)
        require(account.currencyCode.equals(movement.amount.currencyCode, ignoreCase = true))
        require(movement.spaceId == fundingSpace(account, movement.pocketId))
        val category = requireNotNull(movement.categoryId?.let { dao.category(it) })
        require(category.isIncome == (movement.direction == MoneyDirection.INCOME))
    }

    private suspend fun validatePocketBalances() {
        val current = snapshot()
        if (current.pockets.any { it.balance.minorUnits < 0 }) throw MoneyWriteException(MoneyWriteFailure.INSUFFICIENT_FUNDS)
        val accountsWithPockets = current.pockets.map { it.pocket.accountId }.toSet()
        if (current.accounts.filter { it.account.id in accountsWithPockets }.any { it.unallocated.minorUnits < 0 })
            throw MoneyWriteException(MoneyWriteFailure.INSUFFICIENT_FUNDS)
    }

    private fun MoneyMovement.entity() = TransactionEntity(
        id = id, accountId = accountId, amount = amount.decimal.toDouble(), type = direction.name,
        categoryId = categoryId ?: 0, dateEpoch = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        note = note, imageUri = attachmentPath, amountMinor = amount.minorUnits, dateEpochDay = date.toEpochDay(),
        spaceId = spaceId, pocketId = pocketId, transferId = transferId, entryRequestId = entryRequestId)
}

interface MoneyUndoToken

internal class LocalMoneyUndoToken(
    internal val rows: List<TransactionEntity>,
    internal val transfer: MoneyTransferEntity?,
    internal val currencies: Map<Long, String>,
) : MoneyUndoToken
