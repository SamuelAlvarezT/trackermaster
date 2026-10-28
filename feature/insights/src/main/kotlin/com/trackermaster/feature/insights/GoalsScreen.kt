package com.trackermaster.feature.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.data.insights.LifeChoices
import com.trackermaster.core.domain.insights.*
import com.trackermaster.core.domain.model.*
import java.time.LocalDate

@Composable
internal fun GoalsPanel(vm: InsightsViewModel) {
    val goals by vm.goals.collectAsStateWithLifecycle();val progressValues by vm.goalProgress.collectAsStateWithLifecycle();val choices by vm.choices.collectAsStateWithLifecycle()
    val hideMoney by vm.hideMoney.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<LifeGoal?>(null) };var deleting by remember { mutableStateOf<LifeGoal?>(null) }
    var archived by remember { mutableStateOf(false) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Button({editor=LifeGoal()}) {Text("Crear objetivo y plan")};FilterChip(archived,{archived=!archived},label={Text("Mostrar archivados")}) }
        if(goals.none { archived || it.status!="ARCHIVED" }) item { Text("Crea un objetivo independiente o vincúlalo a tus registros.") }
        items(goals.filter { archived || it.status!="ARCHIVED" },key={it.id}) { g ->
            val value=progressValues[g.id]
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(g.title,style=MaterialTheme.typography.titleMedium);if(g.description.isNotBlank()) Text(g.description)
                Text("${g.period.label} · ${g.start} — ${g.end}",style=MaterialTheme.typography.labelMedium)
                Text("${g.source.label} · ${g.status}${if(g.status!="COMPLETED" && g.end<LocalDate.now()) " · Vencido" else ""}")
                val progress=value ?: if(g.source==GoalSource.MANUAL) g.manualProgress else null
                if(hideMoney && g.source==GoalSource.SAVINGS) Text("Importes ocultos. Cambia la visibilidad desde Money.")
                else if(progress!=null) { LinearProgressIndicator(progress={GoalRules.ratio(progress,g.target).toFloat()},modifier=Modifier.fillMaxWidth());Text("${"%.2f".format(progress)} / ${g.target} ${g.unit}") }
                else Text("Selecciona en Resultados un periodo que incluya este objetivo para ver su progreso calculado.")
                Text("Criterio: ${g.criterion.ifBlank { "Alcanzar la meta medible" }}")
                g.parentId?.let { parent -> Text("Superior: ${goals.firstOrNull {it.id==parent}?.title ?: "No disponible"}") }
                if(g.source==GoalSource.CHILDREN) Text("Cada hijo aporta igual peso a un promedio de porcentajes (meta 100 %). El estado del padre se cambia manualmente.")
                g.steps.forEachIndexed { index,step -> Row { Checkbox(step.completed,{vm.save(g.copy(steps=g.steps.mapIndexed { i,s -> if(i==index) s.copy(completed=!s.completed) else s })) {}});Text(step.title,Modifier.padding(top=12.dp)) } }
                if(g.notes.isNotBlank()) Text(g.notes)
                Row { TextButton({editor=g}) {Text("Editar")};TextButton({vm.save(g.copy(status=if(g.status=="ARCHIVED") "ACTIVE" else "ARCHIVED")) {}}) {Text(if(g.status=="ARCHIVED") "Activar" else "Archivar")};TextButton({deleting=g}) {Text("Eliminar")} }
            } }
        }
    }
    editor?.let { goal -> GoalEditor(goal,goals,choices,onDismiss={editor=null},onSave={vm.save(it) {editor=null}}) }
    deleting?.let { goal -> AlertDialog(onDismissRequest={deleting=null},title={Text("¿Eliminar este objetivo?")},text={Text("Se eliminará el plan. Los objetivos hijos quedarán independientes y sus registros originales se conservarán.")},confirmButton={TextButton({vm.delete(goal.id);deleting=null}) {Text("Eliminar")}},dismissButton={TextButton({deleting=null}) {Text("Cancelar")}}) }
}

