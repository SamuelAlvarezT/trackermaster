package com.trackermaster.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Runs in Application.attachBaseContext, before providers, Hilt, Room or DataStore open files. */
object RestoreBootstrap {
    private fun pending(context: Context)=File(context.noBackupFilesDir,"restore-pending.json")
    private fun journal(context: Context)=File(context.noBackupFilesDir,"restore-apply.json")
    fun hasPending(context: Context)=pending(context).isFile
    private fun atomic(file: File,value: String) {
        file.parentFile!!.mkdirs()
        val temporary=File(file.path+".tmp")
        FileOutputStream(temporary).use { it.write(value.toByteArray(Charsets.UTF_8));it.fd.sync() }
        java.nio.file.Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    fun schedule(context: Context,folder: File) {
        val root=File(context.noBackupFilesDir,"restore-staging").canonicalFile
        require(folder.canonicalPath.startsWith(root.path+File.separator))
        check(!hasPending(context))
        atomic(pending(context),JSONObject().put("folder",folder.canonicalPath).toString())
    }
    private fun checked(context: Context,path: String,subdirectory: String): File {
        val root=File(context.noBackupFilesDir,subdirectory).canonicalFile
        val target=File(path).canonicalFile
        require(target.path.startsWith(root.path+File.separator))
        return target
    }
    fun applyPending(context: Context,afterFilesInstalled: ()->Unit={}) {
        val active=journal(context)
        if(active.isFile) {
            val state=JSONObject(active.readText())
            if(state.optString("phase")=="APPLIED") {
                pending(context).delete()
                File(context.noBackupFilesDir,"restore-last-result").writeText("APPLIED")
                File(context.noBackupFilesDir,"restore-refresh-required").writeText("1")
                runCatching { context.getSystemService(android.app.NotificationManager::class.java).cancelAll() }
                return
            }
            rollback(context,state)
            pending(context).delete();active.delete()
            File(context.noBackupFilesDir,"restore-last-result").writeText("ROLLED_BACK")
            return
        }
        if(!pending(context).isFile) return
        var state: JSONObject?=null
        try {
            val folder=checked(context,JSONObject(pending(context).readText()).getString("folder"),"restore-staging")
            val prepared=JSONObject(File(folder,"prepared.json").readText())
            require(prepared.getInt("schema") in 13..14)
            val entries=prepared.getJSONObject("entries")
            for(name in entries.keys()) {
                val file=BackupFiles.target(folder,name);val expected=entries.getJSONObject(name)
                require(file.isFile && file.length()==expected.getLong("bytes") && BackupFiles.sha(file)==expected.getString("sha256"))
            }
            val image=File(folder,"database/trackermaster.db")
            SQLiteDatabase.openDatabase(image.path,null,SQLiteDatabase.OPEN_READONLY).use { require(it.version==prepared.getInt("schema"));SqliteSnapshot.validate(it) }
            val previous=File(context.noBackupFilesDir,"restore-rollback/${folder.name}").apply { check(mkdirs()) }
            val database=context.getDatabasePath("trackermaster.db")
            val files=context.filesDir
            val marker=File(context.noBackupFilesDir,"cloud-sync-restored")
            state=JSONObject().put("phase","PREPARED").put("folder",folder.path).put("rollback",previous.path)
                .put("had_database",database.exists()).put("had_wal",File(database.path+"-wal").exists()).put("had_shm",File(database.path+"-shm").exists()).put("had_files",files.exists()).put("had_cloud_marker",marker.exists())
            if(marker.exists()) marker.copyTo(File(previous,"cloud-marker"))
            atomic(active,state.toString())
            val oldDb=File(previous,"database").apply { mkdirs() }
            for(suffix in listOf("","-wal","-shm")) {
                val file=File(database.path+suffix)
                if(file.exists()) {
                    val saved=File(oldDb,"trackermaster.db$suffix")
                    try { java.nio.file.Files.createLink(saved.toPath(),file.toPath()) }
                    catch(_: Exception) {
                        // A failed/partial copy must never be mistaken for a usable rollback image.
                        val copying=File(saved.path+".copying")
                        try {
                            FileOutputStream(copying).use { output -> file.inputStream().use { it.copyTo(output) };output.fd.sync() }
                            require(copying.length()==file.length())
                            check(copying.renameTo(saved))
                        } finally { copying.delete() }
                    }
                }
            }
            for(suffix in listOf("","-wal","-shm")) File(database.path+suffix).delete()
            val oldFiles=File(previous,"files")
            if(files.exists()) check(files.renameTo(oldFiles))
            val newFiles=File(folder,"files").apply { mkdirs() }
            check(newFiles.renameTo(files))
            // Existing user backup archives are intentionally retained across restoration.
            val backups=File(oldFiles,"backups")
            if(backups.exists()) check(backups.renameTo(File(files,"backups")))
            afterFilesInstalled()
            database.parentFile!!.mkdirs()
            check(image.renameTo(database))
            SQLiteDatabase.openDatabase(database.path,null,SQLiteDatabase.OPEN_READONLY).use { SqliteSnapshot.validate(it) }
            marker.writeText("restart required")
            state.put("phase","APPLIED");atomic(active,state.toString())
            pending(context).delete()
            File(context.noBackupFilesDir,"restore-last-result").writeText("APPLIED")
            File(context.noBackupFilesDir,"restore-refresh-required").writeText("1")
            runCatching { context.getSystemService(android.app.NotificationManager::class.java).cancelAll() }
        } catch(failure: Exception) {
            state?.let { rollback(context,it) }
            pending(context).delete();active.delete()
            File(context.noBackupFilesDir,"restore-last-result").writeText("ROLLED_BACK")
        }
    }
    private fun rollback(context: Context,state: JSONObject) {
        val previous=checked(context,state.getString("rollback"),"restore-rollback")
        val database=context.getDatabasePath("trackermaster.db")
        val old=File(previous,"database/trackermaster.db")
        for((suffix,had) in listOf("" to "had_database","-wal" to "had_wal","-shm" to "had_shm")) {
            val saved=File(old.path+suffix)
            val current=File(database.path+suffix)
            if(saved.exists()) { current.delete();check(saved.renameTo(current)) }
            else if(!state.optBoolean(had,false)) current.delete()
        }
        val oldFiles=File(previous,"files")
        if(oldFiles.exists()) {
            val files=context.filesDir
            val backups=File(files,"backups")
            if(backups.exists() && !File(oldFiles,"backups").exists()) check(backups.renameTo(File(oldFiles,"backups")))
            if(files.exists()) check(files.deleteRecursively())
            check(oldFiles.renameTo(files))
        }
        val marker=File(context.noBackupFilesDir,"cloud-sync-restored")
        if(state.getBoolean("had_cloud_marker")) File(previous,"cloud-marker").copyTo(marker,overwrite=true) else marker.delete()
    }
    /** Deferred until normal application initialization; large old payloads do not delay startup. */
    fun cleanup(context: Context) {
        val file=journal(context)
        if(!file.isFile) {
            if(!hasPending(context)) {
                val root=File(context.noBackupFilesDir,"restore-staging").canonicalFile
                root.listFiles()?.filter { it.isDirectory && System.currentTimeMillis()-it.lastModified()>86_400_000L }?.forEach {
                    checked(context,it.path,"restore-staging").deleteRecursively()
                }
            }
            return
        }
        val state=JSONObject(file.readText())
        if(state.optString("phase")!="APPLIED") return
        checked(context,state.getString("rollback"),"restore-rollback").deleteRecursively()
        checked(context,state.getString("folder"),"restore-staging").deleteRecursively()
        file.delete()
    }
}
