package com.trackermaster.feature.expense

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.createBitmap
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trackermaster.core.data.money.ReceiptFiles
import com.trackermaster.core.domain.money.*
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

data class ReceiptReading(val text: String, val preview: String?)
/** Bundled Latin model; PDF pages are rendered one at a time with bounded bitmap memory. */
class ReceiptReader @Inject constructor(private val files: ReceiptFiles) {
    suspend fun read(receipt: MoneyReceipt): ReceiptReading = withContext(Dispatchers.IO) {
        val file = files.resolve(receipt.path)
        if (receipt.mimeType == "text/plain") {
            if (file.length() > ReceiptParser.MAX_TEXT * 4) throw ReceiptException(ReceiptFailure.TOO_LARGE)
            return@withContext ReceiptReading(file.readText(Charsets.UTF_8).take(ReceiptParser.MAX_TEXT), null)
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        suspend fun recognize(bitmap: Bitmap): String = withContext(NonCancellable) { suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        } }
        fun preview(bitmap: Bitmap): String {
            val name = "${receipt.sha256}.preview.png"
            files.resolve("money/receipts/$name").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return "money/receipts/$name"
        }
        try {
            if (receipt.mimeType == "application/pdf") {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { pdf ->
                    if (pdf.pageCount !in 1..5) throw ReceiptException(ReceiptFailure.TOO_LARGE)
                    val text = StringBuilder(); var firstPreview: String? = null
                    for (i in 0 until pdf.pageCount) {
                        ensureActive()
                        pdf.openPage(i).use { page ->
                            val ratio = 1800f / max(page.width, page.height)
                            val bitmap = createBitmap(max(1,(page.width*ratio).roundToInt()), max(1,(page.height*ratio).roundToInt()), Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE); page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                if (i == 0) firstPreview = preview(bitmap)
                                text.append(recognize(bitmap)).append('\n')
                            } finally { bitmap.recycle() }
                        }
                    }
                    return@withContext ReceiptReading(text.toString().take(ReceiptParser.MAX_TEXT), firstPreview)
                } }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong()*bounds.outHeight > 60000000)
                    throw ReceiptException(ReceiptFailure.UNREADABLE)
                var sample = 1; while (max(bounds.outWidth,bounds.outHeight)/sample > 2400) sample *= 2
                val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw ReceiptException(ReceiptFailure.UNREADABLE)
                val matrix = Matrix()
                when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)) {
                    2 -> matrix.setScale(-1f,1f)
                    3 -> matrix.setRotate(180f)
                    4 -> matrix.setScale(1f,-1f)
                    5 -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }
                    6 -> matrix.setRotate(90f)
                    7 -> { matrix.setRotate(-90f); matrix.postScale(-1f,1f) }
                    8 -> matrix.setRotate(-90f)
                }
                val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also { decoded.recycle() }
                try {
                    ReceiptReading(recognize(bitmap), preview(bitmap))
                } finally { bitmap.recycle() }
            }
        } finally { recognizer.close() }
    }
}
