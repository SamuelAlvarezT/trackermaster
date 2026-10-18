package com.trackermaster.feature.expense

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.trackermaster.core.domain.money.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Shared by receipt originals and temporary reports, with roots declared in the manifest. */
class MoneyFileProvider : androidx.core.content.FileProvider()

interface MoneyReportExportGateway {
    suspend fun create(report: MoneyReport, pdf: Boolean, localeTag: String): String
}
internal object UnavailableMoneyReportExport : MoneyReportExportGateway {
    override suspend fun create(report: MoneyReport,pdf: Boolean,localeTag: String): String = error("No exporter configured")
}

class MoneyReportExporter @Inject constructor(@ApplicationContext private val context: Context) : MoneyReportExportGateway {
    @SuppressLint("AppBundleLocaleChanges")
    override suspend fun create(report: MoneyReport, pdf: Boolean, localeTag: String): String = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir,"money/reports").apply { check(mkdirs() || isDirectory) }
        folder.listFiles()?.filter { (it.name.matches(NAME) || it.name.removeSuffix(".tmp").matches(NAME)) && it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val filename = "money-report-${UUID.randomUUID()}.${if(pdf) "pdf" else "csv"}"
        val output = File(folder,filename)
        val temporary = File(folder,"$filename.tmp")
        try {
            if(pdf) {
                require(report.entries.size <= MAX_PDF_ROWS)
                val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(localeTag)) }
                val localized = context.createConfigurationContext(config)
                temporary.outputStream().use { stream -> MoneyReportPdf.write(localized,report,stream) }
            } else temporary.writer(Charsets.UTF_8).use { MoneyCsv.write(report,it) }
            check(temporary.renameTo(output))
            filename
        } catch (failure: Exception) { temporary.delete();output.delete();throw failure }
    }
    companion object {
        const val MAX_PDF_ROWS = 10_000
        private val NAME = Regex("money-report-[0-9a-f-]{36}\\.(csv|pdf)")
        fun file(context: Context,filename: String): File {
            require(filename.matches(NAME))
            return File(context.cacheDir,"money/reports/$filename").also { require(it.isFile) }
        }
    }
}

/** Page rendering is testable on a native Canvas without substituting Android's PDF serialization. */
internal interface MoneyReportPages : java.io.Closeable {
    fun begin(number: Int): android.graphics.Canvas
    fun end()
    fun writeTo(output: java.io.OutputStream)
}

private class AndroidMoneyReportPages : MoneyReportPages {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    override fun begin(number: Int): android.graphics.Canvas {
        check(page == null)
        return document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create()).also { page = it }.canvas
    }
    override fun end() { document.finishPage(requireNotNull(page));page = null }
    override fun writeTo(output: java.io.OutputStream) = document.writeTo(output)
    override fun close() {
        try { if(page != null) end() } finally { document.close() }
    }
}

/** Text remains selectable/searchable. Paginated output uses Android font fallback for all app languages. */
internal object MoneyReportPdf {
    fun write(context: Context, report: MoneyReport, output: java.io.OutputStream) =
        writeToPages(context,report,output,AndroidMoneyReportPages())

    fun writeToPages(context: Context, report: MoneyReport, output: java.io.OutputStream, pages: MoneyReportPages) {
        val locale = context.resources.configuration.locales[0]
        val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK;textSize = 11f }
        pages.use { target ->
            var number = 1
            var canvas = target.begin(number)
            var y = 42f
            fun finishPage() {
                paint.textSize = 9f;paint.isFakeBoldText = false
                canvas.drawText(context.getString(R.string.x_page,number),36f,814f,paint)
                target.end()
            }
            fun line(text: String, heading: Boolean = false) {
                paint.textSize = if(heading)14f else 11f
                paint.isFakeBoldText = heading
                val height = if(heading)23f else 17f
                text.split('\n').forEach { paragraph ->
                    var rest = paragraph.replace('\r',' ')
                    do {
                        if(y + height > 785f) { finishPage();number++;canvas = target.begin(number);y = 42f;paint.textSize = if(heading)14f else 11f;paint.isFakeBoldText = heading }
                        var count = paint.breakText(rest,true,523f,null).coerceAtLeast(1).coerceAtMost(rest.length)
                        if(count < rest.length) {
                            val space = rest.lastIndexOf(' ',count-1)
                            if(space > count/2) count = space + 1
                        }
                        if(count < rest.length && count > 0 && Character.isHighSurrogate(rest[count-1]))count--
                        if(count <= 0 && rest.isNotEmpty()) count = Character.charCount(rest.codePointAt(0))
                        canvas.drawText(rest.take(count),36f,y,paint)
                        y += height
                        rest = rest.drop(count)
                    } while(rest.isNotEmpty())
                }
            }
            line(context.getString(R.string.x_report),true)
            line(context.getString(R.string.x_scope,report.scope.currency,report.scope.from.format(dates),report.scope.through.format(dates)))
            line(context.getString(R.string.x_note))
            line(context.getString(R.string.x_operating),true)
            line("${context.getString(R.string.m_income)}: ${report.totals.income.format(locale)}")
            line("${context.getString(R.string.m_expense)}: ${report.totals.expenses.format(locale)}")
            line("${context.getString(R.string.m_net)}: ${report.totals.net.format(locale)}")
            line(context.getString(R.string.m_spending),true)
            report.categories.toList().sortedByDescending { it.second.minorUnits }.forEach { (id,amount) ->
                val selectedCategory = report.entries.firstOrNull { it.category?.id == id }?.category
                line("${category(context,selectedCategory)}: ${amount.format(locale)}")
            }
            line(context.getString(R.string.m_flow_trend),true)
            report.months.forEach { month ->
                line(month.month.format(DateTimeFormatter.ofPattern("MMM yyyy",locale)),true)
                line("${context.getString(R.string.m_income)}: ${month.totals.income.format(locale)}")
                line("${context.getString(R.string.m_expense)}: ${month.totals.expenses.format(locale)}")
                line("${context.getString(R.string.m_net)}: ${month.totals.net.format(locale)}")
            }
            line(context.getString(R.string.x_transactions),true)
            report.entries.forEach { row ->
                val m = row.movement
                val type = context.getString(if(m.transferId != null)R.string.x_transfer else if(m.principal || m.investmentCapital)R.string.x_capital else R.string.x_ordinary)
                line("${m.date.format(dates)} · ${m.id} · $type",true)
                line("${context.getString(R.string.x_account)}: ${row.account}${row.pocket?.let { " · $it" }.orEmpty()}")
                line("${context.getString(R.string.x_category)}: ${category(context,row.category)}")
                line("${context.getString(if(m.direction == MoneyDirection.INCOME)R.string.m_income else R.string.m_expense)}: ${m.amount.format(locale)}")
                if(m.note.isNotEmpty()) line("${context.getString(R.string.x_note_column)}: ${m.note}")
            }
            finishPage()
            target.writeTo(output)
        }
    }
    private fun category(context:Context, value:MoneyCategory?):String = when(value?.builtinKey) {
        "food" -> context.getString(R.string.m_food)
        "transport" -> context.getString(R.string.m_transport)
        "shopping" -> context.getString(R.string.m_shopping)
        "bills" -> context.getString(R.string.m_bills)
        "salary" -> context.getString(R.string.m_salary)
        "other" -> context.getString(R.string.m_other)
        else -> value?.name ?: context.getString(R.string.m_uncategorized)
    }
}
