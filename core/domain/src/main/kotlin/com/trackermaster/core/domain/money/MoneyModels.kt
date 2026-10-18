package com.trackermaster.core.domain.money

import com.trackermaster.core.domain.model.AccountType
import java.time.LocalDate

enum class MoneySpaceKind { UNASSIGNED, PERSONAL, BUSINESS, SAVINGS, TRAVEL, CUSTOM }
enum class MoneyDirection { INCOME, EXPENSE }

data class MoneySpace(val id: Long, val kind: MoneySpaceKind, val name: String = "")
data class MoneyAccount(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val initialBalance: Money,
    val spaceId: Long,
    val archived: Boolean = false,
)
data class MoneyPocket(val id: Long = 0, val accountId: Long, val spaceId: Long, val name: String)
data class MoneyMovement(
    val id: Long = 0,
    val accountId: Long,
    val amount: Money,
    val direction: MoneyDirection,
    val date: LocalDate,
    val spaceId: Long,
    val categoryId: Long? = null,
    val pocketId: Long? = null,
    val transferId: String? = null,
    val note: String = "",
    val attachmentPath: String? = null,
    val entryRequestId: String? = null,
    val principal: Boolean = false,
    val investmentId: String? = null,
    val investmentCapital: Boolean = false,
)
data class MoneyAccountBalance(val account: MoneyAccount, val total: Money, val unallocated: Money)
data class MoneyPocketBalance(val pocket: MoneyPocket, val balance: Money)
data class MoneySpaceBalance(val spaceId: Long, val balance: Money)
data class MoneySnapshot(
    val accounts: List<MoneyAccountBalance>,
    val pockets: List<MoneyPocketBalance>,
    val spaces: List<MoneySpaceBalance>,
    val currencies: List<Money>,
)
data class MoneyPeriodTotals(val income: Money, val expenses: Money) {
    val net: Money get() = income - expenses
}

data class MoneyCategory(val id: Long, val name: String, val isIncome: Boolean, val builtinKey: String? = null)
data class MoneyLedgerData(val snapshot: MoneySnapshot, val movements: List<MoneyMovement>)
data class MoneyBudget(val id: Long, val categoryId: Long, val legacyAmount: Double,
    val limit: Money?, val spaceId: Long?, val period: String, val alertThreshold: Int = 80,
    val startDate: LocalDate? = null, val endDate: LocalDate? = null)
data class MoneyWorkspaceData(val ledger: MoneyLedgerData, val categories: List<MoneyCategory>,
    val spaces: List<MoneySpace>, val budgets: List<MoneyBudget>, val plans: List<MoneyPlan> = emptyList(),
    val investments: InvestmentBook = InvestmentBook())
enum class MoneyWriteFailure { INSUFFICIENT_FUNDS, STALE_ENTRY, CURRENCY_CHANGED, REQUEST_CONFLICT }
class MoneyWriteException(val reason: MoneyWriteFailure) : IllegalArgumentException(reason.name)
