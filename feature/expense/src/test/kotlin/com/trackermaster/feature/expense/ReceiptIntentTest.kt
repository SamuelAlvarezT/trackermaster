package com.trackermaster.feature.expense

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.trackermaster.core.data.money.ReceiptSource
import com.trackermaster.core.domain.money.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReceiptIntentTest {
    private val uri=Uri.parse("content://bank/proof")
    @Test fun streamAndClipDataForSameFileImportExactlyOneSource() {
        val intent=Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)
        intent.clipData=ClipData.newRawUri("proof",uri)
        assertEquals(ReceiptSource.SharedFile(uri),receiptSource(intent))
    }
    @Test fun clipOnlyAndPlainTextSharesAreAccepted() {
        val intent=Intent(Intent.ACTION_SEND).setType("application/pdf").apply{clipData=ClipData.newRawUri("proof",uri)}
        assertEquals(ReceiptSource.SharedFile(uri),receiptSource(intent))
        assertEquals(ReceiptSource.SharedText("Nequi proof"),receiptSource(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Nequi proof")))
        assertNull(receiptSource(Intent()))
    }
    @Test fun multipleOrUnsafeSourcesAreRejected() {
        val multiple=Intent(Intent.ACTION_SEND).apply{clipData=ClipData.newRawUri("proof",uri).apply{addItem(ClipData.Item(Uri.parse("content://bank/other")))}}
        assertEquals(ReceiptFailure.MULTIPLE_FILES,assertThrows(ReceiptException::class.java){receiptSource(multiple)}.reason)
        assertEquals(ReceiptFailure.MULTIPLE_FILES,assertThrows(ReceiptException::class.java){receiptSource(Intent(Intent.ACTION_SEND_MULTIPLE))}.reason)
        assertEquals(ReceiptFailure.UNSUPPORTED,assertThrows(ReceiptException::class.java){receiptSource(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,Uri.parse("file:///private/proof")))}.reason)
    }
    @Test fun externalFinancialExtrasDoNotChooseAccountOrCommitMoney() {
        val intent=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Nequi proof")
            .putExtra("accountId",123L).putExtra("confirmed",true).putExtra("receipt_id","existing")
        assertEquals(ReceiptSource.SharedText("Nequi proof"),receiptSource(intent))
    }
}
