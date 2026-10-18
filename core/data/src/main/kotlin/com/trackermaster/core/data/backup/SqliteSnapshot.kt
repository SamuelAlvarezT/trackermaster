package com.trackermaster.core.data.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/** A single SQLite image, read from one Room transaction; no live DB/WAL file copies. */
object SqliteSnapshot {
    fun quote(name: String)="\"${name.replace("\"","\"\"")}\""
    fun copy(source: SupportSQLiteDatabase,target: File,triggers: MutableList<String> = mutableListOf()): Map<String,Long> {
        require(!target.exists())
        val definitions=mutableListOf<Triple<String,String,String>>()
        source.query("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY type,name").use { c ->
            while(c.moveToNext()) definitions+=Triple(c.getString(0),c.getString(1),c.getString(2))
        }
        triggers.addAll(definitions.filter { it.first=="trigger" }.map { it.third })
        val counts=linkedMapOf<String,Long>()
        SQLiteDatabase.openOrCreateDatabase(target,null).use { out ->
            out.transaction {
                definitions.filter { it.first=="table" }.forEach { (_,name,sql) ->
                    if(name=="android_metadata") out.execSQL("DROP TABLE IF EXISTS android_metadata")
                    out.execSQL(sql)
                    source.query("SELECT * FROM ${quote(name)}").use { rows ->
                        val columns=rows.columnNames.joinToString(",") { quote(it) }
                        val placeholders=rows.columnNames.joinToString(",") { "?" }
                        out.compileStatement("INSERT INTO ${quote(name)} ($columns) VALUES($placeholders)").use { insert ->
                            var count=0L
                            while(rows.moveToNext()) {
                                insert.clearBindings()
                                for(index in 0 until rows.columnCount) when(rows.getType(index)) {
                                    Cursor.FIELD_TYPE_NULL -> insert.bindNull(index+1)
                                    Cursor.FIELD_TYPE_INTEGER -> insert.bindLong(index+1,rows.getLong(index))
                                    Cursor.FIELD_TYPE_FLOAT -> insert.bindDouble(index+1,rows.getDouble(index))
                                    Cursor.FIELD_TYPE_BLOB -> insert.bindBlob(index+1,rows.getBlob(index))
                                    else -> insert.bindString(index+1,rows.getString(index))
                                }
                                insert.executeInsert();count++
                            }
                            counts[name]=count
                        }
                    }
                }
                source.query("SELECT name FROM sqlite_master WHERE name='sqlite_sequence'").use { sequence ->
                    if(sequence.moveToFirst()) source.query("SELECT name,seq FROM sqlite_sequence").use { rows ->
                        out.execSQL("DELETE FROM sqlite_sequence")
                        while(rows.moveToNext()) out.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)",arrayOf(rows.getString(0),rows.getLong(1)))
                    }
                }
                definitions.filter { it.first!="table" && it.first!="trigger" }.forEach { (_,_,sql) -> out.execSQL(sql) }
                out.version=source.version
            }
            validate(out)
        }
        return counts
    }
    fun validate(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA quick_check",null).use { require(it.moveToFirst() && it.getString(0)=="ok") { "Invalid SQLite image" } }
        db.rawQuery("PRAGMA foreign_key_check",null).use { require(!it.moveToFirst()) { "Broken database references" } }
    }
    fun detachTriggers(db: SQLiteDatabase): List<String> {
        val triggers=mutableListOf<Pair<String,String>>()
        db.rawQuery("SELECT name,sql FROM sqlite_master WHERE type='trigger' AND sql IS NOT NULL",null).use { rows ->
            while(rows.moveToNext()) triggers+=rows.getString(0) to rows.getString(1)
        }
        triggers.forEach { db.execSQL("DROP TRIGGER ${quote(it.first)}") }
        return triggers.map { it.second }
    }
}
