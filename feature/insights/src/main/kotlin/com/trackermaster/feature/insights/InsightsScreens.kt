package com.trackermaster.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.data.insights.*
import com.trackermaster.core.domain.insights.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class InsightsViewModel @Inject constructor(private val repo: LifeRepository,private val reviewsRepo: LifeReviews,private val apiKey: LifeApiKey,private val moneyPreferences: com.trackermaster.core.data.money.MoneyPreferencesRepository?=null): ViewModel() {
    val selection=MutableStateFlow(LifePeriod.LAST_SEVEN to LocalDate.now())
    val error=MutableStateFlow<String?>(null)
    val working=MutableStateFlow(false)
    val hideMoney=(moneyPreferences?.preferences?.map { it.hideAmounts } ?: flowOf(false))
        .catch { emit(true) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),true)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val report=selection.flatMapLatest { repo.report(it.first,it.second) }.catch { error.value="No se pudo calcular el informe" }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val goals=repo.goals().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val goalProgress=repo.goalProgress().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyMap())
    val choices=repo.choices().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),LifeChoices())
    val reviews=repo.reviews().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun save(goal: LifeGoal,onSaved: ()->Unit) = viewModelScope.launch { runCatching { repo.save(goal) }.onSuccess { onSaved() }.onFailure { error.value=it.message ?: "No se pudo guardar" } }
    fun delete(id: String) = viewModelScope.launch { runCatching { repo.delete(id) }.onFailure { error.value="No se pudo eliminar" } }
    fun generate(ai: Boolean=false,refresh: Boolean=false) = viewModelScope.launch {
        if(working.value) return@launch
        working.value=true
        try { reviewsRepo.generate(selection.value.first,selection.value.second,ai,refresh) }
        catch(e: Exception) { error.value=e.message ?: "No se pudo generar la revisión" }
        finally { working.value=false }
    }
    fun configure(key: String) { runCatching { apiKey.save(key) }.onFailure { error.value="No se pudo proteger la clave en el dispositivo" } }
    fun configured() = apiKey.configured()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(initialTab: Int=0,initialFilter: String="Todas",onBack: ()->Unit={},vm: InsightsViewModel=hiltViewModel()) {
    var tab by remember { mutableIntStateOf(initialTab) }
    val error by vm.error.collectAsStateWithLifecycle()
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),topBar={ TopAppBar(title={ Text("Estadísticas",maxLines=1) },navigationIcon={ IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver") } }) }) { pad ->
        Column(Modifier.padding(pad)) {
            ScrollableTabRow(selectedTabIndex=tab,edgePadding=8.dp) {
                listOf("Resultados","Objetivos y planes","Revisiones").forEachIndexed { i,title -> Tab(selected=tab==i,onClick={tab=i},text={Text(title)}) }
            }
            when(tab) { 0 -> Results(vm,initialFilter);1 -> GoalsPanel(vm);else -> ReviewsPanel(vm) }
        }
    }
    error?.let { AlertDialog(onDismissRequest={vm.error.value=null},title={Text("Aviso")},text={Text(it)},confirmButton={TextButton({vm.error.value=null}) {Text("Cerrar")}}) }
}

@Composable
private fun PeriodSelector(vm: InsightsViewModel) {
    val selection by vm.selection.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(false) };var anchor by remember { mutableStateOf(selection.second.toString()) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Box {
            TextButton({menu=true}) { Text(selection.first.label+" ▾") }
            DropdownMenu(menu,{menu=false}) { LifePeriod.entries.forEach { period -> DropdownMenuItem(text={Text(period.label)},onClick={vm.selection.value=period to selection.second;menu=false}) } }
        }
        TextButton({anchor=selection.second.toString();date=true}) { Text(selection.second.toString()) }
    }
    if(date) AlertDialog(onDismissRequest={date=false},title={Text("Fecha del periodo")},text={OutlinedTextField(anchor,{anchor=it},label={Text("AAAA-MM-DD")})},
        confirmButton={TextButton({vm.selection.value=selection.first to LocalDate.parse(anchor);date=false},enabled=runCatching {LocalDate.parse(anchor)<=LocalDate.now()}.getOrDefault(false)) {Text("Consultar")}},dismissButton={TextButton({date=false}) {Text("Cancelar")}})
}

