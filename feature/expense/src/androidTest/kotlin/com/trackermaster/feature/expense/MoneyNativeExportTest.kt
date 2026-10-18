package com.trackermaster.feature.expense

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.time.LocalDate

/** Runs in its own application/package, using synthetic data and no production database. */
@RunWith(JUnit4::class)
class MoneyNativeExportTest {
    @Test fun nativePdfAndCsvCanBeReadThroughThePrivateProvider() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.trackermaster.money.validation.phase7")
        val day = LocalDate.now()
        val base = MoneyPreviewData.workspace(day)
        val expenses = (1..40).map { index -> MoneyMovement(index.toLong(),1,Money(123400,"COP"),MoneyDirection.EXPENSE,day,2,
            categoryId = 1,note = "Prueba local · 中文说明 · movimiento $index\n" + "Detalle sintético de la operación. ".repeat(8)) }
        val movements = expenses + MoneyMovement(41,1,Money(8000000,"COP"),MoneyDirection.INCOME,day,2,categoryId = 6,note = "Ingreso sintético")
        val workspace = base.copy(ledger = MoneyLedgerData(MoneyLedger.snapshot(base.ledger.snapshot.accounts.map { it.account },
            base.ledger.snapshot.pockets.map { it.pocket },movements),movements))
        val report = MoneyReportRules.build(workspace,MoneyReportScope("COP",day.withDayOfMonth(1),day.withDayOfMonth(day.lengthOfMonth()),spaceId = 2))
        assertEquals(4936000L,report.totals.expenses.minorUnits)
        assertEquals(8000000L,report.totals.income.minorUnits)
        val folder = File(context.filesDir,"native-export").apply { check(mkdirs() || isDirectory) }
        val results = JSONArray()
        for (language in listOf("en","es","zh")) {
            val filename = MoneyReportExporter(context).create(report,true,language)
            val pdf = MoneyReportExporter.file(context,filename)
            val uri = FileProvider.getUriForFile(context,"${context.packageName}.money.receipts",pdf)
            assertEquals("content",uri.scheme)
            context.contentResolver.openInputStream(uri)!!.use { stream ->
                val magic = ByteArray(4);assertEquals(4,stream.read(magic));assertEquals("%PDF",String(magic,Charsets.US_ASCII))
            }
            pdf.copyTo(File(folder,"report-$language.pdf"),overwrite = true)
            var count = 0
            ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    count = renderer.pageCount
                    assertTrue(count > 1)
                    for (index in 0 until count) renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            File(folder,"report-$language-${(index+1).toString().padStart(3,'0')}.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
                            }
                        } finally { bitmap.recycle() }
                    }
                }
            }
            results.put(JSONObject().put("language",language).put("pages",count).put("provider_read",true))
        }
        val csvName = MoneyReportExporter(context).create(report,false,"es")
        val csv = MoneyReportExporter.file(context,csvName)
        val csvUri = FileProvider.getUriForFile(context,"${context.packageName}.money.receipts",csv)
        context.contentResolver.openInputStream(csvUri)!!.bufferedReader().use { assertTrue(it.readLine().startsWith("schema_version,")) }
        csv.copyTo(File(folder,"report.csv"),overwrite = true)
        val outside = File.createTempFile("outside-export-",".txt",context.cacheDir)
        try {
            try { FileProvider.getUriForFile(context,"${context.packageName}.money.receipts",outside);fail("Unrelated cache file exposed") }
            catch (_: IllegalArgumentException) { }
        } finally { outside.delete() }
        val receipt = File(context.filesDir,"money/receipts/native-provider-fixture.txt").apply { parentFile!!.mkdirs();writeText("Synthetic receipt") }
        val receiptUri = FileProvider.getUriForFile(context,"${context.packageName}.money.receipts",receipt)
        context.contentResolver.openInputStream(receiptUri)!!.bufferedReader().use { assertEquals("Synthetic receipt",it.readText()) }
        File(folder,"metadata.json").writeText(JSONObject().put("sdk",Build.VERSION.SDK_INT).put("package",context.packageName)
            .put("synthetic_data",true).put("production_database_accessed",false).put("pdf",results).put("csv_provider_read",true)
            .put("receipt_provider_read",true).put("outside_cache_rejected",true).toString(2))
    }
}
