package com.trackermaster.core.data.insights

import android.content.Context
import android.security.keystore.*
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.data.mapper.toEpochMilli
import com.trackermaster.core.database.*
import com.trackermaster.core.domain.insights.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.File
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.time.*
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.inject.*

/** Deliberate allowlist: personal titles, notes, bodies, names and image paths cannot reach the API. */
object AggregatePayload {
    fun encode(report: LifeReport): String = JSONObject().apply {
        put("from",report.comparison.current.start);put("to",report.comparison.current.end)
        put("previousFrom",report.comparison.previous.start);put("previousTo",report.comparison.previous.end)
        put("metrics",JSONArray().apply { report.metrics.forEach { m ->
            put(JSONObject().put("metric",m.key).put("value",m.value).put("previous",m.previous))
        } })
        put("goalCount",report.goals.size)
        put("goals",JSONArray().apply { report.goals.forEachIndexed { index,(goal,value) ->
            put(JSONObject().put("id","goal_${index+1}").put("ratio",GoalRules.ratio(value,goal.target)).put("completed",goal.status=="COMPLETED")
                .put("planSteps",goal.steps.size).put("completedSteps",goal.steps.count { it.completed }))
        } })
    }.toString()
    fun fingerprint(raw: String) = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
}

@Singleton
class LifeApiKey @Inject constructor(@ApplicationContext context: Context) {
    private val file=File(context.noBackupFilesDir,"life-gemini-key")
    private fun key(): javax.crypto.SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("life-gemini",null) as? javax.crypto.SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("life-gemini",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun configured() = file.exists()
    fun save(value: String) {
        if(value.isBlank()) { file.delete();return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted=cipher.doFinal(value.trim().toByteArray())
        file.writeBytes(byteArrayOf(cipher.iv.size.toByte())+cipher.iv+encrypted)
    }
    internal fun read(): String? = if(!file.exists()) null else runCatching {
        val bytes=file.readBytes();val size=bytes[0].toInt()
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,1+size))) }
            .doFinal(bytes.copyOfRange(1+size,bytes.size)).toString(Charsets.UTF_8)
    }.getOrNull()
}

