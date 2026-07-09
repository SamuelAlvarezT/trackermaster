package com.trackermaster.app.ui



import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.app.R
import com.trackermaster.app.navigation.Routes
import com.trackermaster.core.data.repository.DashboardRepository
import com.trackermaster.core.data.repository.TaskRepository
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.model.DashboardSummary
import com.trackermaster.core.ui.components.TrackerCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repo: DashboardRepository,
    taskRepo: TaskRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val summary = repo.observeSummary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary(0, 0, null, 0.0, 0, 0))
    val pendingTasksCount = taskRepo.observeTasks().map { list -> list.count { !it.completed } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val pendingTasks by vm.pendingTasksCount.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton({ onNavigate(Routes.Insights.route) }) {
                        Icon(Icons.Default.Analytics, contentDescription = null)
                    }
                    IconButton({ onNavigate(Routes.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        if (settings.newInterfaceEnabled) {
            ModernDashboardContent(
                summary = summary,
                pendingTasks = pendingTasks,
                onNavigate = onNavigate,
                modifier = Modifier.padding(pad),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge) }
            item {
                TrackerCard(
                    title = stringResource(R.string.tracker_tasks),
                    subtitle = "$pendingTasks pending tasks",
                    onClick = { onNavigate(Routes.Tasks.route) },
                )
            }
            item {
                TrackerCard(
                    title = stringResource(R.string.tracker_habits),
                    subtitle = "${summary.habitsCompletedToday}/${summary.habitsDueToday} today",
                    onClick = { onNavigate(Routes.Habits.route) },
                )
            }
            item {
                val moodSubtitle = summary.latestMoodLevel?.let { level ->
                    stringResource(R.string.mood_level, level)
                } ?: stringResource(R.string.mood_log_prompt)
                TrackerCard(
                    title = stringResource(R.string.tracker_mood),
                    subtitle = moodSubtitle,
                    onClick = { onNavigate(Routes.Journal.route) },
                )
            }
            item {
                TrackerCard(
                    title = stringResource(R.string.tracker_expense),
                    subtitle = "$${"%.2f".format(summary.totalBalance)}",
                    onClick = { onNavigate(Routes.Expense.route) },
                )
            }
            item {
                TrackerCard(
                    title = stringResource(R.string.tracker_focus),
                    subtitle = stringResource(R.string.focus_score, summary.focusScoreWeek),
                    onClick = { onNavigate(Routes.Focus.route) },
                )
            }
            item {
                TrackerCard(
                    title = stringResource(R.string.tracker_journal),
                    subtitle = stringResource(R.string.journal_streak, summary.journalStreak),
                    onClick = { onNavigate(Routes.Journal.route) },
                )
            }
        }
    }
}

@Composable
private fun ModernDashboardContent(
    summary: DashboardSummary,
    pendingTasks: Int,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val habitProgress = if (summary.habitsDueToday == 0) {
        0f
    } else {
        summary.habitsCompletedToday.toFloat() / summary.habitsDueToday.toFloat()
    }.coerceIn(0f, 1f)
    val moodText = summary.latestMoodLevel?.let { stringResource(R.string.mood_level, it) }
        ?: stringResource(R.string.mood_log_prompt)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { habitProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricText("Habits", "${summary.habitsCompletedToday}/${summary.habitsDueToday}")
                        MetricText("Tasks", pendingTasks.toString())
                        MetricText("Focus", summary.focusScoreWeek.toString())
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactStatCard("Balance", "$${"%.2f".format(summary.totalBalance)}", Modifier.weight(1f))
                CompactStatCard("Journal", stringResource(R.string.journal_streak, summary.journalStreak), Modifier.weight(1f))
            }
        }
        item { Text("Today", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModernNavCard(Icons.Default.TaskAlt, stringResource(R.string.tracker_tasks), "$pendingTasks pending tasks", MaterialTheme.colorScheme.primary) {
                    onNavigate(Routes.Tasks.route)
                }
                ModernNavCard(Icons.Default.CheckCircle, stringResource(R.string.tracker_habits), "${summary.habitsCompletedToday}/${summary.habitsDueToday} today", Color(0xFF2E7D32)) {
                    onNavigate(Routes.Habits.route)
                }
                ModernNavCard(Icons.Default.Mood, stringResource(R.string.tracker_mood), moodText, Color(0xFFAD1457)) {
                    onNavigate(Routes.Journal.route)
                }
                ModernNavCard(Icons.Default.Payments, stringResource(R.string.tracker_expense), "$${"%.2f".format(summary.totalBalance)}", Color(0xFF00695C)) {
                    onNavigate(Routes.Expense.route)
                }
                ModernNavCard(Icons.Default.Psychology, stringResource(R.string.tracker_focus), stringResource(R.string.focus_score, summary.focusScoreWeek), Color(0xFF5E35B1)) {
                    onNavigate(Routes.Focus.route)
                }
                ModernNavCard(Icons.Default.EditNote, stringResource(R.string.tracker_journal), stringResource(R.string.journal_streak, summary.journalStreak), Color(0xFFEF6C00)) {
                    onNavigate(Routes.Journal.route)
                }
            }
        }
    }
}

@Composable
private fun MetricText(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CompactStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(88.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ModernNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f), contentColor = accent) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
