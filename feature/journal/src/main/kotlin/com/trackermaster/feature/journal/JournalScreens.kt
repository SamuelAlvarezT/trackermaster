package com.trackermaster.feature.journal

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.JournalRepository
import com.trackermaster.core.data.repository.MoodRepository
import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.model.Attachment
import com.trackermaster.core.domain.model.JournalEntry
import com.trackermaster.core.domain.model.MoodEntry
import com.trackermaster.core.domain.model.MoodLevel
import com.trackermaster.core.ui.components.EmptyState
import com.trackermaster.core.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repo: JournalRepository,
    private val moodRepo: MoodRepository,
) : ViewModel() {
    val entries = repo.observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val moods = moodRepo.observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val levels = HabitScheduleLogic.defaultMoodLevels()
    val streak = repo.writingStreak().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun save(title: String, body: String, moodLevel: Int?, attachments: List<Attachment>) = viewModelScope.launch {
        val now = LocalDateTime.now()
        repo.save(
            JournalEntry(
                title = title,
                richTextHtml = body,
                createdAt = now,
                updatedAt = now,
                moodLevel = moodLevel,
                attachments = attachments,
            )
        )
    }

    fun logMood(level: Int, note: String) = viewModelScope.launch {
        moodRepo.logMood(level = level, note = note.trim())
    }
}

@Composable
fun JournalScreen(vm: JournalViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val moods by vm.moods.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(floatingActionButton = { FloatingActionButton({ showEditor = true }) { Icon(Icons.Default.Add, null) } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Journal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Daily reflection, mood history, and writing pattern in one place.", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Writing streak", "$streak days")
                StatChip("Entries", "${entries.size}")
                StatChip("Mood logs", "${moods.size}")
            }
            TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 12.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Overview") }, icon = { Icon(Icons.Default.EditNote, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Entries") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Mood") }, icon = { Icon(Icons.Default.Mood, null) })
            }
            when (tab) {
                0 -> JournalOverview(entries, moods, vm.levels)
                1 -> JournalEntries(entries)
                else -> MoodJournalPanel(moods, vm.levels, onLogMood = vm::logMood)
            }
        }
    }
    if (showEditor) {
        JournalEditor(
            levels = vm.levels,
            onDismiss = { showEditor = false },
            onSave = { title, body, moodLevel, attachments ->
                vm.save(title, "<p>${body.replace("\n", "<br>")}</p>", moodLevel, attachments)
                showEditor = false
            },
        )
    }
}

@Composable
private fun JournalOverview(entries: List<JournalEntry>, moods: List<MoodEntry>, levels: List<MoodLevel>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Capture entry, log mood, review recent context before day ends.")
                }
            }
        }
        item { Text("Recent entries", style = MaterialTheme.typography.titleMedium) }
        if (entries.isEmpty()) {
            item { EmptyState("Start your journal") }
        } else {
            items(entries.take(3)) { entry -> JournalEntryCard(entry) }
        }
        item { Text("Recent moods", style = MaterialTheme.typography.titleMedium) }
        if (moods.isEmpty()) {
            item { Text("No mood logs yet", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(moods.take(5)) { mood -> MoodRow(mood, levels) }
        }
    }
}

@Composable
private fun JournalEntries(entries: List<JournalEntry>) {
    var selected by remember { mutableStateOf<JournalEntry?>(null) }
    if (entries.isEmpty()) {
        EmptyState("Start your journal")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry -> JournalEntryCard(entry, onClick = { selected = entry }) }
        }
    }
    selected?.let { entry ->
        JournalEntryDetailDialog(entry = entry, onDismiss = { selected = null })
    }
}

@Composable
private fun MoodJournalPanel(
    moods: List<MoodEntry>,
    levels: List<MoodLevel>,
    onLogMood: (Int, String) -> Unit,
) {
    var selectedLevel by remember { mutableIntStateOf(levels.firstOrNull()?.level ?: 3) }
    var note by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Mood check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        levels.forEach { level ->
                            FilterChip(
                                selected = selectedLevel == level.level,
                                onClick = { selectedLevel = level.level },
                                label = { Text("${level.emoji} ${level.level}") },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Mood note") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            onLogMood(selectedLevel, note)
                            note = ""
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Log mood") }
                }
            }
        }
        item { Text("Mood history", style = MaterialTheme.typography.titleMedium) }
        if (moods.isEmpty()) {
            item { EmptyState("No mood logs yet") }
        } else {
            items(moods) { mood -> MoodRow(mood, levels) }
        }
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntry, onClick: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
            Text(entry.updatedAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")), style = MaterialTheme.typography.labelSmall)
            entry.moodLevel?.let { Text("Mood $it/5", style = MaterialTheme.typography.labelSmall) }
            Text(stripHtml(entry.richTextHtml).take(180), style = MaterialTheme.typography.bodySmall, maxLines = 4)
            if (entry.attachments.isNotEmpty()) {
                AssistChip(
                    onClick = onClick,
                    label = { Text("${entry.attachments.size} attachments") },
                    leadingIcon = { Icon(Icons.Default.AttachFile, null) },
                )
            }
        }
    }
}

