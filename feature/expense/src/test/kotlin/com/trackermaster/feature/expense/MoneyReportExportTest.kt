package com.trackermaster.feature.expense

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoneyReportExportTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun report()=MoneyPreviewData.state(MoneyDestination.REPORTS).reports.document!!
    @Test fun csvIsUtf8ExactAndUsesAnIsolatedCacheDirectory()=runTest{val exporter=MoneyReportExporter(context);val name=exporter.create(report(),false,"es");val file=MoneyReportExporter.file(context,name);assertTrue(file.canonicalPath.startsWith(File(context.cacheDir,"money/reports").canonicalPath+File.separator));val csv=file.readText();assertTrue(csv.startsWith("schema_version,"));assertTrue(csv.contains("COP"));assertFalse(csv.contains("money/receipts/"))}
    @Test fun exportDoesNotReadDataAgainAfterTheSelectedSnapshot()=runTest{val empty=report().copy(entries=emptyList());val name=MoneyReportExporter(context).create(empty,false,"en");assertEquals(1,MoneyReportExporter.file(context,name).readLines().size)}
    @Test fun traversalAndUnexpectedExtensionsAreRejected(){listOf("../secret.csv","money-report-secret.pdf","money-report-00000000-0000-0000-0000-000000000000.apk").forEach{name->try{MoneyReportExporter.file(context,name);fail("Invalid path accepted")}catch(_:IllegalArgumentException){}}}
    @Test fun shareIntentGrantsReadAccessToOnlyTheChosenReport()=runTest{
        val name=MoneyReportExporter(context).create(report(),false,"en")
        val uri=android.net.Uri.Builder().scheme("content").authority("${context.packageName}.money.receipts").appendPath("money_reports").appendPath(name).build()
        for(pdf in listOf(false,true)){
            val intent=moneyReportShareIntent(uri,pdf,name)
            assertEquals(android.content.Intent.ACTION_SEND,intent.action)
            assertEquals(if(pdf)"application/pdf" else "text/csv",intent.type)
            assertEquals(uri,androidx.core.content.IntentCompat.getParcelableExtra(intent,android.content.Intent.EXTRA_STREAM,android.net.Uri::class.java))
            assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0,intent.flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(1,intent.clipData!!.itemCount);assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        }
        try{moneyReportShareIntent(android.net.Uri.fromFile(MoneyReportExporter.file(context,name)),false,name);fail("Raw file URI accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun reportLayoutPaginatesAndRendersAllThreeLanguagesOnNativeCanvas(){
        val directory=File(requireNotNull(System.getProperty("money.screenshots")),"investments").apply{mkdirs()}
        val base=report()
        val rows=(1..40).map{index->base.entries.first().copy(movement=base.entries.first().movement.copy(id=index.toLong(),note="Nota de prueba · 中文说明 · detail $index\n"+"Long paragraph. ".repeat(15)))}
        for(tag in listOf("en","es","zh")){
            val configuration=Configuration(context.resources.configuration).apply{setLocale(Locale.forLanguageTag(tag))}
            val localized=context.createConfigurationContext(configuration)
            val pages=BitmapPages(directory,tag)
            MoneyReportPdf.writeToPages(localized,base.copy(entries=rows),java.io.ByteArrayOutputStream(),pages)
            assertTrue(pages.pages.length()>1);assertTrue(pages.closed);assertTrue(pages.written)
            val text=(0 until pages.pages.length()).flatMap{index->val lines=pages.pages.getJSONObject(index).getJSONArray("lines");(0 until lines.length()).map{lines.getJSONObject(it).getString("text")}}.joinToString(" ").replace(Regex("\\s+")," ")
            assertTrue(text.contains(localized.getString(R.string.x_report)))
            assertTrue(text.contains("detail 40"))
            File(directory,"report-layout-$tag.json").writeText(JSONObject().put("backend","native Canvas layout; Android PdfDocument serialization requires a device").put("pages",pages.pages).toString(2))
        }
    }

    private class RecordingCanvas(bitmap:Bitmap):Canvas(bitmap){
        val lines=JSONArray()
        override fun drawText(text:String,x:Float,y:Float,paint:Paint){
            assertTrue("Left margin",x>=35f);assertTrue("Right margin: $text",x+paint.measureText(text)<=560f)
            assertTrue("Top margin",y+paint.fontMetrics.ascent>=0f);assertTrue("Bottom margin",y+paint.fontMetrics.descent<=842f)
            lines.put(JSONObject().put("text",text).put("x",x.toDouble()).put("y",y.toDouble()).put("size",paint.textSize.toDouble()).put("bold",paint.isFakeBoldText))
            super.drawText(text,x,y,paint)
        }
    }
    private class BitmapPages(private val directory:File,private val tag:String):MoneyReportPages{
        val pages=JSONArray();var closed=false;var written=false
        private var bitmap:Bitmap?=null
        private var canvas:RecordingCanvas?=null
        override fun begin(number:Int):Canvas{check(bitmap==null);val image=Bitmap.createBitmap(595,842,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.WHITE)};bitmap=image;return RecordingCanvas(image).also{canvas=it}}
        override fun end(){val image=requireNotNull(bitmap);val index=pages.length()+1
            File(directory,"report-layout-$tag-${index.toString().padStart(3,'0')}.png").outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
            pages.put(JSONObject().put("page",index).put("lines",requireNotNull(canvas).lines));image.recycle();bitmap=null;canvas=null
        }
        override fun writeTo(output:java.io.OutputStream){check(bitmap==null);written=true}
        override fun close(){bitmap?.recycle();bitmap=null;canvas=null;closed=true}
    }
}
