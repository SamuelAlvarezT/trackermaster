package com.trackermaster.core.domain.money

/** A group is an index over existing records, never a ledger movement. */
data class MoneyGroup(val id: String, val name: String, val createdAt: Long, val memberKeys: List<String>)
data class MoneyGroupRules(val sameDate: Boolean = false, val sameAccount: Boolean = false)
