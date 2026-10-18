package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import java.time.LocalDate
import java.util.UUID

data class InvestmentDraft(val id: String = UUID.randomUUID().toString(), val version: Long = 0,
    val kind: InvestmentEventKind? = null, val editing: Boolean = false,
    val request: String = UUID.randomUUID().toString(), val name: String = "", val amount: String = "0",
    val cost: String = "0", val currency: String = "COP", val space: Long = 2,
    val date: String = LocalDate.now().toString(), val account: Long? = null, val pocket: Long? = null,
    val category: Long? = null, val note: String = "", val linked: Long? = null)

data class InvestmentUiState(val loading: Boolean = true, val loadError: Boolean = false,
    val workspace: MoneyWorkspaceData? = null, val book: InvestmentBook = InvestmentBook(),
    val settings: AppSettings = AppSettings(), val preferences: MoneyPreferences = MoneyPreferences(hideAmounts = true),
    val currency: String = "COP", val space: Long? = null, val rows: List<InvestmentProgress> = emptyList(),
    val selected: String? = null, val draft: InvestmentDraft? = null, val saving: Boolean = false,
    val error: Boolean = false, val canSave: Boolean = false, val dateOpen: Boolean = false,
    val candidates: List<MoneyMovement> = emptyList(), val reverse: InvestmentEvent? = null)

sealed interface InvestmentAction {
    data object Retry : InvestmentAction
    data object New : InvestmentAction
    data object Close : InvestmentAction
    data object Save : InvestmentAction
    data object Privacy : InvestmentAction
    data class Currency(val value: String) : InvestmentAction
    data class Space(val value: Long?) : InvestmentAction
    data class Show(val id: String) : InvestmentAction
    data class Edit(val value: MoneyInvestment) : InvestmentAction
    data class Operation(val value: MoneyInvestment, val kind: InvestmentEventKind) : InvestmentAction
    data class Draft(val value: InvestmentDraft) : InvestmentAction
    data class Link(val value: MoneyMovement?) : InvestmentAction
    data class Archive(val value: MoneyInvestment) : InvestmentAction
    data class Date(val day: LocalDate?) : InvestmentAction
    data class Reverse(val value: InvestmentEvent?) : InvestmentAction
    data object ConfirmReverse : InvestmentAction
}
