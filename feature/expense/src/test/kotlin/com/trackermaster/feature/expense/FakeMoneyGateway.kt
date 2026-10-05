package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.data.money.MoneyUndoToken
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeMoneyGateway : MoneyGateway {
    override val data = MutableStateFlow(MoneyPreviewData.workspace())
    override val preferences = MutableStateFlow(MoneyPreferences())
    override val localeTag = MutableStateFlow("en")
    var recorded = mutableListOf<MoneyMovement>()
    var hold: CompletableDeferred<Unit>? = null
    var failWrites = false
    var failInitialize = false
    var failPreferences = false
    var notifications = 0
    private data class Token(val rows: List<MoneyMovement>) : MoneyUndoToken
    override suspend fun initialize() { if (failInitialize) error("Read failed") }
    override suspend fun record(movement: MoneyMovement): Long {
        recorded += movement
        hold?.await()
        if (failWrites) error("Write failed")
        val id = movement.id.takeIf { it != 0L } ?: ((data.value.ledger.movements.maxOfOrNull { it.id } ?: 0) + 1)
        update(data.value.ledger.movements.filter { it.id != id } + movement.copy(id = id))
        return id
    }
    private fun update(rows: List<MoneyMovement>) {
        val previous = data.value
        val snapshot = MoneyLedger.snapshot(previous.ledger.snapshot.accounts.map { it.account },
            previous.ledger.snapshot.pockets.map { it.pocket }, rows)
        data.value = previous.copy(ledger = MoneyLedgerData(snapshot, rows))
    }
    override suspend fun transfer(editor: MoneyEditorUiState, amount: Money, received: Money) {
        val rows = data.value.ledger.movements
        val next = (rows.maxOfOrNull { it.id } ?: 0) + 1
        val id = editor.transferId ?: editor.requestId
        val accounts = data.value.ledger.snapshot.accounts.associateBy { it.account.id }
        val pockets = data.value.ledger.snapshot.pockets.associateBy { it.pocket.id }
        fun leg(rowId: Long, accountId: Long, pocketId: Long?, value: Money, direction: MoneyDirection) =
            MoneyMovement(rowId, accountId, value, direction, editor.date,
                pocketId?.let { pockets.getValue(it).pocket.spaceId } ?: accounts.getValue(accountId).account.spaceId,
                pocketId = pocketId, transferId = id, note = editor.note)
        update(rows.filter { it.transferId != id } + listOf(
            leg(next, requireNotNull(editor.accountId), editor.pocketId, amount, MoneyDirection.EXPENSE),
            leg(next + 1, requireNotNull(editor.targetAccountId), editor.targetPocketId, received, MoneyDirection.INCOME)))
    }
    override suspend fun delete(id: Long): MoneyUndoToken {
        val rows = data.value.ledger.movements
        val row = rows.single { it.id == id }
        val selected = if (row.transferId == null) listOf(row) else rows.filter { it.transferId == row.transferId }
        update(rows - selected.toSet())
        return Token(selected)
    }
    override suspend fun restore(token: MoneyUndoToken) { update(data.value.ledger.movements + (token as Token).rows) }
    override suspend fun account(account: MoneyAccount): Long = error("Not used")
    override suspend fun archive(id: Long, archived: Boolean) = Unit
    override suspend fun pocket(account: Long, space: Long, name: String): Long = error("Not used")
    override suspend fun category(name: String, income: Boolean): Long = error("Not used")
    override suspend fun space(name: String): Long = error("Not used")
    override suspend fun appearance(detailed: Boolean, sections: Boolean) { preferences.value = preferences.value.copy(detailed = detailed, sections = sections) }
    override suspend fun privacy(hidden: Boolean) { if (failPreferences) error("Preferences failed"); preferences.value = preferences.value.copy(hideAmounts = hidden) }
    override suspend fun rememberFunding(account: Long, pocket: Long?) {
        if (failPreferences) error("Preferences failed")
        preferences.value = preferences.value.copy(lastAccountId = account, lastPocketId = pocket)
    }
    override fun notifyChanged() { notifications++ }
}
