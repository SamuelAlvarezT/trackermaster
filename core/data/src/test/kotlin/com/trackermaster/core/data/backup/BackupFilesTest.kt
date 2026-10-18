package com.trackermaster.core.data.backup

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.nio.file.Files
import java.util.zip.*
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class BackupFilesTest {
    private fun archive(vararg entries: Pair<String,ByteArray>): ByteArray=ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name,bytes) -> zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry() } }
    }.toByteArray()
    private fun extract(bytes: ByteArray,check: (File)->Unit) {
        val root=Files.createTempDirectory("backup-test-").toFile()
        try { BackupFiles.extract(bytes.inputStream(),root);check(root) } finally { root.deleteRecursively() }
    }
    @Test fun rejectsTraversalAbsolutePathsAndRuntimeFiles() {
        for(path in listOf("../outside","files/../outside","/absolute","files/C:/x","files/a\\b","database/other.db","files/backups/recursive.zip","files/datastore/unlock_launch_runtime.preferences_pb")) {
            assertTrue(path,runCatching { extract(archive("database/trackermaster.db" to byteArrayOf(1),path to byteArrayOf(2))) {} }.isFailure)
        }
    }
    @Test fun legacyArchiveKeepsDatabaseAndEveryPrivateAttachment() {
        extract(archive("database/trackermaster.db" to byteArrayOf(1),"files/money/receipts/a.jpg" to byteArrayOf(2,3))) { root ->
            assertArrayEquals(byteArrayOf(2,3),File(root,"files/money/receipts/a.jpg").readBytes())
        }
    }
    @Test fun checksumMismatchRejectsAnOtherwiseCompleteArchive() {
        val manifest=JSONObject().put("format",2).put("entries",JSONObject().put("database/trackermaster.db",JSONObject().put("bytes",1).put("sha256","wrong")))
        assertTrue(runCatching { extract(archive("database/trackermaster.db" to byteArrayOf(1),"manifest.json" to manifest.toString().toByteArray())) {} }.isFailure)
    }
    @Test fun extraFilesAbsentFromManifestAreRejected() {
        val manifest=JSONObject().put("format",2).put("entries",JSONObject())
        assertTrue(runCatching { extract(archive("database/trackermaster.db" to byteArrayOf(1),"manifest.json" to manifest.toString().toByteArray())) {} }.isFailure)
    }
    @Test fun archiveWithoutDatabaseIsRejected() { assertTrue(runCatching { extract(archive("files/note.txt" to byteArrayOf(1))) {} }.isFailure) }
}
