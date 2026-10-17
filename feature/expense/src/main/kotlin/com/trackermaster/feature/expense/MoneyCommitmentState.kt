package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import java.time.LocalDate
import java.util.UUID

enum class CommitmentEditor { DEBT, GOAL, REPAYMENT, ALLOCATION }
data class CommitmentDraft(val editor: CommitmentEditor, val id: String = UUID.randomUUID().toString(),
    val version: Long = 0, val request: String = UUID.randomUUID().toString(), val name: String = "",
    val kind: DebtKind = DebtKind.PAYABLE, val currency: String = "COP", val space: Long = 2,
    val amount: String = "", val due: String = "", val date: String = LocalDate.now().toString(),
    val account: Long? = null, val pocket: Long? = null, val note: String = "", val disburse: Boolean = false,
    val withdraw: Boolean = false, val linked: Long? = null, val existing: Boolean = false)
data class CommitmentControls(val tab: String = "debts", val currency: String = "COP", val space: Long? = null,
    val archived: Boolean = false, val draft: CommitmentDraft? = null, val detail: String? = null,
    val saving: Boolean = false, val error: String? = null, val privacyOverride: Boolean? = null,
    val dateField: String? = null, val reverse: String? = null)
data class MoneyCommitmentUiState(val loading: Boolean = true, val workspace: MoneyWorkspaceData? = null,
    val settings: AppSettings = AppSettings(), val preferences: MoneyPreferences = MoneyPreferences(hideAmounts = true),
    val controls: CommitmentControls = CommitmentControls(), val data: MoneyCommitments = MoneyCommitments(),
    val debts: List<DebtProgress> = emptyList(), val goals: List<GoalProgress> = emptyList(),
    val payable: Money = Money(0,"COP"), val receivable: Money = Money(0,"COP"),
    val candidates: List<MoneyMovement> = emptyList(), val canSave: Boolean = false)

sealed interface CommitmentEvent {
    data object Retry : CommitmentEvent
    data object Privacy : CommitmentEvent
    data object Save : CommitmentEvent
    data object Close : CommitmentEvent
    data class Tab(val value: String) : CommitmentEvent
    data class Currency(val value: String) : CommitmentEvent
    data class Space(val value: Long?) : CommitmentEvent
    data class Archived(val value: Boolean) : CommitmentEvent
    data class Draft(val value: CommitmentDraft) : CommitmentEvent
    data class New(val editor: CommitmentEditor) : CommitmentEvent
    data class Debt(val value: MoneyDebt) : CommitmentEvent
    data class Goal(val value: SavingsGoal) : CommitmentEvent
    data class Repay(val value: MoneyDebt) : CommitmentEvent
    data class Allocate(val value: SavingsGoal, val withdraw: Boolean) : CommitmentEvent
    data class Detail(val id: String?) : CommitmentEvent
    data class Link(val id: Long?) : CommitmentEvent
    data class ArchiveDebt(val value: MoneyDebt) : CommitmentEvent
    data class ArchiveGoal(val value: SavingsGoal) : CommitmentEvent
    data class DateField(val value: String?) : CommitmentEvent
    data class Date(val value: LocalDate) : CommitmentEvent
    data class Reverse(val id: String?) : CommitmentEvent
    data object ConfirmReverse : CommitmentEvent
}
