package com.trackermaster.core.widgets

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
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
        val balance = runBlocking { WidgetHiltEntryPoint.get(context).expenseRepository().totalBalance().first() }
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            WidgetBox("Balance", "$${"%.0f".format(balance)}", action)
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
        val summary = runBlocking { WidgetHiltEntryPoint.get(context).dashboardRepository().observeSummary().first() }
        val action = actionStartActivity(ComponentName(context.packageName, "com.trackermaster.app.MainActivity"))
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(android.graphics.Color.parseColor("#1E1E1E"))).padding(12.dp)
                    .clickable(action),
            ) {
                Text("Trackermaster", style = TextStyle(fontSize = 14.sp))
                Text("Habits: ${summary.habitsCompletedToday}/${summary.habitsDueToday}")
                Text("Mood: ${summary.latestMoodLevel ?: "—"}")
                Text("Balance: $${"%.0f".format(summary.totalBalance)}")
                Text("Focus: ${summary.focusScoreWeek}")
                Text("Journal: ${summary.journalStreak}d streak")
            }
        }
    }
}

@Composable
private fun WidgetBox(title: String, subtitle: String, action: Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(android.graphics.Color.parseColor("#1E1E1E"))).padding(12.dp).clickable(action),
    ) {
        Text(title, style = TextStyle(fontSize = 14.sp))
        Text(subtitle, style = TextStyle(fontSize = 12.sp))
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
