package com.trackermaster.app.lockscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.app.MainActivity
import com.trackermaster.core.data.repository.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.model.Task
import com.trackermaster.core.ui.theme.TrackermasterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class DefumblrLockScreenActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var habitRepository: HabitRepository
    @Inject lateinit var expenseRepository: ExpenseRepository
    @Inject lateinit var journalRepository: JournalRepository
    @Inject lateinit var focusRepository: FocusRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show when locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )

            TrackermasterTheme(settings.themeMode, settings.accentIndex) {
                if (settings.defumblrEnabled) {
                    LockScreenContent(
                        settings = settings,
                        taskRepository = taskRepository,
                        habitRepository = habitRepository,
                        expenseRepository = expenseRepository,
                        journalRepository = journalRepository,
                        focusRepository = focusRepository,
                        onDismiss = { finish() },
                        onBubbleClicked = { tab ->
                            launchAppTab(tab)
                        }
                    )
                } else {
                    finish()
                }
            }
        }
    }

    private fun launchAppTab(tab: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("tab", tab)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    startActivity(intent)
                    finish()
                }
                override fun onDismissCancelled() {
                    startActivity(intent)
                    finish()
                }
            })
        } else {
            startActivity(intent)
            finish()
        }
    }
}

@Composable
fun LockScreenContent(
    settings: AppSettings,
    taskRepository: TaskRepository,
    habitRepository: HabitRepository,
    expenseRepository: ExpenseRepository,
    journalRepository: JournalRepository,
    focusRepository: FocusRepository,
    onDismiss: () -> Unit,
    onBubbleClicked: (String) -> Unit
) {
    // Clock states
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            val today = LocalDate.now()
            timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            dateText = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
            kotlinx.coroutines.delay(1000)
        }
    }

    // Drag to unlock state
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1A3C),
                        Color(0xFF0F0B1E)
                    )
                )
            )
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    offsetY += delta
                    if (offsetY < -300f) {
                        onDismiss()
                    }
                },
                onDragStopped = {
                    offsetY = 0f
                }
            )
            .offset(y = (offsetY / 4).dp)
    ) {
        // Blurry decorative shapes
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopStart)
                .offset(x = (-50).dp, y = (-50).dp)
                .background(Color(0xFF6750A4).copy(alpha = 0.25f), CircleShape)
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .background(Color(0xFF03DAC5).copy(alpha = 0.2f), CircleShape)
                .blur(80.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Clock & Date
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Text(
                    text = timeText,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    letterSpacing = (-2).sp
                )
                Text(
                    text = dateText,
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Widgets Area
            if (settings.lockscreenWidgetsEnabled) {
                LockScreenWidgetsContainer(
                    taskRepository = taskRepository,
                    habitRepository = habitRepository,
                    expenseRepository = expenseRepository,
                    journalRepository = journalRepository,
                    focusRepository = focusRepository,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 24.dp)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Defumblr Bubbles & Swipe to Unlock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Bubbles row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BubbleItem("Tasks", Icons.Default.List, Color(0xFFE57373)) { onBubbleClicked("tasks") }
                    BubbleItem("Habits", Icons.Default.CheckCircle, Color(0xFF81C784)) { onBubbleClicked("habits") }
                    BubbleItem("Money", Icons.Default.AccountBalance, Color(0xFF64B5F6)) { onBubbleClicked("expense") }
                    BubbleItem("Journal", Icons.Default.MenuBook, Color(0xFFFFB74D)) { onBubbleClicked("journal") }
                    BubbleItem("Focus", Icons.Default.Timer, Color(0xFF4DB6AC)) { onBubbleClicked("focus") }
                }

                // Swipe indicator
                Text(
                    text = "↑ Swipe up to unlock",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun BubbleItem(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .padding(2.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun LockScreenWidgetsContainer(
    taskRepository: TaskRepository,
    habitRepository: HabitRepository,
    expenseRepository: ExpenseRepository,
    journalRepository: JournalRepository,
    focusRepository: FocusRepository,
    modifier: Modifier = Modifier
) {
    val tasks by taskRepository.observeTasks().collectAsStateWithLifecycle(emptyList())
    val habitsToday by habitRepository.observeTodayHabits(LocalDate.now()).collectAsStateWithLifecycle(emptyList())
    val balance by expenseRepository.totalBalance().collectAsStateWithLifecycle(0.0)
    val journalStreak by journalRepository.writingStreak().collectAsStateWithLifecycle(0)
    val focusSessions by focusRepository.observeSessions().collectAsStateWithLifecycle(emptyList())

    val pendingTasksCount = tasks.count { !it.completed }
    val completedHabitsCount = habitsToday.count { (_, log) -> log != null && HabitScheduleLogic.isLogComplete(log) }
    val totalHabitsCount = habitsToday.size

    val weekAgo = java.time.LocalDateTime.now().minusDays(7)
    val focusScore = HabitScheduleLogic.calculateFocusScore(
        focusSessions.filter { it.completedAt?.isAfter(weekAgo) == true }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GlassWidgetCard(
                title = "Pending Tasks",
                value = "$pendingTasksCount tasks",
                icon = Icons.Default.List,
                color = Color(0xFFE57373)
            )
        }
        item {
            GlassWidgetCard(
                title = "Habits Today",
                value = "$completedHabitsCount / $totalHabitsCount completed",
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF81C784)
            )
        }
        item {
            GlassWidgetCard(
                title = "Total Balance",
                value = "$${"%.2f".format(balance)}",
                icon = Icons.Default.AccountBalance,
                color = Color(0xFF64B5F6)
            )
        }
        item {
            GlassWidgetCard(
                title = "Journal Streak",
                value = "$journalStreak days writing",
                icon = Icons.Default.MenuBook,
                color = Color(0xFFFFB74D)
            )
        }
        item {
            GlassWidgetCard(
                title = "Focus Score",
                value = "$focusScore points this week",
                icon = Icons.Default.Timer,
                color = Color(0xFF4DB6AC)
            )
        }
    }
}

@Composable
fun GlassWidgetCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
