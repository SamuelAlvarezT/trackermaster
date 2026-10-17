package com.trackermaster.core.domain.money

import java.time.LocalDate

object MoneyLedger {
    fun snapshot(accounts: List<MoneyAccount>, pockets: List<MoneyPocket>, movements: List<MoneyMovement>): MoneySnapshot {
        val accountsById = accounts.associateBy { it.id }
        val balances = accounts.associate { it.id to it.initialBalance.minorUnits }.toMutableMap()
        val pocketBalances = pockets.associate { it.id to 0L }.toMutableMap()
        val pocketsById = pockets.associateBy { it.id }
        movements.forEach { movement ->
            val account = requireNotNull(accountsById[movement.accountId])
            require(account.initialBalance.currencyCode == movement.amount.currencyCode)
            val signed = if (movement.direction == MoneyDirection.INCOME) movement.amount.minorUnits
                else Math.negateExact(movement.amount.minorUnits)
            balances[account.id] = Math.addExact(balances.getValue(account.id), signed)
            movement.pocketId?.let { pocketId ->
                val pocket = requireNotNull(pocketsById[pocketId])
                require(pocket.accountId == account.id && pocket.spaceId == movement.spaceId)
                pocketBalances[pocketId] = Math.addExact(pocketBalances.getValue(pocketId), signed)
            }
        }
        val pocketRows = pockets.map { pocket ->
            val account = requireNotNull(accountsById[pocket.accountId])
            MoneyPocketBalance(pocket, Money(pocketBalances.getValue(pocket.id), account.initialBalance.currencyCode))
        }
        val reservedByAccount = mutableMapOf<Long, Long>()
        pocketRows.forEach { row ->
            reservedByAccount[row.pocket.accountId] = Math.addExact(reservedByAccount[row.pocket.accountId] ?: 0, row.balance.minorUnits)
        }
        val accountRows = accounts.map { account ->
            val total = Money(balances.getValue(account.id), account.initialBalance.currencyCode)
            MoneyAccountBalance(account, total, total.copy(minorUnits = Math.subtractExact(total.minorUnits, reservedByAccount[account.id] ?: 0)))
        }
        val spaceAmounts = mutableMapOf<Pair<Long, String>, Long>()
        fun addSpace(id: Long, amount: Money) {
            val key = id to amount.currencyCode
            spaceAmounts[key] = Math.addExact(spaceAmounts[key] ?: 0, amount.minorUnits)
        }
        accountRows.forEach { addSpace(it.account.spaceId, it.unallocated) }
        pocketRows.forEach { addSpace(it.pocket.spaceId, it.balance) }
        val currencies = accountRows.map { it.total }.groupBy { it.currencyCode }.toSortedMap().map { (code, rows) ->
            rows.fold(Money(0, code)) { total, row -> total + row }
        }
        return MoneySnapshot(accountRows, pocketRows,
            spaceAmounts.toSortedMap(compareBy<Pair<Long, String>> { it.first }.thenBy { it.second })
                .map { (key, value) -> MoneySpaceBalance(key.first, Money(value, key.second)) }, currencies)
    }

    fun periodTotals(movements: List<MoneyMovement>, currencyCode: String, from: LocalDate, through: LocalDate,
                     spaceId: Long? = null): MoneyPeriodTotals {
        require(!through.isBefore(from))
        var income = Money(0, currencyCode)
        var expenses = Money(0, currencyCode)
        movements.asSequence().filter {
            it.transferId == null && !it.principal && it.amount.currencyCode == currencyCode &&
                !it.date.isBefore(from) && !it.date.isAfter(through) && (spaceId == null || it.spaceId == spaceId)
        }.forEach { if (it.direction == MoneyDirection.INCOME) income += it.amount else expenses += it.amount }
        return MoneyPeriodTotals(income, expenses)
    }
}
