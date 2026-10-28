package com.trackermaster.app.tasks

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.TaskRepository
import com.trackermaster.core.domain.model.SubTask
import com.trackermaster.core.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: TaskRepository
) : ViewModel() {
    val tasks = repo.observeTasks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.insertTask(Task(title = title))
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            repo.toggleTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repo.deleteTask(task)
        }
    }

    fun planTask(task: Task, day: String?) {
        if (day != null && runCatching { java.time.LocalDate.parse(day) }.isFailure) return
        viewModelScope.launch { repo.updateTask(task.copy(plannedDate = day)) }
    }

    fun toggleSubtask(task: Task, index: Int) {
        viewModelScope.launch {
            repo.updateTask(task.copy(subtasks = task.subtasks.mapIndexed { i, subtask ->
                if (i == index) subtask.copy(completed = !subtask.completed) else subtask
            }))
        }
    }

    fun addSubtask(task: Task, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.updateTask(task.copy(subtasks = task.subtasks + SubTask(title.trim())))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    vm: TasksViewModel = hiltViewModel()
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var newTaskText by remember { mutableStateOf("") }

    var search by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    var filtering by remember {mutableStateOf(false)}
    var filter by remember {mutableStateOf("Todas")}
    var order by remember {mutableStateOf("Recientes")}
    val visible=tasks.filter { it.title.contains(query,true) && when(filter) {
        "Pendientes" -> !it.completed;"Completadas" ->it.completed
        "Vencidas" -> !it.completed && it.plannedDate?.let { d ->runCatching {java.time.LocalDate.parse(d)<java.time.LocalDate.now()}.getOrDefault(false)}==true
        else ->true
    }}.let { list ->when(order) {"Título" ->list.sortedBy {it.title.lowercase()};"Vencimiento" ->list.sortedBy {it.plannedDate ?: "9999"};else ->list} }
    Scaffold(
        contentWindowInsets=WindowInsets(0,0,0,0),
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },actions={
                    IconButton({search=!search}) {Icon(Icons.Default.Search,"Buscar tareas")}
                    Box {IconButton({filtering=true}) {Icon(Icons.Default.FilterList,"Filtrar y ordenar")}
                        DropdownMenu(filtering,{filtering=false}) {
                            listOf("Todas","Pendientes","Completadas","Vencidas").forEach {f ->DropdownMenuItem(text={Text(f)},onClick={filter=f;filtering=false})}
                            listOf("Recientes","Título","Vencimiento").forEach {o ->DropdownMenuItem(text={Text("Ordenar: $o")},onClick={order=o;filtering=false})}
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if(search) OutlinedTextField(query,{query=it},label={Text("Buscar tareas")},modifier=Modifier.fillMaxWidth())
            // Task input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    label = { Text("New task...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        vm.addTask(newTaskText)
                        newTaskText = ""
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add task",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Task list
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks yet. Create one!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { vm.toggleTask(task) },
                            onDelete = { vm.deleteTask(task) },
                            onToggleSubtask = { index -> vm.toggleSubtask(task, index) },
                            onAddSubtask = { title -> vm.addSubtask(task, title) },
                            onPlan = { day -> vm.planTask(task, day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onToggleSubtask: (Int) -> Unit,
    onAddSubtask: (String) -> Unit,
    onPlan: (String?) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var subtaskText by remember { mutableStateOf("") }
    var plannedDay by remember(task.plannedDate) { mutableStateOf(task.plannedDate.orEmpty()) }
    val completedSubtasks = task.subtasks.count { it.completed }
    val progress = if (task.subtasks.isEmpty()) 0f else completedSubtasks.toFloat() / task.subtasks.size
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (expanded) {
                OutlinedTextField(value = plannedDay, onValueChange = { plannedDay = it },
                    label = { Text("Fecha objetivo (AAAA-MM-DD)") }, singleLine = true,
                    isError = plannedDay.isNotBlank() && runCatching { java.time.LocalDate.parse(plannedDay) }.isFailure)
                TextButton(onClick = { onPlan(plannedDay.takeIf { it.isNotBlank() }) },
                    enabled = plannedDay.isBlank() || runCatching { java.time.LocalDate.parse(plannedDay) }.isSuccess) { Text("Guardar fecha objetivo") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = task.completed,
                    onCheckedChange = { onToggle() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                        color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (task.completed) "Completed" else pendingLabel(task.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Subtasks"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (task.subtasks.isNotEmpty()) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "$completedSubtasks/${task.subtasks.size} subtasks",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.subtasks.forEachIndexed { index, subtask ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = subtask.completed, onCheckedChange = { onToggleSubtask(index) })
                            Text(
                                text = subtask.title,
                                textDecoration = if (subtask.completed) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = subtaskText,
                            onValueChange = { subtaskText = it },
                            label = { Text("New subtask") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            onAddSubtask(subtaskText)
                            subtaskText = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add subtask")
                        }
                    }
                }
            }
        }
    }
}

private fun pendingLabel(createdAt: LocalDateTime): String {
    if (createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() == 0L) {
        return "Creation date unavailable"
    }
    val days = ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDateTime.now().toLocalDate())
    return when {
        days <= 0 -> "Pending today"
        days == 1L -> "Pending for 1 day"
        else -> "Pending for $days days"
    }
}
