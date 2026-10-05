package com.trackermaster.feature.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.ui.theme.TrackermasterTheme

object ReceiptPreviewData {
    fun review() :ReceiptUiState {
        val text="Nequi\nEnviaste\nMonto COP 50.000,00\nFecha 04/10/2026\nReferencia R123"
        val analysis=ReceiptParser.parse(text)
        return ReceiptUiState(workspace=MoneyPreviewData.workspace(),receipt=MoneyReceipt("preview","money/receipts/a.png","image/png","hash","Nequi.png",text,stage="REVIEW"),
            form=ReceiptDraft.from(analysis),analysis=analysis,hidden=false,loading=false)
    }
}
@Preview(name="Comprobante · ES claro",locale="es",widthDp=393,heightDp=852)
@Composable private fun ReceiptLight(){MoneyLocale("es"){TrackermasterTheme(ThemeMode.LIGHT,0){ReceiptScreen(ReceiptPreviewData.review(),{},{},{})}}}
@Preview(name="Comprobante · EN oscuro",locale="en",widthDp=393,heightDp=852)
@Composable private fun ReceiptDark(){MoneyLocale("en"){TrackermasterTheme(ThemeMode.DARK,0){ReceiptScreen(ReceiptPreviewData.review(),{},{},{})}}}
@Preview(name="Comprobante · ZH OLED",locale="zh",widthDp=393,heightDp=852)
@Composable private fun ReceiptOled(){MoneyLocale("zh"){TrackermasterTheme(ThemeMode.OLED,0){ReceiptScreen(ReceiptPreviewData.review(),{},{},{})}}}
@Preview(name="Bandeja vacía",locale="es",widthDp=393,heightDp=852)
@Composable private fun ReceiptInbox(){MoneyLocale("es"){TrackermasterTheme(ThemeMode.LIGHT,0){ReceiptScreen(ReceiptUiState(loading=false),{},{},{})}}}
@Preview(name="Posible duplicado",locale="es",widthDp=393,heightDp=852)
@Composable private fun ReceiptDuplicatePreview(){MoneyLocale("es"){TrackermasterTheme(ThemeMode.LIGHT,0){ReceiptScreen(ReceiptPreviewData.review().copy(duplicates=listOf(ReceiptDuplicate(1,ReceiptDuplicateReason.FILE))),{},{},{})}}}
@Preview(name="Leyendo comprobante",locale="es",widthDp=393,heightDp=852)
@Composable private fun ReceiptReadingPreview(){MoneyLocale("es"){TrackermasterTheme(ThemeMode.LIGHT,0){ReceiptScreen(ReceiptUiState(reading=true),{},{},{})}}}
