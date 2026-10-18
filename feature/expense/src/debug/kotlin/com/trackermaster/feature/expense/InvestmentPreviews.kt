package com.trackermaster.feature.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import java.time.LocalDate

object InvestmentPreviewData {
    fun state(hidden:Boolean=false):InvestmentUiState {
        val p=MoneyInvestment("fund","Mi inversión","COP",2,LocalDate.now().withDayOfMonth(1),Money(5000000,"COP"),Money(5200000,"COP"))
        val book=InvestmentBook(listOf(p))
        return InvestmentUiState(loading=false,workspace=MoneyPreviewData.workspace(),book=book,preferences=MoneyPreferences(hideAmounts=hidden),
            rows=listOf(InvestmentRules.progress(p,emptyList(),LocalDate.now())))
    }
}
@Preview(name="Inversiones · claro · ES",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentLight(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state(),{},{})}}
@Preview(name="Inversiones · oscuro · EN",locale="en",widthDp=393,heightDp=852)
@Composable private fun InvestmentDark(){TrackermasterTheme(ThemeMode.DARK,0){MoneyInvestmentScreen(InvestmentPreviewData.state(),{},{})}}
@Preview(name="Inversiones · OLED · ZH",locale="zh",widthDp=393,heightDp=852)
@Composable private fun InvestmentOled(){TrackermasterTheme(ThemeMode.OLED,0){MoneyInvestmentScreen(InvestmentPreviewData.state(),{},{})}}
@Preview(name="Inversiones · privacidad",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentPrivate(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state(true),{},{})}}
@Preview(name="Aporte",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentContribution(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state().copy(draft=InvestmentDraft(id="fund",kind=InvestmentEventKind.CONTRIBUTION,name="Mi inversión",amount="50000",account=1),canSave=true),{},{})}}
@Preview(name="Retiro",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentWithdrawal(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state().copy(draft=InvestmentDraft(id="fund",kind=InvestmentEventKind.WITHDRAWAL,name="Mi inversión",amount="52000",cost="50000",account=1),canSave=true),{},{})}}
@Preview(name="Valoración",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentValuation(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state().copy(draft=InvestmentDraft(id="fund",kind=InvestmentEventKind.VALUATION,name="Mi inversión",amount="52000"),canSave=true),{},{})}}
@Preview(name="Nueva inversión",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentCreate(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state().copy(draft=InvestmentDraft(name="Work & Travel",currency="USD",space=5),canSave=true),{},{})}}
@Preview(name="Rendimiento cobrado",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentDividend(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state().copy(draft=InvestmentDraft(id="fund",kind=InvestmentEventKind.DIVIDEND,name="Mi inversión",amount="500",account=1,category=6),canSave=true),{},{})}}
@Preview(name="Detalle e historial",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentDetail(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state().copy(selected="fund"),{},{})}}
@Preview(name="Corregir datos iniciales",locale="es",widthDp=393,heightDp=852)
@Composable private fun InvestmentOpeningCorrection(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(InvestmentPreviewData.state().copy(draft=InvestmentDraft(id="fund",editing=true,name="Mi inversión",amount="52000",cost="50000"),canSave=true),{},{})}}
