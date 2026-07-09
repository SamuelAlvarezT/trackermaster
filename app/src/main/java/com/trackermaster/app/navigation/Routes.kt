package com.trackermaster.app.navigation

sealed class Routes(val route: String, val label: String) {
    data object Home : Routes("home", "Home")
    data object Habits : Routes("habits", "Habits")
    data object Mood : Routes("mood", "Mood")
    data object Expense : Routes("expense", "Money")
    data object Focus : Routes("focus", "Focus")
    data object Journal : Routes("journal", "Journal")
    data object Insights : Routes("insights", "Insights")
    data object Settings : Routes("settings", "Settings")
    data object MoodCalendar : Routes("mood_calendar", "Calendar")
    data object Achievements : Routes("achievements", "Badges")
    data object Tasks : Routes("tasks", "Tasks")
}
