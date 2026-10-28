package com.trackermaster.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.model.BackupFrequency
import com.trackermaster.core.domain.model.DefumblrInterval
import com.trackermaster.core.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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
        val DEFUMBLR = booleanPreferencesKey("defumblr_enabled")
        val DEFUMBLR_INTERVAL = stringPreferencesKey("defumblr_interval")
        val DEFUMBLR_LAST_SHOWN = longPreferencesKey("defumblr_last_shown")
        val DEFUMBLR_MORNING_DATE = stringPreferencesKey("defumblr_morning_date")
        val LOCKSCREEN_WIDGETS = booleanPreferencesKey("lockscreen_widgets_enabled")
        val INITIAL_SECTION = stringPreferencesKey("initial_section")
        val APP_WAS_OPEN_BEFORE_SCREEN_OFF = booleanPreferencesKey("app_was_open_before_screen_off")
        val NEW_INTERFACE = booleanPreferencesKey("new_interface_enabled")
        val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
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
            defumblrEnabled = p[Keys.DEFUMBLR] ?: true,
            defumblrInterval = runCatching { DefumblrInterval.valueOf(p[Keys.DEFUMBLR_INTERVAL] ?: DefumblrInterval.ONE_HOUR.name) }
                .getOrDefault(DefumblrInterval.ONE_HOUR),
            defumblrLastShownAtMillis = p[Keys.DEFUMBLR_LAST_SHOWN] ?: 0L,
            defumblrMorningShownDate = p[Keys.DEFUMBLR_MORNING_DATE],
            lockscreenWidgetsEnabled = p[Keys.LOCKSCREEN_WIDGETS] ?: true,
            initialSection = p[Keys.INITIAL_SECTION] ?: "home",
            newInterfaceEnabled = p[Keys.NEW_INTERFACE] ?: false,
            backupFrequency = runCatching { BackupFrequency.valueOf(p[Keys.BACKUP_FREQUENCY] ?: BackupFrequency.APP_START.name) }
                .getOrDefault(BackupFrequency.APP_START),
            lastBackupAtMillis = p[Keys.LAST_BACKUP_AT] ?: 0L,
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        edit { it[Keys.THEME] = mode.name };refreshMoneyWidget()
    }
    suspend fun setAccent(index: Int) = edit { it[Keys.ACCENT] = index }
    suspend fun setLocale(tag: String) {
        edit { it[Keys.LOCALE] = tag };refreshMoneyWidget()
    }
    suspend fun setBiometric(enabled: Boolean) = edit { it[Keys.BIOMETRIC] = enabled }
    suspend fun setOnboardingComplete() = edit { it[Keys.ONBOARDING] = true }
    suspend fun setPomodoro(work: Int, shortBreak: Int, longBreak: Int) = edit {
        it[Keys.POMODORO_WORK] = work
        it[Keys.POMODORO_BREAK] = shortBreak
        it[Keys.POMODORO_LONG] = longBreak
    }
    suspend fun setDefumblrEnabled(enabled: Boolean) = edit { it[Keys.DEFUMBLR] = enabled }
    suspend fun setDefumblrInterval(interval: DefumblrInterval) = edit { it[Keys.DEFUMBLR_INTERVAL] = interval.name }
    suspend fun setDefumblrLastShownAtMillis(value: Long) = edit { it[Keys.DEFUMBLR_LAST_SHOWN] = value }
    suspend fun setDefumblrMorningShownDate(value: String?) = edit {
        if (value == null) it.remove(Keys.DEFUMBLR_MORNING_DATE) else it[Keys.DEFUMBLR_MORNING_DATE] = value
    }
    suspend fun setLockscreenWidgetsEnabled(enabled: Boolean) = edit { it[Keys.LOCKSCREEN_WIDGETS] = enabled }
    suspend fun setInitialSection(section: String) = edit { it[Keys.INITIAL_SECTION] = section }
    suspend fun setAppWasOpenBeforeScreenOff(value: Boolean) = edit { it[Keys.APP_WAS_OPEN_BEFORE_SCREEN_OFF] = value }
    suspend fun wasAppOpenBeforeScreenOff(): Boolean = context.dataStore.data.first()[Keys.APP_WAS_OPEN_BEFORE_SCREEN_OFF] ?: false

    suspend fun shouldShowAndRecordDefumblr(nowMillis: Long = System.currentTimeMillis()): Boolean {
        var shouldShow = false
        edit { preferences ->
            val enabled = preferences[Keys.DEFUMBLR] ?: true
            if (!enabled) return@edit
            val today = LocalDate.now().toString()
            val lastShown = preferences[Keys.DEFUMBLR_LAST_SHOWN] ?: 0L
            val interval = runCatching {
                DefumblrInterval.valueOf(preferences[Keys.DEFUMBLR_INTERVAL] ?: DefumblrInterval.ONE_HOUR.name)
            }.getOrDefault(DefumblrInterval.ONE_HOUR)
            val firstUnlockToday = preferences[Keys.DEFUMBLR_MORNING_DATE] != today
            val intervalElapsed = lastShown == 0L || nowMillis - lastShown >= interval.minutes * 60_000L
            shouldShow = firstUnlockToday || intervalElapsed
            if (shouldShow) {
                preferences[Keys.DEFUMBLR_LAST_SHOWN] = nowMillis
                preferences[Keys.DEFUMBLR_MORNING_DATE] = today
            }
        }
        return shouldShow
    }
    suspend fun setNewInterfaceEnabled(enabled: Boolean) = edit { it[Keys.NEW_INTERFACE] = enabled }
    suspend fun setBackupFrequency(frequency: BackupFrequency) = edit { it[Keys.BACKUP_FREQUENCY] = frequency.name }
    suspend fun setLastBackupAtMillis(value: Long) = edit { it[Keys.LAST_BACKUP_AT] = value }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
    private fun refreshMoneyWidget() {
        context.sendBroadcast(android.content.Intent(com.trackermaster.core.data.money.MoneyPreferencesRepository.ACTION_CHANGED).setPackage(context.packageName))
    }
}
