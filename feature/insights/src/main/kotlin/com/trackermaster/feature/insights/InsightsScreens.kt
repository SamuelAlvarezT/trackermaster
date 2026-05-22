package com.trackermaster.feature.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.DashboardRepository
import com.trackermaster.core.data.repository.ExpenseRepository
import com.trackermaster.core.data.repository.FocusRepository
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.data.repository.MoodRepository
import com.trackermaster.core.domain.model.DashboardSummary
import com.trackermaster.core.ui.components.SectionHeader
import com.trackermaster.feature.expense.ExpenseReportsScreen
import com.trackermaster.feature.mood.MoodCorrelationScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val dashboard: DashboardRepository,
    private val habits: HabitRepository,
    private val mood: MoodRepository,
    private val expense: ExpenseRepository,
    private val focus: FocusRepository,
) : ViewModel() {
    val summary = dashboard.observeSummary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary(0, 0, null, 0.0, 0, 0))
}

@Composable
fun InsightsScreen(vm: InsightsViewModel = hiltViewModel()) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val tabs = listOf("Overview", "Mood×Habit", "Expenses", "Focus")
    val pager = rememberPagerState { tabs.size }

    Column {
        TabRow(selectedTabIndex = pager.currentPage) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pager.currentPage == i, onClick = { }, text = { Text(t, maxLines = 1) })
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> OverviewTab(summary)
                1 -> MoodCorrelationScreen()
                2 -> ExpenseReportsScreen()
                3 -> FocusInsightTab(summary.focusScoreWeek)
            }
        }
    }
}

@Composable
fun OverviewTab(summary: DashboardSummary) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Life Dashboard Insights")
        InsightCard("Habits today", "${summary.habitsCompletedToday}/${summary.habitsDueToday}")
        InsightCard("Latest mood", summary.latestMoodLevel?.toString() ?: "—")
        InsightCard("Balance", "$${"%.2f".format(summary.totalBalance)}")
        InsightCard("Focus score (week)", "${summary.focusScoreWeek}")
        InsightCard("Journal streak", "${summary.journalStreak} days")
    }
}

@Composable
fun InsightCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun FocusInsightTab(score: Int) {
    Column(Modifier.padding(16.dp)) {
        SectionHeader("Weekly Focus Score")
        Text("$score / 100", style = MaterialTheme.typography.displayMedium)
    }
}
