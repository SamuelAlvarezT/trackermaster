package com.trackermaster.feature.insights

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trackermaster.core.database.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

class LifeNativeMigrationTest {
    @Test fun existingThirteenDatabaseMigratesOnAndroidWithoutLosingJournalImages(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="life-migration-${java.util.UUID.randomUUID()}.db"
        val schema=JSONObject(context.assets.open("com.trackermaster.core.database.TrackermasterDatabase/13.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        val file=context.getDatabasePath(name).apply {parentFile!!.mkdirs()}
        SQLiteDatabase.openOrCreateDatabase(file,null).use { sql ->
            val entities=schema.getJSONArray("entities")
            for(index in 0 until entities.length()) {
                val entity=entities.getJSONObject(index)
                sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                val indices=entity.getJSONArray("indices")
                for(i in 0 until indices.length()) sql.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            sql.execSQL("INSERT INTO journal_entries(id,title,richTextHtml,createdAtEpoch,updatedAtEpoch,moodLevel) VALUES(1,'Original','Preservar',1680000000000,1690000000000,4)")
            sql.execSQL("INSERT INTO attachments VALUES(1,1,'/original/image.jpg','image/jpeg')")
            sql.version=13
        }
        val db=Room.databaseBuilder(context,TrackermasterDatabase::class.java,name).addMigrations(LifeMigration.MIGRATION_13_14).build()
        try {
            val entry=db.journalDao().getById(1)!!
            assertEquals("Original",entry.title);assertEquals("PERSONAL",entry.kind);assertNull(entry.contentEpochDay)
            assertEquals(1680000000000L,entry.createdAtEpoch);assertEquals(1690000000000L,entry.updatedAtEpoch)
            assertEquals("/original/image.jpg",db.journalDao().getAttachments(1).first().path)
            assertEquals(14,db.openHelper.writableDatabase.version)
        } finally {db.close()}
    }
}