@Singleton
class LifeReviews @Inject constructor(private val repo: LifeRepository,private val db: TrackermasterDatabase,private val clock: ProgressClock,private val keys: LifeApiKey) {
    private val mutex=Mutex()
    suspend fun generate(period: LifePeriod,anchor: LocalDate,withAi: Boolean=false,refresh: Boolean=false): ReviewRecord = mutex.withLock {
        val report=repo.report(period,anchor).first()
        val payload=AggregatePayload.encode(report)
        // Personal text stays local; this hash also invalidates a local review after editing a plan.
        val fingerprint=AggregatePayload.fingerprint(payload+report.goals.joinToString { LifeJson.goal(it.first) })
        val span=report.comparison.current
        val existing=repo.reviews().first().firstOrNull { it.period==period.name && it.start==span.start && it.end==span.end && it.provisional==report.comparison.provisional && it.fingerprint==fingerprint && (!withAi || it.analysis.startsWith("Interpretación de IA")) }
        if(existing!=null && !refresh) return@withLock existing
        val text=if(withAi) analyze(payload) else localAdvice(report)
        val record=ReviewRecord(java.util.UUID.randomUUID().toString(),period.name,span.start,span.end,report.comparison.provisional,
            clock.wallMs(),fingerprint,report.calculatedText(),text)
        db.lifeDao().saveReview(LifeReviewEntity(record.id,record.period,record.start.toEpochDay(),record.end.toEpochDay(),record.provisional,record.createdAt,record.fingerprint,record.calculated,record.analysis))
        record
    }
    /** Foreground catch-up: runs off the main thread; never requires the app to be open on Sunday. */
    suspend fun catchUp() = withContext(Dispatchers.Default) {
        val data=repo.data().first()
        val first=(data.activity.logs.map { it.date }+data.activity.journal.map { it.contentDate }+data.activity.tasks.filter { it.createdAt.toEpochMilli()>0 }.map { it.createdAt.toLocalDate() }+
            data.finance.transactions.filter { it.dateEpochDay!=0L }.map { LocalDate.ofEpochDay(it.dateEpochDay) }+data.focus.contributions.map { LocalDate.ofEpochDay(it.dateEpochDay) }+
            data.focus.sessions.mapNotNull { (it.startedAtEpoch ?: it.completedAtEpoch)?.let { ms -> Instant.ofEpochMilli(ms).atZone(clock.zone()).toLocalDate() } }).minOrNull() ?: data.today
        val stored=repo.reviews().first()
        for(period in listOf(LifePeriod.WEEK,LifePeriod.MONTH,LifePeriod.QUARTER,LifePeriod.HALF,LifePeriod.YEAR)) {
            var bounds=LifePeriods.bounds(period,first)
            while(bounds.end<data.today || (period==LifePeriod.WEEK && bounds.end==data.today)) {
                if(stored.none { it.period==period.name && it.start==bounds.start && it.end==bounds.end && !it.provisional }) generate(period,bounds.end)
                bounds=LifePeriods.bounds(period,bounds.end.plusDays(1))
            }
        }
    }
    private fun localAdvice(report: LifeReport): String = buildString {
        appendLine("Revisión local basada en registros; sin interpretación de IA.")
        if(report.comparison.provisional) appendLine("Periodo provisional: sus resultados aún pueden cambiar.")
        val habit=report.metrics.first { it.key=="habits" }
        appendLine("Avances: cumplimiento de hábitos ${"%.1f".format(habit.value)} %. ${LifeCalculations.variation(habit.value,habit.previous)} respecto al periodo anterior.")
        if(report.goals.isEmpty()) appendLine("No hay objetivos ni planes definidos para este periodo.")
        else report.goals.forEach { (g,v) ->
            appendLine("${g.title}: ${"%.1f".format(GoalRules.ratio(v,g.target)*100)} %; ${g.steps.count { it.completed }}/${g.steps.size} pasos realizados. Criterio: ${g.criterion.ifBlank { "meta medible" }}.")
        }
        appendLine("Dificultades y acciones para el siguiente periodo:")
        report.difficulties.firstOrNull()?.let { appendLine("• Revisa la dificultad de ${it.first} y reserva una acción concreta en tu plan.") }
        val overdue=report.metrics.first { it.key=="tasks_overdue" }.value.toInt()
        if(overdue>0) appendLine("• Reprograma o completa las $overdue tareas vencidas.")
        if(report.goals.any { GoalRules.ratio(it.second,it.first.target)<1 }) appendLine("• Elige el próximo paso pendiente de tus objetivos y asigna una fecha.")
        if(report.metrics.first { it.key=="focus" }.value==0.0) appendLine("• Si el trabajo concentrado forma parte de tu plan, registra una sesión de Focus.")
        append("Los progresos manuales utilizan la última versión guardada hasta la fecha consultada; no se inventan estados anteriores al primer registro.")
    }
    private suspend fun analyze(payload: String): String = withContext(Dispatchers.IO) {
        val key=keys.read() ?: error("Configura una clave válida de un proyecto gratuito sin facturación")
        val connection=URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent").openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=30000;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("x-goog-api-key",key)
            val prompt="Interpreta estas estadísticas ya calculadas en español. No recalcules cifras oficiales ni inventes datos o metas. Da un resumen, observaciones por Habits, Tasks, Money, Focus, Journal y objetivos, dificultades, cambios respecto al periodo anterior y tres acciones concretas para el siguiente periodo. Distingue sugerencias de hechos y ausencia de datos. Los identificadores son neutros. Datos: $payload"
            val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt)))))
                .put("generationConfig",JSONObject().put("maxOutputTokens",1800).put("temperature",0.3))
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            when(connection.responseCode) {
                200 -> {
                    val response=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val parts=response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val answer=parts?.let { (0 until it.length()).mapNotNull { i -> it.optJSONObject(i)?.optString("text") }.joinToString("\n") }.orEmpty()
                    check(answer.isNotBlank()) { "La API no devolvió un análisis. Se conserva la revisión anterior." }
                    "Interpretación de IA · Gemini 2.5 Flash-Lite\n$answer"
                }
                400,401,403 -> error("Clave inválida, acceso denegado o modelo no disponible. Revisa la configuración en AI Studio.")
                429 -> error("Cuota agotada. Inténtalo más tarde; se conserva la última revisión válida.")
                else -> error("API no disponible (${connection.responseCode}). Se conserva la última revisión válida.")
            }
        } catch(e: java.io.IOException) { error("Sin conexión o tiempo de espera agotado. Se conserva la última revisión válida.") }
        finally { connection.disconnect() }
    }
}