@Composable
private fun Results(vm: InsightsViewModel,initialFilter: String) {
    val report by vm.report.collectAsStateWithLifecycle()
    val hideMoney by vm.hideMoney.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(initialFilter) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PeriodSelector(vm) }
        report?.let { r ->
            item { Text("${r.comparison.current.start} — ${r.comparison.current.end}\nFrente a ${r.comparison.previous.start} — ${r.comparison.previous.end}\n${if(r.comparison.provisional) "Informe provisional" else "Periodo cerrado"}",style=MaterialTheme.typography.bodySmall) }
            item { LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { items(listOf("Todas","Habits","Tasks","Money","Focus","Journal","Objetivos")) { name -> FilterChip(selected=filter==name,onClick={filter=name},label={Text(name)}) } } }
            if(filter in listOf("Todas","Habits")) item { DailyChart("Habits · evolución en días equivalentes",r.comparison.current,r.habitDaily) }
            if(filter in listOf("Todas","Focus")) item { DailyChart("Focus · minutos por día",r.comparison.current,r.focusDaily) }
            items(r.metrics.filter { filter=="Todas" || it.title.startsWith(filter) },key={it.key}) { metric ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(metric.title,style=MaterialTheme.typography.titleSmall)
                    if(hideMoney && metric.title.startsWith("Money")) {
                        Text("Importes ocultos. Cambia la visibilidad desde Money.",style=MaterialTheme.typography.bodySmall)
                    } else {
                    Text("${"%.2f".format(metric.value)} ${metric.unit}",style=MaterialTheme.typography.headlineSmall)
                    MetricBars(metric.value,metric.previous)
                    Text("Anterior: ${"%.2f".format(metric.previous)} · ${LifeCalculations.variation(metric.value,metric.previous)}",style=MaterialTheme.typography.bodySmall)
                    if(metric.detail.isNotBlank()) Text(metric.detail,style=MaterialTheme.typography.bodySmall)
                    }
                } }
            }
            if(filter in listOf("Todas","Habits")) item { Text("Hábitos con mayor dificultad",style=MaterialTheme.typography.titleMedium)
                if(r.difficulties.isEmpty()) Text("Sin fechas programadas suficientes") else r.difficulties.forEach { Text("${it.first}: ${"%.1f".format(it.second*100)} %") }
            }
            item { Text("Sin registros, los valores son cero. Las fechas de creación desconocidas y finalizaciones antiguas sin hora no se inventan.",style=MaterialTheme.typography.bodySmall) }
        } ?: item { CircularProgressIndicator() }
    }
}

@Composable
private fun MetricBars(current: Double,previous: Double) {
    if(current==0.0 && previous==0.0) return
    val primary=MaterialTheme.colorScheme.primary
    val secondary=MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val min=minOf(0.0,current,previous);val max=maxOf(0.0,current,previous)
        val range=(max-min).coerceAtLeast(1e-9)
        val zero=((-min/range)*size.width).toFloat()
        drawLine(primary,Offset(zero,6f),Offset(((current-min)/range*size.width).toFloat(),6f),strokeWidth=8f)
        drawLine(secondary,Offset(zero,size.height-6f),Offset(((previous-min)/range*size.width).toFloat(),size.height-6f),strokeWidth=8f)
    }
}

@Composable
private fun DailyChart(title: String,span: DateSpan,values: Map<LocalDate,Double>) {
    val color=MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text(title,style=MaterialTheme.typography.titleSmall)
        if(values.values.none { it>0 }) Text("Sin actividad registrada en el periodo",Modifier.padding(vertical=16.dp))
        else {
            val max=values.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            Canvas(Modifier.fillMaxWidth().height(100.dp).padding(vertical=8.dp)) {
                val width=size.width/span.days
                for(index in 0 until span.days.toInt()) {
                    val amount=values[span.start.plusDays(index.toLong())] ?: 0.0
                    if(amount>0) drawLine(color,Offset(width*(index+0.5f),size.height),Offset(width*(index+0.5f),(size.height*(1-amount/max)).toFloat()),strokeWidth=(width*0.65f).coerceAtLeast(1f),cap=StrokeCap.Round)
                }
            }
            Text("Máximo diario: ${"%.1f".format(max)}",style=MaterialTheme.typography.labelSmall)
        }
        Text("${span.start} — ${span.end}",style=MaterialTheme.typography.labelSmall)
    } }
}

