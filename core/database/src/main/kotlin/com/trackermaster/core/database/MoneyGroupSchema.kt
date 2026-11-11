package com.trackermaster.core.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "money_groups")
data class MoneyGroupEntity(@PrimaryKey val id: String, val name: String, val createdAt: Long)

@Entity(tableName = "money_group_members", indices = [Index("groupId")],
    foreignKeys = [ForeignKey(entity = MoneyGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)])
data class MoneyGroupMemberEntity(@PrimaryKey val memberKey: String, val groupId: String)

data class MoneyGroupWithMembers(@Embedded val group: MoneyGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId") val members: List<MoneyGroupMemberEntity>)

@Dao
interface MoneyGroupDao {
    @Transaction @Query("SELECT * FROM money_groups ORDER BY createdAt DESC")
    fun observeGroups(): Flow<List<MoneyGroupWithMembers>>
    @Query("SELECT * FROM money_groups WHERE id = :id") suspend fun get(id: String): MoneyGroupEntity?
    @Query("SELECT * FROM money_group_members WHERE groupId = :id") suspend fun members(id: String): List<MoneyGroupMemberEntity>
    @Insert suspend fun insert(group: MoneyGroupEntity)
    @Insert suspend fun add(members: List<MoneyGroupMemberEntity>)
    @Query("DELETE FROM money_group_members WHERE memberKey = :key") suspend fun removeMember(key: String)
    @Query("DELETE FROM money_groups WHERE id = :id") suspend fun delete(id: String)
}

object MoneyGroupMigration {
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS money_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS money_group_members (memberKey TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, FOREIGN KEY(groupId) REFERENCES money_groups(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_money_group_members_groupId ON money_group_members(groupId)")
        }
    }
}
