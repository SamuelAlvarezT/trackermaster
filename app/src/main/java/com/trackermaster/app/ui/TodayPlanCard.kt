package com.trackermaster.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackermaster.app.R
import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.HabitLog
import com.trackermaster.core.domain.model.ProgressType
import com.trackermaster.core.domain.model.Task
import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.habits.formatHabitMinutes
import java.time.LocalDate

data class TodayPlan(val habits: List<Pair<Habit, HabitLog?>> = emptyList(), val tasks: List<Task> = emptyList())

internal fun buildTodayPlan(day: LocalDate, habits: List<Pair<Habit, HabitLog?>>, tasks: List<Task>): TodayPlan =
    TodayPlan(habits.filter { (habit, log) -> !habit.archived && (log == null || !HabitScheduleLogic.isLogComplete(log)) },
        tasks.filter { task -> !task.completed && task.cancelledAtEpoch == null &&
            (task.plannedDate == null || runCatching { LocalDate.parse(task.plannedDate) <= day }.getOrDefault(false)) })

@Composable
fun TodayPlanCard(plan: TodayPlan?, onHabit: (Long) -> Unit, onTask: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.today_plan_title), style = MaterialTheme.typography.titleMedium)
            when {
                plan == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                plan.habits.isEmpty() && plan.tasks.isEmpty() -> Text(stringResource(R.string.today_plan_empty))
                else -> {
                    plan.habits.forEach { (habit, log) ->
                        val type = log?.progressTypeAtLog ?: habit.progressType
                        val target = log?.targetDurationMsAtLog ?: habit.targetDurationMs
                        val remaining = (target - (log?.durationMs ?: 0L).coerceAtLeast(0)).coerceAtLeast(0)
                        Column(Modifier.fillMaxWidth().clickable { onHabit(habit.id) }.padding(vertical = 6.dp)) {
                            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                            Text(if (type in listOf(ProgressType.TIMER, ProgressType.STOPWATCH) && target > 0)
                                stringResource(R.string.today_plan_habit_time, formatHabitMinutes(remaining))
                                else stringResource(R.string.today_plan_habit), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    plan.tasks.forEach { task ->
                        Column(Modifier.fillMaxWidth().clickable { onTask(task.id) }.padding(vertical = 6.dp)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge)
                            Text(task.plannedDate?.let { stringResource(R.string.today_plan_task_due, it) }
                                ?: stringResource(R.string.today_plan_task_undated), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
