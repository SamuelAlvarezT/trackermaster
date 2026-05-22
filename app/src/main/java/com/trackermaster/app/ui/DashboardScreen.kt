package com.trackermaster.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.app.R
import com.trackermaster.app.navigation.Routes
import com.trackermaster.core.data.repository.DashboardRepository
import com.trackermaster.core.domain.model.DashboardSummary
import com.trackermaster.core.domain.model.TrackerType
import com.trackermaster.core.ui.components.TrackerCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(repo: DashboardRepository) : ViewModel() {
    val summary = repo.observeSummary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary(0, 0, null, 0.0, 0, 0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
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
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge) }
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
                    onClick = { onNavigate(Routes.Mood.route) },
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
