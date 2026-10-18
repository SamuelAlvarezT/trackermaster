package com.trackermaster.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import java.io.File
import java.util.UUID

object BackupAttachments {
    private const val PREFIX="backup-file://"
    private val columns=listOf("attachments" to "path","transactions" to "imageUri",
        "money_receipts" to "path","money_receipts" to "previewPath")

    /** Preserve private attachments and materialize legacy external URIs with existing grants. */
    fun prepare(context: Context,database: File,payload: File,oldRoot: String?,exporting: Boolean) {
        SQLiteDatabase.openDatabase(database.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                for((table,column) in columns) {
                    db.rawQuery("SELECT id,${SqliteSnapshot.quote(column)} FROM ${SqliteSnapshot.quote(table)} WHERE ${SqliteSnapshot.quote(column)} IS NOT NULL",null).use { rows ->
                        while(rows.moveToNext()) {
                            val value=rows.getString(1)
                            if(value.isNullOrBlank()) continue
                            val portable=value.replace('\\','/')
                            val root=oldRoot?.replace('\\','/')?.trimEnd('/')
                            val relative=when {
                                value.startsWith(PREFIX) -> value.removePrefix(PREFIX)
                                root!=null && portable.startsWith("$root/") -> portable.removePrefix("$root/")
                                !exporting && portable.contains("/files/") -> portable.substringAfterLast("/files/")
                                !File(value).isAbsolute && !value.contains("://") -> portable
                                else -> null
                            }
                            val archived=relative?.let { BackupFiles.target(payload,it) }
                            val destination=if(relative!=null && archived!!.isFile) {
                                relative
                            } else {
                                require(exporting) { "The archive is missing a referenced attachment" }
                                val copied="restored_attachments/${UUID.randomUUID()}.bin"
                                val target=BackupFiles.target(payload,copied).apply { parentFile!!.mkdirs() }
                                val uri=value.toUri()
                                val local=if(value.startsWith("file://")) File(requireNotNull(uri.path)) else File(value)
                                if(!value.startsWith("content://")) require(!local.canonicalPath.startsWith(context.dataDir.canonicalPath+File.separator) ||
                                    local.canonicalPath.startsWith(context.filesDir.canonicalPath+File.separator))
                                val stream=if(value.startsWith("content://")) context.contentResolver.openInputStream(uri)
                                    else local.takeIf { it.isFile }?.inputStream()
                                requireNotNull(stream) { "A referenced attachment cannot be read" }.use { input ->
                                    target.outputStream().use { output ->
                                        var bytes=0L;val buffer=ByteArray(64*1024)
                                        while(true) {
                                            val n=input.read(buffer);if(n<0)break
                                            bytes+=n;require(bytes<=BackupFiles.MAX_TOTAL) { "Attachment too large" }
                                            output.write(buffer,0,n)
                                        }
                                    }
                                }
                                require(target.length()<=BackupFiles.MAX_TOTAL) { "Attachment too large" }
                                copied
                            }
                            val normalized=if(exporting) PREFIX+destination else BackupFiles.target(context.filesDir,destination).path
                            db.execSQL("UPDATE ${SqliteSnapshot.quote(table)} SET ${SqliteSnapshot.quote(column)}=? WHERE id=?",arrayOf(normalized,rows.getString(0)))
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
}
