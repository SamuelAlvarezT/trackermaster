package com.trackermaster.core.data.progress

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusAlarmData by preferencesDataStore("focus_alarm")

data class FocusAlarmState(
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val armedSessionId: Long = 0,
    val activeSessionId: Long = 0,
    val dismissedSessionId: Long = 0,
    val fallbackSessionId: Long = 0,
    val pendingStartAfter: Long? = null,
)

/** Alarm selection and session identity live outside UI state and survive process recreation. */
@Singleton
class FocusAlarmStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Key {
        val sound = stringPreferencesKey("sound_uri")
        val vibration = booleanPreferencesKey("vibrate")
        val armed = longPreferencesKey("armed_session")
        val active = longPreferencesKey("active_session")
        val dismissed = longPreferencesKey("dismissed_session")
        val fallback = longPreferencesKey("fallback_session")
        val pending = longPreferencesKey("pending_start_after")
    }
    val state = context.focusAlarmData.data.map { p ->
        FocusAlarmState(p[Key.sound], p[Key.vibration] ?: true, p[Key.armed] ?: 0,
            p[Key.active] ?: 0, p[Key.dismissed] ?: 0, p[Key.fallback] ?: 0, p[Key.pending])
    }
    suspend fun saveSelection(uri: String?, vibrate: Boolean) {
        context.focusAlarmData.edit { p ->
            if (uri == null) p.remove(Key.sound) else p[Key.sound] = uri
            p[Key.vibration] = vibrate
        }
    }
    suspend fun beginStart(previousSessionId: Long) {
        context.focusAlarmData.edit { it[Key.pending] = previousSessionId.coerceAtLeast(0) }
    }
    suspend fun arm(sessionId: Long) {
        require(sessionId > 0)
        context.focusAlarmData.edit { p ->
            if (p[Key.dismissed] != sessionId) { p[Key.armed] = sessionId; p[Key.active] = 0 }
            p.remove(Key.pending)
        }
    }
    suspend fun activate(sessionId: Long): Boolean {
        var activated = false
        context.focusAlarmData.edit { p ->
            if (p[Key.armed] == sessionId && p[Key.dismissed] != sessionId) {
                p[Key.active] = sessionId; activated = true
            }
        }
        return activated
    }
    suspend fun dismiss(sessionId: Long) {
        context.focusAlarmData.edit { p ->
            val pending = p[Key.pending]
            if (p[Key.armed] == sessionId || p[Key.active] == sessionId || (pending != null && sessionId > pending)) {
                p[Key.dismissed] = sessionId; p[Key.armed] = 0; p[Key.active] = 0; p.remove(Key.pending)
            }
        }
    }
    suspend fun recordFallback(sessionId: Long) { context.focusAlarmData.edit { it[Key.fallback] = sessionId } }
    suspend fun clearFallback() { context.focusAlarmData.edit { it[Key.fallback] = 0 } }
}
