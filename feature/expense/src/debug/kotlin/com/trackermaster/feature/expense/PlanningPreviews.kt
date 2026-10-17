package com.trackermaster.feature.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import java.time.LocalDate
import java.util.Locale

object PlanningPreviewData {
    fun workspace():MoneyWorkspaceData {
        val day=LocalDate.now();val base=MoneyPreviewData.workspace(day)
        val budgets=listOf(MoneyBudget(1,1,25000.0,Money(2500000,"COP"),2,"MONTHLY"),
            MoneyBudget(2,2,3500.0,Money(350000,"COP"),2,"MONTHLY"),MoneyBudget(3,1,10000.0,Money(1000000,"COP"),2,"MONTHLY"))
        val plan=MoneyPlan("data","Plan de datos",MoneyPlanKind.PAYMENT,MoneyDirection.EXPENSE,Money(3200000,"COP"),"COP",1,null,4,
            day,day,MoneyFrequency.MONTHLY,version=1,spaceId=2)
        return base.copy(budgets=budgets,plans=listOf(plan,plan.copy(id="soap",name="Jabón para el pelo",kind=MoneyPlanKind.PURCHASE,amount=null,frequency=MoneyFrequency.ONCE,nextDue=day.plusDays(2))))
    }
    fun state(tab:String="budgets",hidden:Boolean=false):MoneyPlanningUiState {
        val data=workspace();val day=LocalDate.now()
        return MoneyPlanningUiState(loading=false,workspace=data,preferences=MoneyPreferences(hideAmounts=hidden),controls=PlanningControls(tab=tab),
            budgets=data.budgets.map{planningBudgetItem(it,data,day,Locale.forLanguageTag("es-CO"))},plans=data.plans,notificationsAllowed=true,
            forecast=PlanRules.forecast(data.plans,day.withDayOfMonth(1),day.withDayOfMonth(day.lengthOfMonth()),"COP",MoneyDirection.EXPENSE))
    }
}
@Preview @Composable private fun PlanningBudgetsLight(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyPlanningScreen(PlanningPreviewData.state(),{},{})}}
@Preview @Composable private fun PlanningBudgetsDark(){TrackermasterTheme(ThemeMode.DARK,0){MoneyPlanningScreen(PlanningPreviewData.state(),{},{})}}
@Preview @Composable private fun PlanningBudgetsOled(){TrackermasterTheme(ThemeMode.OLED,0){MoneyPlanningScreen(PlanningPreviewData.state(),{},{})}}
@Preview @Composable private fun PlanningPlans(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyPlanningScreen(PlanningPreviewData.state("plans"),{},{})}}
@Preview @Composable private fun PlanningBudgetEditor(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyPlanningScreen(PlanningPreviewData.state().copy(controls=PlanningControls(budget=BudgetForm()),canSave=false),{},{})}}
@Preview @Composable private fun PlanningPlanEditor(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyPlanningScreen(PlanningPreviewData.state().copy(controls=PlanningControls(plan=PlanForm()),canSave=false),{},{})}}
@Preview @Composable private fun PlanningSettlement(){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyPlanningScreen(PlanningPreviewData.state().copy(controls=PlanningControls(settlement=SettlementForm("data",LocalDate.now().toString(),1,"Plan de datos","COP",MoneyDirection.EXPENSE,"32000",1,null,4)),canSave=true),{},{})}}
