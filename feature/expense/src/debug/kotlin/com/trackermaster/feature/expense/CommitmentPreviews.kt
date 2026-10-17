package com.trackermaster.feature.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import java.time.LocalDate

object CommitmentPreviewData {
    fun data()=MoneyCommitments(debts=listOf(MoneyDebt("sister","Work and Travel",DebtKind.PAYABLE,Money(250000,"USD"),5,LocalDate.now().plusMonths(6)),MoneyDebt("friend","Préstamo personal",DebtKind.RECEIVABLE,Money(18000000,"COP"),2,LocalDate.now().minusDays(3))),goals=listOf(SavingsGoal("travel","Mi próximo viaje",Money(10000000,"COP"),1,LocalDate.now().plusMonths(3))))
    fun state(tab:String="debts",hidden:Boolean=false):MoneyCommitmentUiState {
        val w=MoneyPreviewData.workspace();val d=data()
        val debts=d.debts.filter{it.principal.currencyCode=="COP"}.map{CommitmentRules.debt(it,d.payments,w.ledger.movements,LocalDate.now())}
        val goals=d.goals.map{CommitmentRules.goal(it,w.ledger.snapshot.pockets.single{p->p.pocket.id==it.pocketId})}
        return MoneyCommitmentUiState(false,w,preferences=MoneyPreferences(hideAmounts=hidden),controls=CommitmentControls(tab=tab),data=d,debts=debts,goals=goals,receivable=Money(18000000,"COP"))
    }
}
@Preview(locale="es",widthDp=393,heightDp=852) @Composable private fun DebtsLight(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyCommitmentScreen(CommitmentPreviewData.state(),{},{})}}
@Preview(locale="en",widthDp=393,heightDp=852) @Composable private fun DebtsDark(){TrackermasterTheme(ThemeMode.DARK,0){MoneyCommitmentScreen(CommitmentPreviewData.state(),{},{})}}
@Preview(locale="zh",widthDp=393,heightDp=852) @Composable private fun GoalsOled(){TrackermasterTheme(ThemeMode.OLED,0){MoneyCommitmentScreen(CommitmentPreviewData.state("goals"),{},{})}}
@Preview @Composable private fun DebtEditor(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyCommitmentScreen(CommitmentPreviewData.state().copy(controls=CommitmentControls(draft=CommitmentDraft(CommitmentEditor.DEBT))),{},{})}}
@Preview @Composable private fun GoalEditor(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyCommitmentScreen(CommitmentPreviewData.state().copy(controls=CommitmentControls(draft=CommitmentDraft(CommitmentEditor.GOAL))),{},{})}}
@Preview @Composable private fun RepaymentEditor(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyCommitmentScreen(CommitmentPreviewData.state().copy(controls=CommitmentControls(draft=CommitmentDraft(CommitmentEditor.REPAYMENT,name="Préstamo personal",kind=DebtKind.RECEIVABLE,account=1))),{},{})}}
@Preview @Composable private fun ContributionEditor(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyCommitmentScreen(CommitmentPreviewData.state().copy(controls=CommitmentControls(draft=CommitmentDraft(CommitmentEditor.ALLOCATION,name="Mi próximo viaje",account=1,pocket=1))),{},{})}}
@Preview @Composable private fun DebtHistory(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyCommitmentScreen(CommitmentPreviewData.state().copy(controls=CommitmentControls(detail="friend")),{},{})}}
