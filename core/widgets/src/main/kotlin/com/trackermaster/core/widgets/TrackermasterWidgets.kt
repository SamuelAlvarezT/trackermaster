package com.trackermaster.core.widgets

import android.content.ComponentName
import android.content.Context
import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import com.trackermaster.core.domain.money.formatBalances
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.model.DashboardSummary
import com.trackermaster.core.data.money.MoneyPreferences
import kotlinx.coroutines.flow.catch
import androidx.glance.action.Action
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.trackermaster.core.data.repository.DashboardRepository
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.data.repository.MoodRepository
import com.trackermaster.core.domain.HabitScheduleLogic
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class HabitProgressWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetHiltEntryPoint.get(context)
        val today = runBlocking { entry.habitRepository().observeTodayHabits().first() }
        val done = today.count { (_, log) -> log != null && HabitScheduleLogic.isLogComplete(log) }
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            WidgetBox("Habits Today", "$done / ${today.size}", action)
        }
    }
}

class StreakOverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetHiltEntryPoint.get(context)
        val habits = runBlocking { entry.habitRepository().observeActiveHabits().first() }
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            WidgetBox("Streaks", "${habits.size} active habits", action)
        }
    }
}

class MoodLogWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            WidgetBox("Log Mood", "Tap to open", action)
        }
    }
}

class ExpenseBalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetHiltEntryPoint.get(context)
        val balances = entry.expenseRepository().balancesByCurrency().flowOn(Dispatchers.Default)
        val initialBalances = balances.first()
        val privacyFlow = entry.moneyPreferencesRepository().preferences.catch { emit(MoneyPreferences(hideAmounts = true)) }
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            val privacy by privacyFlow.collectAsState(MoneyPreferences(hideAmounts = true))
            val moneyContext = moneyWidgetContext(entry, context)
            val currentBalances by balances.collectAsState(initialBalances)
            WidgetBox(moneyContext.getString(R.string.money_balance_title),
                if (privacy.hideAmounts) moneyContext.getString(R.string.money_balance_hidden)
                else currentBalances.formatBalances(moneyContext.resources.configuration.locales[0]), action,
                textColor = ColorProvider(Color.White))
        }
    }
}

class FocusQuickStartWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            WidgetBox("Focus", "Start Pomodoro", action)
        }
    }
}

class DashboardComboWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetHiltEntryPoint.get(context)
        val summaries = entry.dashboardRepository().observeSummary().flowOn(Dispatchers.Default)
        val initialSummary = summaries.first()
        val privacyFlow = entry.moneyPreferencesRepository().preferences.catch { emit(MoneyPreferences(hideAmounts = true)) }
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            val privacy by privacyFlow.collectAsState(MoneyPreferences(hideAmounts = true))
            val moneyContext = moneyWidgetContext(entry, context)
            val summary by summaries.collectAsState(initialSummary)
            Column(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF1E1E1E))).padding(12.dp)
                    .clickable(action),
            ) {
                Text("Trackermaster", style = TextStyle(fontSize = 14.sp, color = ColorProvider(Color.White)))
                Text("Habits: ${summary.habitsCompletedToday}/${summary.habitsDueToday}", style = TextStyle(color = ColorProvider(Color.White)))
                Text("Mood: ${summary.latestMoodLevel ?: "—"}", style = TextStyle(color = ColorProvider(Color.White)))
                Text(moneyContext.getString(R.string.money_balance_line,
                    if (privacy.hideAmounts) moneyContext.getString(R.string.money_balance_hidden)
                    else summary.balancesByCurrency.formatBalances(moneyContext.resources.configuration.locales[0])), style = TextStyle(color = ColorProvider(Color.White)))
                Text("Focus: ${summary.focusScoreWeek}", style = TextStyle(color = ColorProvider(Color.White)))
                Text("Journal: ${summary.journalStreak}d streak", style = TextStyle(color = ColorProvider(Color.White)))
            }
        }
    }
}

@Composable
private fun WidgetBox(title: String, subtitle: String, action: Action,
    textColor: ColorProvider = ColorProvider(Color.Black)) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF1E1E1E))).padding(12.dp).clickable(action),
    ) {
        Text(title, style = TextStyle(fontSize = 14.sp, color = textColor))
        Text(subtitle, style = TextStyle(fontSize = 12.sp, color = textColor))
    }
}

class HabitProgressWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = HabitProgressWidget() }
class StreakOverviewWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = StreakOverviewWidget() }
class MoodLogWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = MoodLogWidget() }
class ExpenseBalanceWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = ExpenseBalanceWidget() }
class FocusQuickStartWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = FocusQuickStartWidget() }
class DashboardComboWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = DashboardComboWidget() }

object WidgetRefresh {
    suspend fun refreshAll(context: Context) {
        HabitProgressWidget().updateAll(context)
        StreakOverviewWidget().updateAll(context)
        MoodLogWidget().updateAll(context)
        ExpenseBalanceWidget().updateAll(context)
        FocusQuickStartWidget().updateAll(context)
        DashboardComboWidget().updateAll(context)
    }
}

// The host app disables language splits, so changing locale needs no network download.
@SuppressLint("AppBundleLocaleChanges")
@Composable
private fun moneyWidgetContext(entry: WidgetHiltEntryPoint, context: Context): Context {
    val flow = remember(entry) { entry.settingsRepository().settings }
    val settings by flow.collectAsState(AppSettings(localeTag = context.resources.configuration.locales[0].toLanguageTag()))
    return remember(context, settings.localeTag) {
        context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(settings.localeTag))
        })
    }
}
