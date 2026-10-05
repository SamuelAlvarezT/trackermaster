package com.trackermaster.core.data.money

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReceiptFilesTest {
    private lateinit var context:Context
    private lateinit var files:ReceiptFiles
    private lateinit var provider:ProofProvider
    private val uri=Uri.parse("content://receipt.test/proof")
    @Before fun before() {
        context=ApplicationProvider.getApplicationContext();files=ReceiptFiles(context)
        provider=ProofProvider().apply{attachInfo(context,ProviderInfo().apply{authority="receipt.test"})}
        ShadowContentResolver.registerProviderInternal("receipt.test",provider)
    }
    private suspend fun failure(source:ReceiptSource)=try{files.copy(source);error("Expected rejection")}catch(e:ReceiptException){e.reason}
    @Test fun textIsCopiedLocallyHasStableHashAndDeduplicatedFilePath()=runTest {
        val a=files.copy(ReceiptSource.SharedText("Nequi\nMonto COP 10"))
        val b=files.copy(ReceiptSource.SharedText("Nequi\nMonto COP 10"))
        assertEquals(a.path,b.path);assertEquals(64,a.sha256.length);assertEquals("text/plain",a.mime)
        assertEquals("Nequi\nMonto COP 10",files.resolve(a.path).readText())
    }
    @Test fun actualImageHeaderDeterminesTypeAndCopySurvivesSourceDeletion()=runTest {
        provider.file=File(context.cacheDir,"source.bin").apply{writeBytes(byteArrayOf(137.toByte(),80,78,71,13,10,26,10,0))}
        provider.mime="application/octet-stream"
        val copied=files.copy(ReceiptSource.SharedFile(uri));provider.file.delete()
        assertEquals("image/png",copied.mime);assertEquals(9L,files.resolve(copied.path).length())
        assertTrue(copied.path.startsWith("money/receipts/"))
    }
    @Test fun claimedImageMimeDoesNotMakeArbitraryBytesAnImage()=runTest {
        provider.file=File(context.cacheDir,"bad.bin").apply{writeText("invalid bytes")};provider.mime="image/png"
        assertEquals(ReceiptFailure.UNSUPPORTED,failure(ReceiptSource.SharedFile(uri)))
    }
    @Test fun largeIncomingFileFailsBeforePermanentStorage()=runTest {
        provider.file=File(context.cacheDir,"large.pdf").apply{outputStream().use{out->out.write("%PDF-".toByteArray());repeat(ReceiptFiles.MAX_BYTES/8192+1){out.write(ByteArray(8192))}}}
        assertEquals(ReceiptFailure.TOO_LARGE,failure(ReceiptSource.SharedFile(uri)))
        assertFalse(File(context.filesDir,"money/receipts").listFiles().orEmpty().any{it.extension=="tmp"})
    }
    @Test fun fileNetworkEmptyAndOversizedTextAreRejected()=runTest {
        assertEquals(ReceiptFailure.UNSUPPORTED,failure(ReceiptSource.SharedFile(Uri.parse("file:///etc/proof"))))
        assertEquals(ReceiptFailure.UNSUPPORTED,failure(ReceiptSource.SharedFile(Uri.parse("https://example.com/proof"))))
        assertEquals(ReceiptFailure.UNREADABLE,failure(ReceiptSource.SharedText("  ")))
        assertEquals(ReceiptFailure.TOO_LARGE,failure(ReceiptSource.SharedText("a".repeat(ReceiptParser.MAX_TEXT+1))))
    }
    @Test fun viewerCannotResolveFilesOutsideReceiptDirectory() {
        assertThrows(IllegalArgumentException::class.java){files.resolve("money/receipts/../../settings.xml")}
        assertThrows(IllegalArgumentException::class.java){files.resolve("money/receipts-other/proof.jpg")}
        assertThrows(IllegalArgumentException::class.java){files.resolve("../proof.jpg")}
    }
    class ProofProvider:ContentProvider() {
        lateinit var file:File
        var mime="application/pdf"
        override fun onCreate()=true
        override fun getType(uri:Uri)=mime
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor=
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply{addRow(arrayOf("Original proof"))}
        override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    }
}