@Composable
private fun ReviewsPanel(vm: InsightsViewModel) {
    val records by vm.reviews.collectAsStateWithLifecycle();val busy by vm.working.collectAsStateWithLifecycle()
    val hideMoney by vm.hideMoney.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ReviewRecord?>(null) };var setup by remember { mutableStateOf(false) }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { PeriodSelector(vm);Text("Las revisiones semanales se preparan desde el domingo. Si no abres la app, se recuperan al volver. Cada versión queda guardada.") }
        item { Button({vm.generate()},enabled=!busy) {Text("Guardar revisión local")} }
        item { OutlinedButton({if(vm.configured()) vm.generate(ai=true) else setup=true},enabled=!busy) {Text("Analizar con IA / reutilizar")} }
        item { TextButton({if(vm.configured()) vm.generate(ai=true,refresh=true) else setup=true},enabled=!busy) {Text("Actualizar IA manualmente")} }
        item { TextButton({setup=true}) {Text("Configurar Gemini")};if(busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(records.isEmpty()) item {Text("Aún no hay revisiones guardadas.")}
        items(records,key={it.id}) { record -> OutlinedCard(onClick={selected=record},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("${LifePeriod.valueOf(record.period).label}: ${record.start} — ${record.end}")
            Text("${if(record.provisional) "Provisional" else "Periodo cerrado"} · ${Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).toLocalDateTime()}",style=MaterialTheme.typography.labelSmall)
            Text(if(record.analysis.startsWith("Interpretación de IA")) "Interpretación de IA guardada" else "Revisión local")
        } } }
    }
    selected?.let { r -> AlertDialog(onDismissRequest={selected=null},title={Text("Revisión ${r.start}")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(if(hideMoney) r.calculated.lineSequence().filterNot {it.startsWith("Money")}.joinToString("\n") else r.calculated)}
        item {Text(if(hideMoney) "Análisis guardado. Para consultarlo completo, muestra los importes desde Money." else r.analysis)}
    }},confirmButton={TextButton({selected=null}) {Text("Cerrar")}}) }
    if(setup) ApiSetup(onDismiss={setup=false},onSave={vm.configure(it);setup=false})
}

@Composable
private fun ApiSetup(onDismiss: ()->Unit,onSave: (String)->Unit) {
    var key by remember { mutableStateOf("") };var free by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val activity=remember(context) { generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull() }
    DisposableEffect(activity) {
        val window=activity?.window
        val wasSecure=((window?.attributes?.flags ?: 0) and android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if(!wasSecure) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    AlertDialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn),title={Text("Gemini · clave propia")},text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Modelo: gemini-2.5-flash-lite. Usa un proyecto gratuito SIN facturación. La app no activa facturación; una clave de un proyecto de pago sí podría generar cargos. Consulta tus cuotas en AI Studio.")
        Text("Se envían solo cifras agregadas; Google puede usar datos del nivel gratuito para mejorar sus productos. No se envían textos, imágenes ni nombres personales.")
        OutlinedTextField(key,{key=it},label={Text("Clave API")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true)
        Row { Checkbox(free,{free=it});Text("Confirmo que mi proyecto no tiene facturación habilitada") }
        Text("La clave se cifra en este dispositivo y queda excluida de las copias de seguridad.",style=MaterialTheme.typography.bodySmall)
    } },confirmButton={TextButton({onSave(key)},enabled=free && key.isNotBlank()) {Text("Guardar")}},dismissButton={Row { TextButton({onSave("")}) {Text("Quitar clave")};TextButton(onDismiss) {Text("Cancelar")} }})
}
