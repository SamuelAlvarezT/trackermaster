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
import java.time.LocalDate
import java.time.LocalDateTime
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.domain.model.Habit
import com.trackermaster.core.domain.model.ProgressType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(private val repo: JournalRepository,private val moodRepo: MoodRepository,habits: HabitRepository) : ViewModel() {
    val all = repo.observeAllEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val moods = moodRepo.observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val habits = habits.observeActiveHabits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val levels = HabitScheduleLogic.defaultMoodLevels()
    val error=MutableStateFlow<String?>(null)
    val busy=MutableStateFlow(false)
    fun versions(id: Long)=repo.versions(id)
    fun save(entry: JournalEntry,onSaved: ()->Unit)=viewModelScope.launch {
        if(busy.value) return@launch
        busy.value=true
        try {repo.save(entry);onSaved()} catch(e: Exception) {error.value=e.message ?: "No se pudo guardar"} finally {busy.value=false}
    }
    fun trash(id: Long)=viewModelScope.launch {runCatching {repo.trash(id)}.onFailure {error.value="No se pudo mover a la papelera"}}
    fun recover(id: Long)=viewModelScope.launch {runCatching {repo.recover(id)}.onFailure {error.value=it.message ?: "No se pudo recuperar"}}
    fun purge(id: Long)=viewModelScope.launch {runCatching {repo.permanentlyDelete(id)}.onFailure {error.value="No se pudo eliminar"}}
    fun restore(id: Long,revision: Long)=viewModelScope.launch {runCatching {repo.restore(id,revision)}.onFailure {error.value=it.message ?: "No se pudo restaurar"}}
    fun logMood(level: Int,note: String)=viewModelScope.launch {moodRepo.logMood(level,note.trim())}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: JournalViewModel = hiltViewModel()) {
    val all by vm.all.collectAsStateWithLifecycle();val habits by vm.habits.collectAsStateWithLifecycle()
    val moods by vm.moods.collectAsStateWithLifecycle();val error by vm.error.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by remember {mutableIntStateOf(0)};var query by remember {mutableStateOf("")};var searching by remember {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)};var trash by remember {mutableStateOf(false)};var lucidOnly by remember {mutableStateOf(false)}
    var moodOnly by remember {mutableStateOf(false)};var moodPanel by remember {mutableStateOf(false)}
    var editor by remember {mutableStateOf<JournalEntry?>(null)};var selectedId by remember {mutableStateOf<Long?>(null)}
    var deleting by remember {mutableStateOf<JournalEntry?>(null)};var history by remember {mutableStateOf<Long?>(null)}
    val kind=if(tab==0) "PERSONAL" else "DREAM"
    val entries=all.filter { (it.deletedAt!=null)==trash && it.kind==kind && (!lucidOnly || it.lucid) && (!moodOnly || it.moodLevel!=null) &&
        listOf(it.title,stripHtml(it.richTextHtml),it.tags,it.emotions,it.contentDate.toString()).any {v ->v.contains(query,true)} }.sortedByDescending {it.contentDate}
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),topBar={TopAppBar(title={Text(if(trash) "Journal · Papelera" else "Journal")},actions={
        IconButton({searching=!searching}) {Icon(Icons.Default.Search,"Buscar entradas")}
        Box {IconButton({menu=true}) {Icon(Icons.Default.FilterList,"Filtros y acciones")};DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text(if(trash) "Ver entradas" else "Papelera")},onClick={trash=!trash;menu=false})
            DropdownMenuItem(text={Text(if(lucidOnly) "Todos los sueños" else "Solo sueños lúcidos")},onClick={lucidOnly=!lucidOnly;moodOnly=false;menu=false})
            DropdownMenuItem(text={Text(if(moodOnly) "Todos los estados de ánimo" else "Con estado de ánimo")},onClick={moodOnly=!moodOnly;lucidOnly=false;menu=false})
            DropdownMenuItem(text={Text("Estado de ánimo e historial")},onClick={moodPanel=true;menu=false})
        }}
    })},floatingActionButton={if(!trash) FloatingActionButton({val now=LocalDateTime.now();editor=JournalEntry(richTextHtml="",createdAt=now,updatedAt=now,kind=kind)}) {Icon(Icons.Default.Add,"Crear entrada")}}) {pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {Tab(tab==0,{tab=0;lucidOnly=false},text={Text("Diario personal")});Tab(tab==1,{tab=1;moodOnly=false},text={Text("Diario de sueños")})}
            if(searching) OutlinedTextField(query,{query=it},label={Text("Texto, etiqueta, emoción o fecha")},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
            if(entries.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Text(if(trash) "La papelera está vacía" else "Sin entradas para estos filtros")}
            else LazyColumn(contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=88.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(entries,key={it.id}) {entry ->JournalEntryCard(entry,onClick={selectedId=entry.id})}
            }
        }
    }
    editor?.let {entry ->JournalEditor(entry,habits,vm.levels,busy,onDismiss={editor=null},onSave={saved ->vm.save(saved) {editor=null;selectedId=null;tab=if(saved.kind=="DREAM") 1 else 0}})}
    all.firstOrNull {it.id==selectedId}?.let {entry ->
        AlertDialog(onDismissRequest={selectedId=null},title={Text(entry.title.ifBlank {"Sin título"})},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text("Fecha del contenido: ${entry.contentDate}\nCreada: ${entry.createdAt}\nEditada: ${entry.updatedAt}",style=MaterialTheme.typography.labelSmall)}
            item {Text(stripHtml(entry.richTextHtml));Text("${entry.tags} ${entry.emotions}");if(entry.lucid) Text("Sueño lúcido")}
            items(entry.attachments) {AttachmentPreview(it)}
            item {TextButton({history=entry.id;selectedId=null}) {Text("Historial de versiones")}}
            if(entry.deletedAt==null) item {TextButton({editor=entry;selectedId=null}) {Text("Editar")}}
            else item {TextButton({vm.recover(entry.id);selectedId=null}) {Text("Recuperar de la papelera")}}
            item {TextButton({deleting=entry;selectedId=null}) {Text(if(entry.deletedAt==null) "Mover a papelera" else "Eliminar definitivamente")}}
        }},confirmButton={TextButton({selectedId=null}) {Text("Cerrar")}})
    }
    deleting?.let {entry ->AlertDialog(onDismissRequest={deleting=null},title={Text(if(entry.deletedAt==null) "¿Mover a la papelera?" else "¿Eliminar definitivamente?")},text={Text(if(entry.deletedAt==null) "Podrás recuperar la entrada, imágenes e historial. Se retirará solo su contribución al hábito." else "Se eliminarán el registro y su historial de esta app. Esta acción no se puede deshacer.")},confirmButton={TextButton({if(entry.deletedAt==null) vm.trash(entry.id) else vm.purge(entry.id);deleting=null}) {Text("Confirmar")}},dismissButton={TextButton({deleting=null}) {Text("Cancelar")}})}
    history?.let {id ->
        val revisions by remember(id) {vm.versions(id)}.collectAsStateWithLifecycle(initialValue=emptyList())
        var preview by remember {mutableStateOf<com.trackermaster.core.data.repository.JournalRevision?>(null)}
        AlertDialog(onDismissRequest={history=null},title={Text("Historial completo")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(revisions.isEmpty()) item {Text("La versión original se conservará al realizar la primera edición.")}
            items(revisions,key={it.id}) {r ->OutlinedCard(onClick={preview=r}) {Column(Modifier.padding(10.dp)) {Text("Revisión #${r.id} · ${java.time.Instant.ofEpochMilli(r.savedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}");Text(r.entry.title);Text(r.entry.contentDate.toString())}}}
        }},confirmButton={TextButton({history=null}) {Text("Cerrar")}})
        preview?.let {r ->AlertDialog(onDismissRequest={preview=null},title={Text(r.entry.title.ifBlank {"Versión anterior"})},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text("${r.entry.contentDate} · ${r.entry.kind}\nÁnimo: ${r.entry.moodLevel ?: "—"} · Emociones: ${r.entry.emotions}\nEtiquetas: ${r.entry.tags} · Lúcido: ${r.entry.lucid}\nHábito: ${r.entry.habitId ?: "Ninguno"} · Aporte: ${r.entry.contribution}")}
            item {Text(stripHtml(r.entry.richTextHtml))};items(r.entry.attachments) {AttachmentPreview(it)}
        }},confirmButton={TextButton({vm.restore(id,r.id);preview=null;history=null}) {Text("Restaurar como nueva revisión")}},dismissButton={TextButton({preview=null}) {Text("Volver")}})}
    }
    if(moodPanel) AlertDialog(onDismissRequest={moodPanel=false},title={Text("Estado de ánimo")},text={MoodJournalPanel(moods,vm.levels,vm::logMood)},confirmButton={TextButton({moodPanel=false}) {Text("Cerrar")}})
    error?.let {AlertDialog(onDismissRequest={vm.error.value=null},title={Text("Aviso")},text={Text(it)},confirmButton={TextButton({vm.error.value=null}) {Text("Cerrar")}})}
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
            Text(entry.contentDate.toString(), style = MaterialTheme.typography.labelSmall)
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
private fun JournalEditor(initial: JournalEntry,habits: List<Habit>,levels: List<MoodLevel>,busy: Boolean,onDismiss: ()->Unit,onSave: (JournalEntry)->Unit) {
    val context=LocalContext.current
    var entry by remember {mutableStateOf(initial)};var body by remember {mutableStateOf(initial.richTextHtml)}
    var date by remember {mutableStateOf(initial.contentDate.toString())};var contribution by remember {mutableStateOf(initial.contribution.toString())}
    var habitsMenu by remember {mutableStateOf(false)};var removeImage by remember {mutableStateOf<Attachment?>(null)}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->uri?.let {entry=entry.copy(attachments=entry.attachments+copyAttachment(context,it,"image/*"))}}
    val audioPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri ->uri?.let {entry=entry.copy(attachments=entry.attachments+copyAttachment(context,it,"audio/*"))}}
    val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri ->uri?.let {entry=entry.copy(attachments=entry.attachments+copyAttachment(context,it,"application/pdf"))}}
    AlertDialog(onDismissRequest={if(!busy) onDismiss()},title={Text(if(initial.id==0L) "Nueva entrada" else "Editar entrada")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(entry.title,{entry=entry.copy(title=it)},label={Text("Título")})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(entry.kind=="PERSONAL",{entry=entry.copy(kind="PERSONAL")},label={Text("Personal")})
            FilterChip(entry.kind=="DREAM",{entry=entry.copy(kind="DREAM")},label={Text("Sueños")})
        }
        OutlinedTextField(date,{date=it},label={Text("Fecha del contenido AAAA-MM-DD")},isError=runCatching {LocalDate.parse(date)}.isFailure)
        OutlinedTextField(body,{body=it},label={Text(if(entry.kind=="DREAM") "Relato del sueño" else "Texto (admite HTML existente)")},minLines=5)
        Row {levels.forEach {level ->FilterChip(entry.moodLevel==level.level,{entry=entry.copy(moodLevel=if(entry.moodLevel==level.level) null else level.level)},label={Text(level.emoji)})}}
        OutlinedTextField(entry.tags,{entry=entry.copy(tags=it)},label={Text("Etiquetas separadas por comas")})
        if(entry.kind=="DREAM") {
            OutlinedTextField(entry.emotions,{entry=entry.copy(emotions=it)},label={Text("Emociones")})
            Row {Checkbox(entry.lucid,{entry=entry.copy(lucid=it)});Text("Sueño lúcido",Modifier.padding(top=12.dp))}
        }
        Box {OutlinedButton({habitsMenu=true}) {Text("Hábito: ${habits.firstOrNull {it.id==entry.habitId}?.name ?: if(entry.habitId==null) "Ninguno" else "Archivado / no disponible"}")}
            DropdownMenu(habitsMenu,{habitsMenu=false}) {
                DropdownMenuItem(text={Text("Sin vínculo")},onClick={entry=entry.copy(habitId=null);habitsMenu=false})
                habits.filter {it.progressType!=ProgressType.CHECKLIST}.forEach {h ->DropdownMenuItem(text={Text(h.name)},onClick={entry=entry.copy(habitId=h.id);habitsMenu=false})}
            }
        }
        if(entry.habitId!=null) {
            val timed=habits.firstOrNull {it.id==entry.habitId}?.progressType in listOf(ProgressType.TIMER,ProgressType.STOPWATCH)
            OutlinedTextField(contribution,{contribution=it},label={Text(if(timed) "Aporte en minutos" else "Aporte en unidades")})
            Text("Se aplica a la fecha del contenido solo si el hábito estaba programado. Las listas de verificación se marcan desde Habits.",style=MaterialTheme.typography.bodySmall)
        }
        Row {TextButton({imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Imagen")};TextButton({audioPicker.launch("audio/*")}) {Text("Audio")};TextButton({pdfPicker.launch("application/pdf")}) {Text("PDF")}}
        entry.attachments.forEach {attachment ->AttachmentPreview(attachment);TextButton({removeImage=attachment}) {Text("Quitar adjunto")}}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={TextButton({onSave(entry.copy(richTextHtml=body,contentDate=LocalDate.parse(date),contribution=contribution.toInt()))},enabled=!busy && runCatching {LocalDate.parse(date)<=LocalDate.now()}.getOrDefault(false) && contribution.toIntOrNull() in 1..1440 && (body.isNotBlank() || entry.attachments.isNotEmpty())) {Text("Guardar")}},dismissButton={TextButton(onDismiss,enabled=!busy) {Text("Cancelar")}})
    removeImage?.let {attachment ->AlertDialog(onDismissRequest={removeImage=null},title={Text("¿Quitar el adjunto de esta versión?")},text={Text("Seguirá disponible en las versiones anteriores guardadas.")},confirmButton={TextButton({entry=entry.copy(attachments=entry.attachments.filterNot {it.path==attachment.path});removeImage=null}) {Text("Quitar")}},dismissButton={TextButton({removeImage=null}) {Text("Cancelar")}})}
}

private fun stripHtml(html: String): String {
    return html
        .replace("<br>", "\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()
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
    val target = File(dir, "journal_${java.util.UUID.randomUUID()}$ext")
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
