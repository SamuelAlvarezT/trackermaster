package com.trackermaster.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "money_spaces", indices = [Index(value = ["kind", "name"], unique = true)])
data class MoneySpaceEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val kind: String, val name: String = "")

@Entity(tableName = "money_pockets", indices = [Index("accountId"), Index("spaceId")], foreignKeys = [
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = MoneySpaceEntity::class, parentColumns = ["id"], childColumns = ["spaceId"], onDelete = ForeignKey.RESTRICT),
])
data class MoneyPocketEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val accountId: Long, val spaceId: Long, val name: String)

@Entity(tableName = "money_transfers", indices = [Index("sourceAccountId"), Index("targetAccountId")], foreignKeys = [
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["sourceAccountId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["targetAccountId"], onDelete = ForeignKey.RESTRICT),
])
data class MoneyTransferEntity(@PrimaryKey val id: String, val sourceAccountId: Long, val targetAccountId: Long)
