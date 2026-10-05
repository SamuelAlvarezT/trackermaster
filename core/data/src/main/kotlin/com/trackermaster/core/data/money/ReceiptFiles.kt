package com.trackermaster.core.data.money

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.trackermaster.core.domain.money.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ReceiptSource {
    data class SharedFile(val uri: Uri) : ReceiptSource
    data class SharedText(val value: String) : ReceiptSource
}
data class ReceiptFile(val path: String, val mime: String, val sha256: String, val name: String)

@Singleton
class ReceiptFiles @Inject constructor(@ApplicationContext private val context: Context) {
    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
    fun resolve(path: String): File {
        val directory = File(context.filesDir, "money/receipts").canonicalFile
        val file = File(context.filesDir, path).canonicalFile
        require(file.path.startsWith(directory.path + File.separator))
        return file
    }
    suspend fun copy(source: ReceiptSource): ReceiptFile = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "money/receipts").apply { mkdirs() }
        val temporary = File(directory, "${UUID.randomUUID()}.tmp")
        try {
            val text = source is ReceiptSource.SharedText
            val name: String
            val claimedMime: String?
            val stream: InputStream
            when (source) {
                is ReceiptSource.SharedText -> {
                    if (source.value.length > ReceiptParser.MAX_TEXT) throw ReceiptException(ReceiptFailure.TOO_LARGE)
                    if (source.value.isBlank()) throw ReceiptException(ReceiptFailure.UNREADABLE)
                    name = "text.txt"; claimedMime = "text/plain"
                    stream = source.value.toByteArray(Charsets.UTF_8).inputStream()
                }
                is ReceiptSource.SharedFile -> {
                    if (source.uri.scheme != "content") throw ReceiptException(ReceiptFailure.UNSUPPORTED)
                    claimedMime = context.contentResolver.getType(source.uri)
                    name = runCatching { context.contentResolver.query(source.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()?.take(160) ?: "receipt"
                    stream = context.contentResolver.openInputStream(source.uri) ?: throw ReceiptException(ReceiptFailure.UNREADABLE)
                }
            }
            val hash = MessageDigest.getInstance("SHA-256")
            stream.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    total += count; if (total > MAX_BYTES) throw ReceiptException(ReceiptFailure.TOO_LARGE)
                    hash.update(buffer, 0, count); output.write(buffer, 0, count)
                }
            } }
            val head = temporary.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
            val ascii = head.toString(Charsets.ISO_8859_1)
            val mime = when {
                ascii.startsWith("%PDF-") -> "application/pdf"
                head[0] == 0xff.toByte() && head[1] == 0xd8.toByte() -> "image/jpeg"
                head.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)) -> "image/png"
                ascii.startsWith("RIFF") && ascii.substring(8,12) == "WEBP" -> "image/webp"
                text || claimedMime == "text/plain" -> "text/plain"
                else -> throw ReceiptException(ReceiptFailure.UNSUPPORTED)
            }
            if (temporary.length() == 0L) throw ReceiptException(ReceiptFailure.UNREADABLE)
            val digest = hash.digest().joinToString("") { "%02x".format(it) }
            val extension = when(mime) { "application/pdf" -> "pdf"; "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "txt" }
            val target = File(directory, "$digest.$extension")
            if (!target.exists() && !temporary.renameTo(target)) throw ReceiptException(ReceiptFailure.UNREADABLE)
            ReceiptFile("money/receipts/${target.name}", mime, digest, name)
        } finally { temporary.delete() }
    }
}
