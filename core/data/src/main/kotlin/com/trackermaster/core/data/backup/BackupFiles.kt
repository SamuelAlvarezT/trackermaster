package com.trackermaster.core.data.backup

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

object BackupFiles {
    const val MAX_TOTAL=1_073_741_824L
    const val MAX_FILES=20_000
    fun target(root: File,name: String): File {
        require(name.isNotBlank() && !name.startsWith('/') && !name.contains('\\') && !name.contains(':')) { "Invalid archive path" }
        require(name.split('/').none { it==".." || it=="." || it.isEmpty() }) { "Invalid archive path" }
        val file=File(root,name).canonicalFile
        require(file.path.startsWith(root.canonicalPath+File.separator)) { "Archive path escaped its directory" }
        return file
    }
    fun sha(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes=ByteArray(64*1024);while(true) { val n=input.read(bytes);if(n<0)break;digest.update(bytes,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun included(relative: String): Boolean = relative!="backups" && !relative.startsWith("backups/") &&
        relative!="datastore/unlock_launch_runtime.preferences_pb" &&
        !(relative.startsWith("datastore/") && (relative.endsWith(".tmp") || relative.endsWith(".lock")))

    fun extract(input: java.io.InputStream,folder: File): JSONObject? {
        require(folder.mkdirs() || folder.isDirectory)
        val seen=mutableSetOf<String>();var total=0L
        ZipInputStream(input).use { zip ->
            while(true) {
                val entry=zip.nextEntry ?: break
                if(entry.isDirectory) { target(folder,entry.name.trimEnd('/'));zip.closeEntry();continue }
                require(seen.add(entry.name) && seen.size<=MAX_FILES) { "Duplicate or excessive archive entries" }
                require(entry.name=="manifest.json" || entry.name.startsWith("database/") || entry.name.startsWith("files/")) { "Unexpected archive path" }
                if(entry.name.startsWith("database/")) require(entry.name in listOf("database/trackermaster.db","database/trackermaster.db-wal","database/trackermaster.db-shm"))
                if(entry.name.startsWith("files/")) require(included(entry.name.removePrefix("files/")))
                val output=target(folder,entry.name)
                output.parentFile!!.mkdirs()
                output.outputStream().use { stream ->
                    val buffer=ByteArray(64*1024)
                    while(true) {
                        val size=zip.read(buffer);if(size<0)break
                        total+=size;require(total<=MAX_TOTAL) { "Archive too large" }
                        stream.write(buffer,0,size)
                    }
                }
                zip.closeEntry()
            }
        }
        val manifest=File(folder,"manifest.json").takeIf { it.isFile }?.let { require(it.length()<=8*1024*1024);JSONObject(it.readText()) }
        if(manifest!=null) {
            require(manifest.getInt("format")==2) { "Unsupported backup format" }
            val entries=manifest.getJSONObject("entries")
            require(entries.keys().asSequence().toSet()==seen-"manifest.json") { "Incomplete archive manifest" }
            for(name in entries.keys()) {
                val expected=entries.getJSONObject(name);val file=target(folder,name)
                require(file.length()==expected.getLong("bytes") && sha(file)==expected.getString("sha256")) { "Archive checksum mismatch" }
            }
        }
        require(File(folder,"database/trackermaster.db").isFile) { "Database is missing" }
        return manifest
    }
}
