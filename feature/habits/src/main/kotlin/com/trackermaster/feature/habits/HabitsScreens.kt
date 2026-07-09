package com.trackermaster.feature.habits

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.ui.components.EmptyState
import com.trackermaster.core.ui.components.SectionHeader
import java.time.LocalDate

@Composable
@Suppress("UNUSED_VALUE")
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var showEditor by remember { mutableStateOf(false) }
    var editHabitData by remember { mutableStateOf<Habit?>(null) }
    var deleteHabitData by remember { mutableStateOf<Habit?>(null) }
    var reminderHabitData by remember { mutableStateOf<Habit?>(null) }
    var descriptionHabitData by remember { mutableStateOf<Habit?>(null) }
    var calendarHabitData by remember { mutableStateOf<Habit?>(null) }
    var selectedHabitForMenu by remember { mutableStateOf<Habit?>(null) }

    val currentRawList = if (uiState.selectedTab == 0) uiState.activeHabits else uiState.archivedHabits
    
    // We keep local list strictly for drag-drop fluidity but sync it to uiState
    var localList by remember(currentRawList) { mutableStateOf(currentRawList) }
    LaunchedEffect(currentRawList) { localList = currentRawList }

    val lazyListState = rememberLazyListState()
    val today = remember { LocalDate.now() }
    val last7Days = remember(today) { (0..6).map { today.minusDays(it.toLong()) }.reversed() }
    val weekdayHeaders = remember(last7Days) { last7Days.map { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).take(3) } }
    val onSwap = { from: Int, to: Int ->
        if (from in localList.indices && to in localList.indices) {
            val newList = localList.toMutableList()
            val item = newList.removeAt(from)
            newList.add(to, item)
            localList = newList
        }
    }
    
    val onDragEnd = {
        viewModel.updateHabitsOrder(localList.map { it.first })
        Unit
    }

    val dragDropState = rememberDragDropState(lazyListState, onSwap, onDragEnd)
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (uiState.selectedTab == 0 && !uiState.isReorderMode) {
                FloatingActionButton(onClick = { showEditor = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val tabs = listOf(stringResource(R.string.habit_tab_active), stringResource(R.string.habit_tab_archived))
                    tabs.forEachIndexed { index, title ->
                        val isSelected = uiState.selectedTab == index
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val txtColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(bgColor, RoundedCornerShape(20.dp))
                                .clickable { viewModel.selectTab(index) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = txtColor
                            )
                        }
                    }
                }

                IconButton(onClick = { viewModel.toggleReorderMode() }) {
                    Icon(
                        imageVector = if (uiState.isReorderMode) Icons.Default.Check else Icons.Default.Menu,
                        contentDescription = "Reorder",
                        tint = if (uiState.isReorderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (localList.isEmpty() && !showEditor) {
                EmptyState(stringResource(R.string.habit_no_habits), Modifier.weight(1f))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(modifier = Modifier.width(112.dp))
                    weekdayHeaders.forEachIndexed { index, label ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = if (uiState.isReorderMode) {
                        Modifier
                            .weight(1f)
                            .pointerInput(dragDropState) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset -> dragDropState.onDragStart(offset) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDropState.onDrag(dragAmount)
                                    },
                                    onDragEnd = { dragDropState.onDragEnd() },
                                    onDragCancel = { dragDropState.onDragEnd() }
                                )
                            }
                    } else {
                        Modifier.weight(1f)
                    },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(localList, key = { _, pair -> pair.first.id }) { index, (habit, logs) ->
                        val isDragged = index == dragDropState.draggedIndex
                        val translationY = if (isDragged) dragDropState.dragOffset else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    this.translationY = translationY
                                    shadowElevation = if (isDragged) 12.dp.toPx() else 0f
                                    scaleX = if (isDragged) 1.03f else 1f
                                    scaleY = if (isDragged) 1.03f else 1f
                                }
                                .zIndex(if (isDragged) 1f else 0f)
                        ) {
                            HabitRow(
                                habit = habit,
                                logs = logs,
                                isReorderMode = uiState.isReorderMode,
                                onDayClick = { date -> viewModel.toggleDay(habit, date) },
                                onMenuClick = { selectedHabitForMenu = habit }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        HabitEditorDialog(onDismiss = { showEditor = false }) { habit ->
            viewModel.saveHabit(habit)
            showEditor = false
        }
    }

    if (editHabitData != null) {
        HabitEditorDialog(
            onDismiss = { editHabitData = null },
            initialHabit = editHabitData
        ) { updated ->
            viewModel.saveHabit(updated)
            editHabitData = null
        }
    }

    if (deleteHabitData != null) {
        AlertDialog(
            onDismissRequest = { deleteHabitData = null },
            title = { Text(stringResource(R.string.habit_delete_title)) },
            text = { Text(stringResource(R.string.habit_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteHabitData?.let { viewModel.deleteHabit(it) }
                        deleteHabitData = null
                    }
                ) {
                    Text(stringResource(R.string.habit_delete_btn), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteHabitData = null }) {
                    Text(stringResource(R.string.habit_cancel))
                }
            }
        )
    }

    if (reminderHabitData != null) {
        CustomTimePickerDialog(
            initialHour = reminderHabitData?.reminderHour,
            initialMinute = reminderHabitData?.reminderMinute,
            onDismiss = { reminderHabitData = null },
            onConfirm = { hour, minute ->
                reminderHabitData?.let { viewModel.saveHabit(it.copy(reminderHour = hour, reminderMinute = minute)) }
                reminderHabitData = null
            },
            onClear = {
                reminderHabitData?.let { viewModel.saveHabit(it.copy(reminderHour = null, reminderMinute = null)) }
                reminderHabitData = null
            }
        )
    }

    if (descriptionHabitData != null) {
        AddDescriptionDialog(
            initialDescription = descriptionHabitData?.description ?: "",
            onDismiss = { descriptionHabitData = null },
            onConfirm = { desc ->
                descriptionHabitData?.let { viewModel.saveHabit(it.copy(description = desc)) }
                descriptionHabitData = null
            }
        )
    }

    if (calendarHabitData != null) {
        val calendarLogs by viewModel.observeHabitLogs(calendarHabitData!!.id).collectAsStateWithLifecycle(initialValue = emptyList())
        FullScreenCalendarDialog(
            habit = calendarHabitData!!,
            logs = calendarLogs,
            onDismiss = { calendarHabitData = null },
            onDayClick = { date -> calendarHabitData?.let { viewModel.toggleDay(it, date) } }
        )
    }

    selectedHabitForMenu?.let { habit ->
        val streakInfo by viewModel.observeStreak(habit.id).collectAsStateWithLifecycle(initialValue = StreakInfo(0, 0))
        val completionsCount by viewModel.observeCompletionsCount(habit.id).collectAsStateWithLifecycle(initialValue = 0)
        
        val shareText = stringResource(R.string.habit_share_text, habit.name, streakInfo.current, streakInfo.best)

        HabitBottomSheet(
            habit = habit,
            streakInfo = streakInfo,
            completionsCount = completionsCount,
            onDismissRequest = { selectedHabitForMenu = null },
            onReminderClick = {
                reminderHabitData = habit
                selectedHabitForMenu = null
            },
            onDescriptionClick = {
                descriptionHabitData = habit
                selectedHabitForMenu = null
            },
            onCalendarClick = {
                calendarHabitData = habit
                selectedHabitForMenu = null
            },
            onShareClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir Progreso"))
                selectedHabitForMenu = null
            },
            onEditClick = {
                editHabitData = habit
                selectedHabitForMenu = null
            },
            onDeleteClick = {
                deleteHabitData = habit
                selectedHabitForMenu = null
            },
            onArchiveToggleClick = {
                viewModel.toggleArchive(habit)
                selectedHabitForMenu = null
            }
        )
    }
}

@Composable
fun HabitRow(
    habit: Habit,
    logs: List<HabitLog>,
    isReorderMode: Boolean,
    onDayClick: (LocalDate) -> Unit,
    onMenuClick: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val last7Days = remember(today) { (0..6).map { today.minusDays(it.toLong()) }.reversed() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (habit.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = habit.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                if (isReorderMode) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Drag Handle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    IconButton(onClick = onMenuClick) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                }
            }
            
            if (!isReorderMode) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.habit_frequency, habit.scheduleType.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    last7Days.forEach { date ->
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

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (isDue) Modifier.clickable { onDayClick(date) } else Modifier)
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(backgroundColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitBottomSheet(
    habit: Habit,
    streakInfo: StreakInfo?,
    completionsCount: Int,
    onDismissRequest: () -> Unit,
    onReminderClick: () -> Unit,
    onDescriptionClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onShareClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onArchiveToggleClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (habit.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = habit.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${streakInfo?.current ?: 0}d",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.habit_streak_current),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${streakInfo?.best ?: 0}d",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.habit_streak_best),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = completionsCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = stringResource(R.string.habit_completions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    val reminderText = if (habit.reminderHour != null && habit.reminderMinute != null) {
                        stringResource(R.string.habit_reminder_set, String.format(java.util.Locale.getDefault(), "%02d:%02d", habit.reminderHour, habit.reminderMinute))
                    } else stringResource(R.string.habit_reminder_add)
                    BottomSheetActionRow(icon = Icons.Default.Notifications, title = reminderText, onClick = onReminderClick)
                }
                item {
                    val descText = if (habit.description.isNotBlank()) stringResource(R.string.habit_desc_modify) else stringResource(R.string.habit_desc_add)
                    BottomSheetActionRow(icon = Icons.Default.Info, title = descText, onClick = onDescriptionClick)
                }
                item {
                    BottomSheetActionRow(icon = Icons.Default.DateRange, title = stringResource(R.string.habit_calendar_historic), onClick = onCalendarClick)
                }
                item {
                    BottomSheetActionRow(icon = Icons.Default.Share, title = stringResource(R.string.habit_share_progress), onClick = onShareClick)
                }
                item {
                    BottomSheetActionRow(icon = Icons.Default.Edit, title = stringResource(R.string.habit_edit_title), onClick = onEditClick)
                }
                item {
                    val archiveText = if (habit.archived) stringResource(R.string.habit_restore) else stringResource(R.string.habit_archive)
                    BottomSheetActionRow(icon = if (habit.archived) Icons.Default.Refresh else Icons.Default.Archive, title = archiveText, onClick = onArchiveToggleClick)
                }
                item {
                    BottomSheetActionRow(icon = Icons.Default.Delete, title = stringResource(R.string.habit_delete_title), iconColor = MaterialTheme.colorScheme.error, textColor = MaterialTheme.colorScheme.error, onClick = onDeleteClick)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BottomSheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}

@Composable
fun AchievementsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(achievements) { a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                    Text(a.type, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
