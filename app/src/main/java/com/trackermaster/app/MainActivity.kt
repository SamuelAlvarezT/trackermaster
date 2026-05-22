package com.trackermaster.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trackermaster.app.navigation.Routes
import com.trackermaster.app.onboarding.OnboardingScreen
import com.trackermaster.app.security.BiometricGate
import com.trackermaster.app.ui.DashboardScreen
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.ui.theme.TrackermasterTheme
import com.trackermaster.feature.expense.ExpenseScreen
import com.trackermaster.feature.focus.FocusScreen
import com.trackermaster.feature.habits.AchievementsScreen
import com.trackermaster.feature.habits.HabitsScreen
import com.trackermaster.feature.insights.InsightsScreen
import com.trackermaster.feature.journal.JournalScreen
import com.trackermaster.feature.mood.MoodCalendarScreen
import com.trackermaster.feature.mood.MoodScreen
import com.trackermaster.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTab = intent?.getStringExtra("tab")

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = com.trackermaster.core.domain.model.AppSettings()
            )
            var unlocked by remember { mutableStateOf(!settings.biometricEnabled) }

            TrackermasterTheme(settings.themeMode, settings.accentIndex) {
                if (!unlocked && settings.biometricEnabled) {
                    BiometricGate(activity = this, onUnlocked = { unlocked = true })
                } else if (!settings.onboardingComplete) {
                    OnboardingScreen(onComplete = {
                        lifecycleScope.launch { settingsRepository.setOnboardingComplete() }
                    })
                } else {
                    TrackermasterRoot(startTab = startTab)
                }
            }
        }
    }
}

@Composable
fun TrackermasterRoot(startTab: String? = null) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    LaunchedEffect(startTab) {
        when (startTab) {
            "habits" -> navController.navigate(Routes.Habits.route)
            "mood" -> navController.navigate(Routes.Mood.route)
            "expense" -> navController.navigate(Routes.Expense.route)
            "focus" -> navController.navigate(Routes.Focus.route)
            "journal" -> navController.navigate(Routes.Journal.route)
            "insights" -> navController.navigate(Routes.Insights.route)
        }
    }

    val bottomItems = listOf(
        Routes.Home to Icons.Default.Home,
        Routes.Habits to Icons.Default.CheckCircle,
        Routes.Mood to Icons.Default.Mood,
        Routes.Expense to Icons.Default.AccountBalance,
        Routes.Focus to Icons.Default.Timer,
        Routes.Journal to Icons.Default.MenuBook,
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomItems.map { it.first.route }) {
                NavigationBar {
                    bottomItems.forEach { (route, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route.route,
                            onClick = { navController.navigate(route.route) { launchSingleTop = true } },
                            icon = { Icon(icon, contentDescription = route.label) },
                            label = { Text(route.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Home.route) { DashboardScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.Habits.route) { HabitsScreen() }
            composable(Routes.Mood.route) { MoodScreen() }
            composable(Routes.Expense.route) { ExpenseScreen() }
            composable(Routes.Focus.route) { FocusScreen() }
            composable(Routes.Journal.route) { JournalScreen() }
            composable(Routes.Insights.route) { InsightsScreen() }
            composable(Routes.Settings.route) { SettingsScreen() }
            composable(Routes.MoodCalendar.route) { MoodCalendarScreen() }
            composable(Routes.Achievements.route) { AchievementsScreen() }
        }
    }
}
