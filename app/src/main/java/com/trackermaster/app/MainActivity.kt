package com.trackermaster.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
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
import com.trackermaster.core.domain.model.BackupFrequency
import com.trackermaster.core.ui.theme.TrackermasterTheme
import com.trackermaster.feature.backup.BackupManager
import com.trackermaster.feature.expense.ExpenseScreen
import com.trackermaster.feature.focus.FocusScreen
import com.trackermaster.feature.habits.AchievementsScreen
import com.trackermaster.feature.habits.HabitsScreen
import com.trackermaster.feature.insights.InsightsScreen
import com.trackermaster.feature.journal.JournalScreen
import com.trackermaster.feature.mood.MoodCalendarScreen
import com.trackermaster.feature.settings.SettingsScreen
import com.trackermaster.app.lockscreen.DefumblrLockScreenService
import com.trackermaster.app.tasks.TasksScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var backupManager: BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        val startTab = intent?.getStringExtra("tab")

        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.defumblrEnabled) {
                startDefumblrLockScreenService()
            }
            if (shouldRunBackup(settings.backupFrequency, settings.lastBackupAtMillis)) {
                backupManager.exportLocalZip()
                settingsRepository.setLastBackupAtMillis(System.currentTimeMillis())
            }
        }

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

    private fun startDefumblrLockScreenService() {
        try {
            val serviceIntent = Intent(this, DefumblrLockScreenService::class.java)
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        @Suppress("DEPRECATION")
        val image = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (!text.isNullOrBlank() || image != null) {
            ShareIntentHandler.update(SharedContent(text = text, imageUri = image))
        }
    }

    private fun shouldRunBackup(frequency: BackupFrequency, lastBackupAtMillis: Long): Boolean {
        if (frequency == BackupFrequency.APP_START) return true
        if (lastBackupAtMillis == 0L) return true
        val elapsed = System.currentTimeMillis() - lastBackupAtMillis
        val dayMillis = 24L * 60L * 60L * 1000L
        return elapsed >= when (frequency) {
            BackupFrequency.APP_START -> 0L
            BackupFrequency.DAILY -> dayMillis
            BackupFrequency.WEEKLY -> dayMillis * 7L
            BackupFrequency.MONTHLY -> dayMillis * 30L
        }
    }
}

@Composable
fun TrackermasterRoot(startTab: String? = null) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val sharedContent by ShareIntentHandler.sharedContent.collectAsStateWithLifecycle()

    LaunchedEffect(startTab) {
        when (startTab) {
            "habits" -> navController.navigate(Routes.Habits.route)
            "tasks" -> navController.navigate(Routes.Tasks.route)
            "mood" -> navController.navigate(Routes.Journal.route)
            "expense" -> navController.navigate(Routes.Expense.route)
            "focus" -> navController.navigate(Routes.Focus.route)
            "journal" -> navController.navigate(Routes.Journal.route)
            "insights" -> navController.navigate(Routes.Insights.route)
        }
    }

    val bottomItems = listOf(
        Routes.Home to Icons.Default.Home,
        Routes.Habits to Icons.Default.CheckCircle,
        Routes.Tasks to Icons.AutoMirrored.Filled.List,
        Routes.Expense to Icons.Default.AccountBalance,
        Routes.Focus to Icons.Default.Timer,
        Routes.Journal to Icons.AutoMirrored.Filled.MenuBook,
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
            composable(Routes.Tasks.route) { TasksScreen() }
            composable(Routes.Mood.route) { JournalScreen() }
            composable(Routes.Expense.route) { ExpenseScreen() }
            composable(Routes.Focus.route) { FocusScreen() }
            composable(Routes.Journal.route) { JournalScreen() }
            composable(Routes.Insights.route) { InsightsScreen() }
            composable(Routes.Settings.route) { SettingsScreen() }
            composable(Routes.MoodCalendar.route) { MoodCalendarScreen() }
            composable(Routes.Achievements.route) { AchievementsScreen() }
        }
    }
    sharedContent?.let { content ->
        AlertDialog(
            onDismissRequest = { ShareIntentHandler.clear() },
            title = { Text("Import shared item") },
            text = { Text(content.text ?: content.imageUri?.toString().orEmpty()) },
            confirmButton = {
                TextButton({
                    navController.navigate(Routes.Expense.route) { launchSingleTop = true }
                    ShareIntentHandler.clear()
                }) { Text("Log Expense") }
            },
            dismissButton = {
                TextButton({
                    navController.navigate(Routes.Journal.route) { launchSingleTop = true }
                    ShareIntentHandler.clear()
                }) { Text("Add to Journal") }
            },
        )
    }
}
