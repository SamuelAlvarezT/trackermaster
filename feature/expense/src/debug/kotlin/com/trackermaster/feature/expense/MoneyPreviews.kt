package com.trackermaster.feature.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AccountType
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import java.time.LocalDate

/** Debug-only data, shared by previews and UI regression tests. */
object MoneyPreviewData {
    fun workspace(day: LocalDate = LocalDate.now()): MoneyWorkspaceData {
        val accounts = listOf(MoneyAccount(1, "Nequi", AccountType.CHECKING, Money(30000000, "COP"), 2),
            MoneyAccount(2, "Negocio", AccountType.CASH, Money(8000000, "COP"), 3),
            MoneyAccount(3, "Work & Travel", AccountType.SAVINGS, Money(150000, "USD"), 5))
        val pockets = listOf(MoneyPocket(1, 1, 4, "Reserva"))
        val categories = listOf("food", "transport", "shopping", "bills", "other", "salary").mapIndexed { i, key ->
            MoneyCategory(i + 1L, key, key == "salary", key)
        }
        fun movement(id: Long, account: Long, amount: Long, direction: MoneyDirection, space: Long,
                     category: Long? = null, pocket: Long? = null, transfer: String? = null, date: LocalDate = day, note: String = "") =
            MoneyMovement(id, account, Money(amount, "COP"), direction, date, space, category, pocket, transfer, note)
        val rows = listOf(movement(1, 1, 1890000, MoneyDirection.EXPENSE, 2, 1, note = "Almuerzo"),
            movement(2, 1, 320000, MoneyDirection.EXPENSE, 2, 2, date = day.minusDays(1)),
            movement(3, 1, 22000000, MoneyDirection.INCOME, 2, 6, date = day.withDayOfMonth(1)),
            movement(4, 1, 5000000, MoneyDirection.EXPENSE, 2, transfer = "business"),
            movement(5, 2, 5000000, MoneyDirection.INCOME, 3, transfer = "business"),
            movement(6, 1, 2000000, MoneyDirection.EXPENSE, 2, transfer = "reserve"),
            movement(7, 1, 2000000, MoneyDirection.INCOME, 4, pocket = 1, transfer = "reserve"))
        return MoneyWorkspaceData(MoneyLedgerData(MoneyLedger.snapshot(accounts, pockets, rows), rows), categories,
            MoneySpaceKind.entries.take(5).mapIndexed { i, kind -> MoneySpace(i + 1L, kind) }, emptyList())
    }
    fun state(destination: MoneyDestination = MoneyDestination.SUMMARY, themePrivacy: Boolean = false): MoneyHomeUiState =
        MoneyPresenter().build(workspace(), MoneyControls(destination = destination, localeTag = "es-CO"))
            .copy(preferences = MoneyPreferences(hideAmounts = themePrivacy, detailed = true))
}

@Preview(name = "Resumen · claro · ES", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun OverviewLight() { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(MoneyPreviewData.state()) {} } }
@Preview(name = "Resumen · oscuro · EN", locale = "en", widthDp = 393, heightDp = 852)
@Composable private fun OverviewDark() { TrackermasterTheme(ThemeMode.DARK, 0) { MoneyContent(MoneyPreviewData.state()) {} } }
@Preview(name = "Resumen · OLED · ZH", locale = "zh", widthDp = 393, heightDp = 852)
@Composable private fun OverviewOled() { TrackermasterTheme(ThemeMode.OLED, 0) { MoneyContent(MoneyPreviewData.state()) {} } }
@Preview(name = "Movimientos", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun HistoryPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(MoneyPreviewData.state(MoneyDestination.MOVEMENTS)) {} } }
@Preview(name = "Cuentas y bolsillos", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun AccountsPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(MoneyPreviewData.state(MoneyDestination.ACCOUNTS)) {} } }
@Preview(name = "Reportes", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun ReportsPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(MoneyPreviewData.state(MoneyDestination.REPORTS)) {} } }
@Preview(name = "Presupuestos vacíos", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun BudgetsPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(MoneyPreviewData.state(MoneyDestination.BUDGETS)) {} } }
@Preview(name = "Registro rápido", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun EditorPreview() {
    val data = MoneyPreviewData.workspace()
    val state = MoneyPresenter().build(data, MoneyControls(editor = MoneyEditorUiState(requestId = "preview",
        accountId = 1, categoryId = 1, amount = "18900"))).copy(preferences = MoneyPreferences())
    TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyEditorContent(state) {} }
}
@Preview(name = "Ajustes de Money", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun SettingsPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) {
    MoneyContent(MoneyPreviewData.state().let { it.copy(controls = it.controls.copy(settingsOpen = true)) }) {}
} }
@Preview(name = "Nueva cuenta", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun NewAccountPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) {
    MoneyContent(MoneyPreviewData.state().let { it.copy(controls = it.controls.copy(accountEditor = MoneyAccountEditorUiState())) }) {}
} }
@Preview(name = "Nuevo bolsillo", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun NewPocketPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) {
    MoneyContent(MoneyPreviewData.state().let { it.copy(controls = it.controls.copy(pocketEditor = MoneyPocketEditorUiState(1))) }) {}
} }
@Preview(name = "Carga", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun LoadingPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(MoneyHomeUiState()) {} } }
@Preview(name = "Error con reintento", locale = "es", widthDp = 393, heightDp = 852)
@Composable private fun ErrorPreview() { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(MoneyHomeUiState(load = MoneyLoadState.ERROR)) {} } }
