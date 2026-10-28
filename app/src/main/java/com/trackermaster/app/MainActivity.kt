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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import kotlinx.coroutines.flow.map
import androidx.lifecycle.Lifecycle
import com.trackermaster.core.data.unlock.*
import com.trackermaster.core.data.progress.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var requestedTab by mutableStateOf<String?>(null)
    private var navigationRequest by mutableIntStateOf(0)
    private var autoToken by mutableStateOf<String?>(null)
    private var quickEntry by mutableStateOf<String?>(null)
    private var quickToken by mutableStateOf("")
    private var activityResumed by mutableStateOf(false)
    private var windowFocused by mutableStateOf(false)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var unlockStore: UnlockLaunchStore
    @Inject lateinit var unlockController: UnlockMonitorController
    @Inject lateinit var focusProgress: FocusProgressRepository
    @Inject lateinit var focusController: FocusTimerController
    @Inject lateinit var backupManager: BackupManager
    @Inject lateinit var lifeReviews: com.trackermaster.core.data.insights.LifeReviews
    @Inject lateinit var cloudSync: com.trackermaster.core.data.sync.CloudSyncManager

    override fun onStart() {
        super.onStart()
        cloudSync.foreground(true)
        lifecycleScope.launch { runCatching { lifeReviews.catchUp() } }
        lifecycleScope.launch {
            if(unlockStore.settings.first().enabled && !UnlockMonitorSignals.running.value) unlockController.start()
            val focus=focusProgress.checkpoint()
            if(focus.status==com.trackermaster.core.domain.focus.FocusRunStatus.RUNNING && !FocusTimerSignals.running.value)
                runCatching { focusController.send(FocusTimerController.RECOVER) }
        }
    }

    override fun onStop() {
        cloudSync.foreground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        requestedTab = intent?.getStringExtra("tab")
        autoToken = intent?.getStringExtra(UnlockLaunchStore.EXTRA_TOKEN)
        quickEntry=intent?.getStringExtra("money_quick_entry")
        quickToken=savedInstanceState?.getString("money_widget_token") ?: java.util.UUID.randomUUID().toString()

        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.defumblrEnabled && !unlockStore.settings.first().enabled) {
                startDefumblrLockScreenService()
            }
            if (shouldRunBackup(settings.backupFrequency, settings.lastBackupAtMillis)) {
                runCatching {
                    backupManager.exportLocalZip()
                    settingsRepository.setLastBackupAtMillis(System.currentTimeMillis())
                }
            }
        }

        setContent {
            val settings by remember { settingsRepository.settings.map<com.trackermaster.core.domain.model.AppSettings,com.trackermaster.core.domain.model.AppSettings?> { it } }
                .collectAsStateWithLifecycle(initialValue=null)
            val loaded=settings
            if(loaded==null) {
                TrackermasterTheme(com.trackermaster.core.domain.model.ThemeMode.DARK,0) {
                    Surface(Modifier.fillMaxSize()) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
                }
            } else {
                var unlocked by remember(loaded.biometricEnabled,navigationRequest) { mutableStateOf(!loaded.biometricEnabled) }
                val rootState=rememberSaveableStateHolder()
                TrackermasterTheme(loaded.themeMode,loaded.accentIndex) {
                    com.trackermaster.core.ui.LocalizedAppContent(loaded.localeTag) {
                    if(!unlocked && loaded.biometricEnabled) BiometricGate(activity=this,onUnlocked={unlocked=true})
                    else if(!loaded.onboardingComplete) OnboardingScreen(onComplete={
                        lifecycleScope.launch { settingsRepository.setOnboardingComplete() }
                    })
                    else rootState.SaveableStateProvider("authenticated-root") { TrackermasterRoot(startTab=requestedTab ?: loaded.initialSection,navigationRequest=navigationRequest,
                        quickEntry=quickEntry,quickToken=quickToken,onQuickConsumed={quickEntry=null;intent?.removeExtra("money_quick_entry")},
                        canAcknowledge=activityResumed && windowFocused,onDestinationReady={ route -> acknowledgeAutomaticOpening(route) })
                    }
                    }
                }
            }
        }
    }

    private fun acknowledgeAutomaticOpening(route: String) {
        val token=autoToken ?: return
        if(!activityResumed || !windowFocused || getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
        lifecycleScope.launch {
            if(unlockStore.acknowledge(token,route)) {
                unlockController.clearFallback()
                if(autoToken==token) autoToken=null
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
        requestedTab = intent.getStringExtra("tab")
        autoToken = intent.getStringExtra(UnlockLaunchStore.EXTRA_TOKEN)
        quickEntry=intent.getStringExtra("money_quick_entry")
        quickToken=java.util.UUID.randomUUID().toString()
        navigationRequest++
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        activityResumed=true
        autoToken?.let { token -> lifecycleScope.launch {
            if(!getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) unlockStore.visible(token)
        } }
        lifecycleScope.launch { settingsRepository.setAppWasOpenBeforeScreenOff(true) }
    }

    override fun onPause() { activityResumed=false;super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("money_widget_token",quickToken)
        super.onSaveInstanceState(outState)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus);windowFocused=hasFocus }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        lifecycleScope.launch { settingsRepository.setAppWasOpenBeforeScreenOff(false) }
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
fun TrackermasterRoot(startTab: String? = null, navigationRequest: Int = 0, canAcknowledge: Boolean = false,
    quickEntry: String? = null,quickToken: String="",onQuickConsumed: ()->Unit={},onDestinationReady: (String)->Unit = {}) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route?.substringBefore('?')
    val sharedContent by ShareIntentHandler.sharedContent.collectAsStateWithLifecycle()

    LaunchedEffect(startTab, navigationRequest) {
        when (startTab) {
            "home" -> navController.navigate(Routes.Home.route) { launchSingleTop=true }
            "habits" -> navController.navigate(Routes.Habits.route) { launchSingleTop=true }
            "tasks" -> navController.navigate(Routes.Tasks.route) { launchSingleTop=true }
            "mood" -> navController.navigate(Routes.Journal.route) { launchSingleTop=true }
            "expense" -> navController.navigate(Routes.Expense.route) { launchSingleTop=true }
            "focus" -> navController.navigate(Routes.Focus.route) { launchSingleTop=true }
            "journal" -> navController.navigate(Routes.Journal.route) { launchSingleTop=true }
            "insights" -> navController.navigate(Routes.Insights.route) { launchSingleTop=true }
        }
    }

    LaunchedEffect(currentRoute,navigationRequest,canAcknowledge,startTab) {
        if(canAcknowledge && currentRoute==startTab?.substringBefore('?')) currentRoute?.let(onDestinationReady)
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
                            label = { Text(route.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.Home.route) { DashboardScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.Habits.route+"?habitId={habitId}",arguments=listOf(androidx.navigation.navArgument("habitId") {
                type=androidx.navigation.NavType.StringType;nullable=true;defaultValue=null
            })) { entry -> HabitsScreen(openHabitId=entry.arguments?.getString("habitId")?.toLongOrNull()) }
            composable(Routes.Tasks.route) { TasksScreen() }
            composable(Routes.Mood.route) { JournalScreen() }
            composable(Routes.Expense.route) { ExpenseScreen(quickEntry=quickEntry,quickToken=quickToken,onQuickConsumed=onQuickConsumed) }
            composable(Routes.Focus.route) { FocusScreen(onStatistics={navController.navigate("focus_insights")}) }
            composable(Routes.Journal.route) { JournalScreen() }
            composable(Routes.Insights.route) { InsightsScreen(onBack={navController.popBackStack()}) }
            composable("goals") { InsightsScreen(initialTab=1,onBack={navController.popBackStack()}) }
            composable("focus_insights") { InsightsScreen(initialFilter="Focus",onBack={navController.popBackStack()}) }
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
