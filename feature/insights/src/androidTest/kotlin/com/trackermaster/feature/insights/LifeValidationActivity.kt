package com.trackermaster.feature.insights

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.data.insights.*
import com.trackermaster.core.data.repository.*
import com.trackermaster.core.database.*
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.domain.insights.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import com.trackermaster.feature.journal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*

/** Actual feature composables against an isolated synthetic DB, never the installed user's app. */
class LifeValidationActivity: ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val db=Room.databaseBuilder(this,TrackermasterDatabase::class.java,"life-ui.db").build()
        val clock=ProgressClock(this)
        val journal=JournalRepository(db.journalDao(),db,clock)
        val repo=LifeRepository(db,journal,clock)
        runBlocking(Dispatchers.IO) {
            if(db.habitDao().observeAll().first().isEmpty()) {
                val day=LocalDate.now()
                val habit=db.habitDao().insert(HabitEntity(name="Escritura de prueba",progressType="COUNTER",scheduleType="DAILY",targetCount=2,colorArgb=0,createdEpochDay=day.minusDays(7).toEpochDay(),creationDateKnown=true))
                val now=LocalDateTime.now()
                val personal=journal.save(JournalEntry(title="Reflexión de prueba",richTextHtml="Entrada sintética para verificar edición, fechas y versiones.",createdAt=now,updatedAt=now,habitId=habit))
                journal.save(JournalEntry(id=personal,title="Reflexión revisada",richTextHtml="Contenido actualizado; la versión anterior se conserva.",createdAt=now,updatedAt=now,habitId=habit,tags="prueba, calma"))
                journal.save(JournalEntry(title="Sueño de prueba",richTextHtml="Relato sintético de un sueño lúcido.",createdAt=now,updatedAt=now,kind="DREAM",emotions="Calma",lucid=true,tags="agua",habitId=habit))
                val deleted=journal.save(JournalEntry(title="Entrada en papelera",richTextHtml="Recuperable con su historial.",createdAt=now,updatedAt=now));journal.trash(deleted)
                repo.save(LifeGoal(title="Plan semanal de prueba",description="Datos sintéticos",start=day.minusDays(2),end=day.plusDays(4),target=4.0,source=GoalSource.HABIT,linkedIds=listOf(habit.toString()),unit="días equivalentes",criterion="Escribir según lo programado",steps=listOf(SubTask("Reservar un momento para escribir",true),SubTask("Revisar el domingo"))))
                db.taskDao().insert(TaskEntity(title="Tarea de prueba",plannedDate=day.toString()))
                db.focusDao().insert(FocusSessionEntity(category="STUDY",plannedMinutes=30,actualSeconds=900,actualDurationMs=900_000,completedAtEpoch=System.currentTimeMillis()))
            }
        }
        val key=LifeApiKey(this)
        val reviews=LifeReviews(repo,db,clock,key)
        val insightsVm=InsightsViewModel(repo,reviews,key)
        val habitRepo=HabitRepository(db.habitDao(),db.habitLogDao(),db.achievementDao(),db,clock)
        val journalVm=JournalViewModel(journal,MoodRepository(db.moodDao(),db.habitLogDao(),db.habitDao()),habitRepo)
        setContent { TrackermasterTheme(ThemeMode.DARK,0) {
            if(intent.getStringExtra("screen")=="journal") JournalScreen(journalVm)
            else InsightsScreen(initialTab=intent.getIntExtra("tab",0),onBack={finish()},vm=insightsVm)
        } }
    }
}
