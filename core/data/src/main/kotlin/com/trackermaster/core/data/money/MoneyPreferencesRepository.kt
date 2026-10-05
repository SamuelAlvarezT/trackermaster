package com.trackermaster.core.data.money

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private val Context.moneyDataStore by preferencesDataStore("money_settings")

data class MoneyPreferences(
    val detailed: Boolean = false,
    val sections: Boolean = false,
    val hideAmounts: Boolean = false,
    val lastAccountId: Long? = null,
    val lastPocketId: Long? = null,
)

@Singleton
class MoneyPreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val detailed = booleanPreferencesKey("detailed")
    private val sections = booleanPreferencesKey("sections")
    private val privacy = booleanPreferencesKey("privacy")
    private val account = longPreferencesKey("last_account")
    private val pocket = longPreferencesKey("last_pocket")
    private val privacyOverride = MutableStateFlow<Boolean?>(null)
    private val persisted = context.moneyDataStore.data.map { values ->
        MoneyPreferences(values[detailed] ?: false, values[sections] ?: false, values[privacy] ?: false,
            values[account], values[pocket])
    }
    // All active surfaces share the mask immediately, including if disk persistence fails.
    val preferences: Flow<MoneyPreferences> = combine(persisted, privacyOverride) { values, _ ->
        values.copy(hideAmounts = privacyOverride.value ?: values.hideAmounts)
    }
    suspend fun setAppearance(isDetailed: Boolean, useSections: Boolean) {
        context.moneyDataStore.edit { it[detailed] = isDetailed; it[sections] = useSections }
    }
    suspend fun setPrivacy(hidden: Boolean) {
        privacyOverride.value = hidden
        context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName).putExtra(EXTRA_HIDE, hidden))
        context.moneyDataStore.edit { it[privacy] = hidden }
    }
    suspend fun rememberFunding(accountId: Long, pocketId: Long?) {
        context.moneyDataStore.edit {
            it[account] = accountId
            if (pocketId == null) it.remove(pocket) else it[pocket] = pocketId
        }
    }
    fun notifyMoneyChanged() {
        context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
    }
    companion object {
        const val ACTION_CHANGED = "com.trackermaster.MONEY_CHANGED"
        const val EXTRA_HIDE = "hide_amounts"
    }
}
