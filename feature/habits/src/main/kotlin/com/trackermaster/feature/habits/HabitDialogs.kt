package com.trackermaster.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.HabitLog
import com.trackermaster.core.domain.model.ProgressType
import com.trackermaster.core.domain.model.ScheduleType
import com.trackermaster.feature.habits.R
import java.time.LocalDate

class DragDropState(
    val lazyListState: LazyListState,
    private val onSwap: (Int, Int) -> Unit,
    private val onDragEnd: () -> Unit
) {
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffset by mutableStateOf(0f)

    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offset.y.toInt() in item.offset..(item.offset + item.size)
            }?.let { item ->
                draggedIndex = item.index
                dragOffset = 0f
            }
    }

    fun onDrag(change: Offset) {
        val index = draggedIndex ?: return
        dragOffset += change.y

        val draggedItemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val currentOffset = draggedItemInfo.offset + dragOffset

        val targetItem = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                item.index != index && currentOffset.toInt() in item.offset..(item.offset + item.size)
            }

        if (targetItem != null) {
            onSwap(index, targetItem.index)
            draggedIndex = targetItem.index
            dragOffset = 0f
        }
    }

    fun onDragEnd() {
        if (draggedIndex != null) {
            draggedIndex = null
            dragOffset = 0f
            onDragEnd()
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onSwap: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
): DragDropState {
    return remember(lazyListState, onSwap, onDragEnd) {
        DragDropState(lazyListState, onSwap, onDragEnd)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTimePickerDialog(
    initialHour: Int?,
    initialMinute: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onClear: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour ?: 12,
        initialMinute = initialMinute ?: 0,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_reminder_title)) },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.habit_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.habit_delete_btn), color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.habit_cancel))
                }
            }
        }
    )
}

@Composable
fun AddDescriptionDialog(
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_description_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.habit_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.habit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.habit_cancel))
            }
        }
    )
}

@Composable
fun CalendarMonthGrid(
    yearMonth: java.time.YearMonth,
    habit: Habit,
    logs: List<HabitLog>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfMonth = yearMonth.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
    val offset = firstDayOfWeek - 1

    val totalSlots = offset + daysInMonth
    val rows = (totalSlots + 6) / 7

    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            text = yearMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() } + " " + yearMonth.year,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val weekdayLabels = listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do")
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (c in 0..6) {
                    val slotIndex = r * 7 + c
                    val dayNumber = slotIndex - offset + 1
                    
                    if (dayNumber in 1..daysInMonth) {
                        val date = yearMonth.atDay(dayNumber)
                        val isToday = date == today
                        val log = logs.find { it.date == date }
                        val isDue = com.trackermaster.core.domain.HabitScheduleLogic.isDueOnDate(habit, date)

                        val (backgroundColor, textColor) = when {
                            !isDue -> Color.LightGray.copy(alpha = 0.3f) to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            log?.completed == true -> Color.Green to Color.Black
                            log != null && log.value < 0 -> Color.Red to Color.White
                            isToday -> Color.Yellow to Color.Black
                            else -> Color.White to Color.Black
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .background(backgroundColor, CircleShape)
                                .then(if (isDue) Modifier.clickable { onDayClick(date) } else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNumber.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenCalendarDialog(
    habit: Habit,
    logs: List<HabitLog>,
    onDismiss: () -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    val currentMonth = remember { java.time.YearMonth.now() }
    val months = remember(currentMonth) {
        listOf(currentMonth.minusMonths(2), currentMonth.minusMonths(1), currentMonth)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.habit_calendar_title, habit.name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(months) { month ->
                        CalendarMonthGrid(yearMonth = month, habit = habit, logs = logs, onDayClick = onDayClick)
                    }
                }
            }
        }
    }
}

@Composable
fun HabitEditorDialog(
    onDismiss: () -> Unit,
    initialHabit: Habit? = null,
    onSave: (Habit) -> Unit
) {
    var name by remember { mutableStateOf(initialHabit?.name ?: "") }
    var progressType by remember { mutableStateOf(initialHabit?.progressType ?: ProgressType.CHECKBOX) }
    var scheduleType by remember { mutableStateOf(initialHabit?.scheduleType ?: ScheduleType.DAILY) }
    var targetCountStr by remember { mutableStateOf(initialHabit?.targetCount?.toString() ?: "1") }
    var targetDurationMinutesStr by remember { mutableStateOf((initialHabit?.targetDurationMs?.div(60000L))?.toString() ?: "10") }
    var scheduleDaysBitmask by remember { mutableStateOf(initialHabit?.scheduleDaysBitmask ?: 1) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialHabit == null) R.string.habit_new_title else R.string.habit_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.habit_name_label)) }, singleLine = true)
                Text(stringResource(R.string.habit_progress_label, progressType.name))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProgressType.entries.take(3).forEach { t ->
                        FilterChip(progressType == t, { progressType = t }, { Text(t.name.take(4)) })
                    }
                }
                if (progressType == ProgressType.COUNTER) {
                    OutlinedTextField(targetCountStr, { targetCountStr = it }, label = { Text("Target Count") }, singleLine = true)
                } else if (progressType == ProgressType.TIMER || progressType == ProgressType.STOPWATCH) {
                    OutlinedTextField(targetDurationMinutesStr, { targetDurationMinutesStr = it }, label = { Text("Target Minutes") }, singleLine = true)
                }

                Text(stringResource(R.string.habit_schedule_label, scheduleType.name))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScheduleType.entries.take(4).forEach { s ->
                        FilterChip(scheduleType == s, { scheduleType = s }, { Text(s.name.take(5)) })
                    }
                }
                if (scheduleType == ScheduleType.WEEKLY || scheduleType == ScheduleType.SPECIFIC_DAYS) {
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        days.forEachIndexed { index, day ->
                            val bit = 1 shl index
                            val isSelected = (scheduleDaysBitmask and bit) != 0
                            FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    scheduleDaysBitmask = if (isSelected) {
                                        scheduleDaysBitmask and bit.inv()
                                    } else {
                                        scheduleDaysBitmask or bit
                                    }
                                },
                                label = { Text(day) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val count = targetCountStr.toIntOrNull() ?: 1
                    val mins = targetDurationMinutesStr.toLongOrNull() ?: 10L
                    onSave(
                        initialHabit?.copy(
                            name = name,
                            progressType = progressType,
                            scheduleType = scheduleType,
                            scheduleDaysBitmask = scheduleDaysBitmask,
                            targetCount = count,
                            targetDurationMs = mins * 60000L
                        ) ?: Habit(
                            name = name,
                            progressType = progressType,
                            scheduleType = scheduleType,
                            scheduleDaysBitmask = scheduleDaysBitmask,
                            targetCount = count,
                            targetDurationMs = mins * 60000L,
                            colorArgb = 0xFF6750A4.toInt() // Can be updated to use theme dynamically later
                        )
                    )
                }
            }) { Text(stringResource(R.string.habit_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.habit_cancel)) } },
    )
}