@Composable
private fun GoalEditor(initial: LifeGoal,all: List<LifeGoal>,choices: LifeChoices,onDismiss: ()->Unit,onSave: (LifeGoal)->Unit) {
    var g by remember { mutableStateOf(initial) };var start by remember {mutableStateOf(initial.start.toString())};var end by remember {mutableStateOf(initial.end.toString())}
    var target by remember {mutableStateOf(initial.target.toString())};var progress by remember {mutableStateOf(initial.manualProgress.toString())}
    var newStep by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Objetivo y plan")},text={ LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {OutlinedTextField(g.title,{g=g.copy(title=it)},label={Text("Título")})}
        item {OutlinedTextField(g.description,{g=g.copy(description=it)},label={Text("Descripción")})}
        item {ChoiceMenu("Periodo",g.period.label,LifePeriod.entries.filter {it!=LifePeriod.LAST_SEVEN}.map {it to it.label}) {p ->
            val bounds=LifePeriods.bounds(p,runCatching { LocalDate.parse(start) }.getOrDefault(LocalDate.now()))
            g=g.copy(period=p);start=bounds.start.toString();end=bounds.end.toString()
        }}
        item {OutlinedTextField(start,{start=it},label={Text("Desde AAAA-MM-DD")});OutlinedTextField(end,{end=it},label={Text("Hasta AAAA-MM-DD")})}
        item {ChoiceMenu("Estado",g.status,listOf("ACTIVE" to "Activo","PAUSED" to "Pausado","COMPLETED" to "Completado","ARCHIVED" to "Archivado")) {g=g.copy(status=it,completedOn=if(it=="COMPLETED") g.completedOn ?: LocalDate.now() else null)}}
        item {OutlinedTextField(g.criterion,{g=g.copy(criterion=it)},label={Text("Criterio de cumplimiento")})}
        item {ChoiceMenu("Progreso",g.source.label,GoalSource.entries.map {it to it.label}) { source ->
            g=g.copy(source=source,linkedIds=emptyList(),unit=when(source) {GoalSource.HABIT->"días equivalentes";GoalSource.TASK->"tareas";GoalSource.FOCUS->"min";GoalSource.CHILDREN->"%";else->""});if(source==GoalSource.CHILDREN) target="100"
        }}
        item {OutlinedTextField(target,{target=it},label={Text("Meta medible")});OutlinedTextField(g.unit,{g=g.copy(unit=it)},label={Text("Unidad / moneda")})}
        if(g.source==GoalSource.MANUAL) item {OutlinedTextField(progress,{progress=it},label={Text("Progreso manual actual")})}
        val links=when(g.source) {
            GoalSource.HABIT -> choices.habits.map {it.id.toString() to it.name}
            GoalSource.TASK -> choices.tasks.map {it.id.toString() to it.title}
            GoalSource.FOCUS -> FocusCategoryType.entries.map {it.name to it.name}+choices.habits.map {it.id.toString() to "Hábito: ${it.name}"}
            GoalSource.SAVINGS -> choices.savings
            else -> emptyList()
        }
        if(links.isNotEmpty()) item {Text("Vínculos: se deduplican por registro. Una sola fuente por objetivo.",style=MaterialTheme.typography.bodySmall)}
        items(links,key={it.first}) { (id,label) -> Row {Checkbox(id in g.linkedIds,{checked ->g=g.copy(linkedIds=if(!checked) g.linkedIds-id else if(g.source==GoalSource.SAVINGS) listOf(id) else (g.linkedIds+id).distinct())});Text(label,Modifier.padding(top=12.dp))} }
        item {ChoiceMenu("Superior opcional",all.firstOrNull {it.id==g.parentId}?.title ?: "Independiente",listOf("" to "Independiente")+all.filter {it.id!=g.id && it.period.ordinal>g.period.ordinal}.map {it.id to it.title}) {g=g.copy(parentId=it.takeIf {it.isNotBlank()})}}
        item {Text("Pasos del plan");OutlinedTextField(newStep,{newStep=it},label={Text("Nuevo paso")});TextButton({g=g.copy(steps=g.steps+SubTask(newStep.trim()));newStep=""},enabled=newStep.isNotBlank()) {Text("Agregar paso")}}
        items(g.steps.indices.toList()) { index -> Row {Checkbox(g.steps[index].completed,{g=g.copy(steps=g.steps.mapIndexed {i,s ->if(i==index) s.copy(completed=!s.completed) else s})});Text(g.steps[index].title,Modifier.weight(1f));TextButton({g=g.copy(steps=g.steps.filterIndexed {i,_ ->i!=index})}) {Text("Quitar")}}}
        item {OutlinedTextField(g.notes,{g=g.copy(notes=it)},label={Text("Notas")});error?.let {Text(it,color=MaterialTheme.colorScheme.error)}}
    } },confirmButton={TextButton({runCatching { g.copy(start=LocalDate.parse(start),end=LocalDate.parse(end),target=target.toDouble(),manualProgress=progress.toDouble()).also {GoalRules.validate(it,all)} }.onSuccess(onSave).onFailure {error=it.message ?: "Revisa los campos"}}) {Text("Guardar")}},dismissButton={TextButton(onDismiss) {Text("Cancelar")}})
}

@Composable
private fun <T> ChoiceMenu(label: String,current: String,options: List<Pair<T,String>>,onSelect: (T)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {OutlinedButton({expanded=true}) {Text("$label: $current ▾")};DropdownMenu(expanded,{expanded=false}) {options.forEach { (value,text) ->DropdownMenuItem(text={Text(text)},onClick={onSelect(value);expanded=false})}}}
}
