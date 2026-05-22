package com.trackermaster.feature.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.JournalRepository
import com.trackermaster.core.domain.model.JournalEntry
import com.trackermaster.core.ui.components.EmptyState
import com.trackermaster.core.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(private val repo: JournalRepository) : ViewModel() {
    val entries = repo.observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streak = repo.writingStreak().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun save(title: String, body: String) = viewModelScope.launch {
        val now = LocalDateTime.now()
        repo.save(JournalEntry(title = title, richTextHtml = body, createdAt = now, updatedAt = now))
    }
}

@Composable
fun JournalScreen(vm: JournalViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(floatingActionButton = { FloatingActionButton({ showEditor = true }) { Icon(Icons.Default.Add, null) } }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Writing streak", "$streak days")
            }
            if (entries.isEmpty()) EmptyState("Start your journal")
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(e.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                            Text(stripHtml(e.richTextHtml).take(120), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
    if (showEditor) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("New entry") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") })
                    OutlinedTextField(body, { body = it }, label = { Text("Content") }, minLines = 4)
                    Text("Supports **bold**, *italic* in export", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton({
                    vm.save(title, "<p>${body.replace("\n", "<br>")}</p>")
                    showEditor = false
                }) { Text("Save") }
            },
        )
    }
}

private fun stripHtml(html: String) = html.replace(Regex("<[^>]+>"), "")
