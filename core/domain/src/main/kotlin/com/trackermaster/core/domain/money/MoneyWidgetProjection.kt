package com.trackermaster.core.domain.money

import java.time.LocalDate

data class MoneyWidgetScope(val space: MoneySpace,val balance: Money)
data class MoneyWidgetSummary(val balances: List<Money>,val scopes: List<MoneyWidgetScope>,
    val month: List<MoneyPeriodTotals>,val upcomingPayments: Int,val hasAccounts: Boolean)
data class MoneyWidgetSelection(val currency: String?=null,val spaceId: Long?=null,val accountId: Long?=null)

object MoneyWidgetProjection {
    fun build(data: MoneyWorkspaceData,today: LocalDate,selection: MoneyWidgetSelection=MoneyWidgetSelection()): MoneyWidgetSummary {
        val snapshot=data.ledger.snapshot
        val balances=when {
            selection.accountId!=null->snapshot.accounts.filter { it.account.id==selection.accountId }.map { it.total }
            selection.spaceId!=null->snapshot.spaces.filter { it.spaceId==selection.spaceId }.map { it.balance }
            else->snapshot.currencies
        }.filter { selection.currency==null || it.currencyCode==selection.currency }
        val currencies=balances.map { it.currencyCode }.distinct()
        val movements=data.ledger.movements.filter { selection.accountId==null || it.accountId==selection.accountId }
        return MoneyWidgetSummary(balances,
            snapshot.spaces.filter { (selection.spaceId==null || it.spaceId==selection.spaceId) &&
                (selection.currency==null || it.balance.currencyCode==selection.currency) && selection.accountId==null }
                .mapNotNull { row -> data.spaces.find { it.id==row.spaceId }?.let { MoneyWidgetScope(it,row.balance) } }
                .sortedWith(compareBy<MoneyWidgetScope> { it.space.id }.thenBy { it.balance.currencyCode }),
            currencies.map { MoneyLedger.periodTotals(movements,it,today.withDayOfMonth(1),today,selection.spaceId) },
            data.plans.count { it.active && !it.archived && it.kind==MoneyPlanKind.PAYMENT &&
                it.direction==MoneyDirection.EXPENSE && it.nextDue<=today.plusDays(7) &&
                (selection.currency==null || it.currency==selection.currency) &&
                (selection.spaceId==null || it.spaceId==selection.spaceId) &&
                (selection.accountId==null || it.accountId==selection.accountId) },snapshot.accounts.isNotEmpty())
    }
}
