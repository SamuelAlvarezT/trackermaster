package com.trackermaster.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("trackermaster_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = intPreferencesKey("accent")
        val LOCALE = stringPreferencesKey("locale")
        val BIOMETRIC = booleanPreferencesKey("biometric")
        val ONBOARDING = booleanPreferencesKey("onboarding")
        val POMODORO_WORK = intPreferencesKey("pomodoro_work")
        val POMODORO_BREAK = intPreferencesKey("pomodoro_break")
        val POMODORO_LONG = intPreferencesKey("pomodoro_long")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: ThemeMode.DARK.name) }
                .getOrDefault(ThemeMode.DARK),
            accentIndex = p[Keys.ACCENT] ?: 0,
            localeTag = p[Keys.LOCALE] ?: "en",
            biometricEnabled = p[Keys.BIOMETRIC] ?: false,
            onboardingComplete = p[Keys.ONBOARDING] ?: false,
            pomodoroWorkMinutes = p[Keys.POMODORO_WORK] ?: 25,
            pomodoroBreakMinutes = p[Keys.POMODORO_BREAK] ?: 5,
            pomodoroLongBreakMinutes = p[Keys.POMODORO_LONG] ?: 15,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setAccent(index: Int) = edit { it[Keys.ACCENT] = index }
    suspend fun setLocale(tag: String) = edit { it[Keys.LOCALE] = tag }
    suspend fun setBiometric(enabled: Boolean) = edit { it[Keys.BIOMETRIC] = enabled }
    suspend fun setOnboardingComplete() = edit { it[Keys.ONBOARDING] = true }
    suspend fun setPomodoro(work: Int, shortBreak: Int, longBreak: Int) = edit {
        it[Keys.POMODORO_WORK] = work
        it[Keys.POMODORO_BREAK] = shortBreak
        it[Keys.POMODORO_LONG] = longBreak
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
