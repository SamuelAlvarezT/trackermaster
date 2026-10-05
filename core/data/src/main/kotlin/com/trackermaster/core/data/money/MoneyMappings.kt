package com.trackermaster.core.data.money

import com.trackermaster.core.database.dao.MoneyAccountLedger
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.money.*
import java.time.LocalDate

internal fun AccountEntity.moneyAccount() = MoneyAccount(id, name, AccountType.valueOf(type),
    Money(initialBalanceMinor, currencyCode.uppercase(java.util.Locale.ROOT)), spaceId, archived)
internal fun MoneyPocketEntity.moneyPocket() = MoneyPocket(id, accountId, spaceId, name)
internal fun MoneySpaceEntity.moneySpace() = MoneySpace(id, MoneySpaceKind.valueOf(kind), name)
internal fun TransactionEntity.moneyMovement(currencyCode: String) = MoneyMovement(id, accountId,
    Money(amountMinor, currencyCode.uppercase(java.util.Locale.ROOT)), MoneyDirection.valueOf(type),
    LocalDate.ofEpochDay(dateEpochDay), spaceId, categoryId.takeIf { it != 0L }, pocketId, transferId, note, imageUri, entryRequestId)
internal fun List<MoneyAccountLedger>.moneyMovements() = flatMap { row ->
    row.movements.map { it.moneyMovement(row.account.currencyCode) }
}
internal fun List<MoneyAccountLedger>.moneySnapshot() = MoneyLedger.snapshot(
    map { it.account.moneyAccount() }, flatMap { it.pockets }.map { it.moneyPocket() }, moneyMovements())