@Composable
private fun MoodRow(entry: MoodEntry, levels: List<MoodLevel>) {
    val level = levels.find { it.level == entry.level }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(level?.emoji ?: ":|", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(level?.label ?: "Level ${entry.level}", fontWeight = FontWeight.SemiBold)
                Text(entry.timestamp.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")), style = MaterialTheme.typography.labelSmall)
                if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun JournalEditor(
    levels: List<MoodLevel>,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?, List<Attachment>) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var moodLevel by remember { mutableStateOf<Int?>(null) }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { attachments = attachments + copyAttachment(context, it, "image/*") }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { attachments = attachments + copyAttachment(context, it, "audio/*") }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { attachments = attachments + copyAttachment(context, it, "application/pdf") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New journal entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Reflection") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.forEach { level ->
                        FilterChip(
                            selected = moodLevel == level.level,
                            onClick = { moodLevel = if (moodLevel == level.level) null else level.level },
                            label = { Text(level.emoji) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        label = { Text("Image") },
                        leadingIcon = { Icon(Icons.Default.Image, null) },
                    )
                    AssistChip(onClick = { audioPicker.launch("audio/*") }, label = { Text("Audio") }, leadingIcon = { Icon(Icons.Default.AudioFile, null) })
                    AssistChip(onClick = { pdfPicker.launch("application/pdf") }, label = { Text("PDF") }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) })
                }
                if (attachments.isNotEmpty()) Text("${attachments.size} files attached", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton({ onSave(title, body, moodLevel, attachments) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onDismiss) { Text("Cancel") }
        },
    )
}

private fun stripHtml(html: String): String {
    return html
        .replace("<br>", "\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()
}

@Composable
private fun JournalEntryDetailDialog(entry: JournalEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title.ifBlank { "Untitled" }) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(entry.updatedAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")), style = MaterialTheme.typography.labelSmall)
                }
                entry.moodLevel?.let { level ->
                    item { Text("Mood $level/5", style = MaterialTheme.typography.labelLarge) }
                }
                item { Text(stripHtml(entry.richTextHtml), style = MaterialTheme.typography.bodyMedium) }
                items(entry.attachments) { attachment -> AttachmentPreview(attachment) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AttachmentPreview(attachment: Attachment) {
    val context = LocalContext.current
    when {
        attachment.mimeType.startsWith("image") -> AsyncImage(
            model = File(attachment.path),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        )
        attachment.mimeType.startsWith("audio") -> AudioAttachment(attachment)
        attachment.mimeType == "application/pdf" -> AssistChip(
            onClick = { openAttachment(context, attachment) },
            label = { Text(File(attachment.path).name) },
            leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
        )
        else -> AssistChip(
            onClick = { openAttachment(context, attachment) },
            label = { Text(File(attachment.path).name) },
            leadingIcon = { Icon(Icons.Default.AttachFile, null) },
        )
    }
}

@Composable
private fun AudioAttachment(attachment: Attachment) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }
    AssistChip(
        onClick = {
            if (playing) {
                player?.pause()
                playing = false
            } else {
                val mp = player ?: MediaPlayer().apply {
                    setDataSource(attachment.path)
                    prepare()
                }.also { player = it }
                mp.start()
                playing = true
            }
        },
        label = { Text(File(attachment.path).name) },
        leadingIcon = { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) },
    )
}

private fun copyAttachment(context: Context, uri: Uri, fallbackMime: String): Attachment {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: fallbackMime
    val ext = when {
        mime.startsWith("image") -> ".jpg"
        mime.startsWith("audio") -> ".audio"
        mime == "application/pdf" -> ".pdf"
        else -> ".bin"
    }
    val dir = File(context.filesDir, "journal_attachments").apply { mkdirs() }
    val target = File(dir, "journal_${System.currentTimeMillis()}$ext")
    resolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return Attachment(path = target.absolutePath, mimeType = mime)
}

private fun openAttachment(context: Context, attachment: Attachment) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.fromFile(File(attachment.path)), attachment.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
