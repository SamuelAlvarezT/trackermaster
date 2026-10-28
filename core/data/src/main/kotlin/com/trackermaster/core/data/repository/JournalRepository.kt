package com.trackermaster.core.data.repository

import androidx.room.withTransaction
import com.trackermaster.core.data.insights.LifeJson
import com.trackermaster.core.data.mapper.*
import com.trackermaster.core.data.progress.*
import com.trackermaster.core.database.*
import com.trackermaster.core.database.dao.JournalDao
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.HabitScheduleLogic
import com.trackermaster.core.domain.habits.HabitProgressRules
import com.trackermaster.core.domain.model.*
import kotlinx.coroutines.flow.*
import java.time.*
import javax.inject.*

data class JournalRevision(val id: Long,val savedAt: Long,val entry: JournalEntry)
@Singleton
class JournalRepository @Inject constructor(private val journalDao: JournalDao,private val db: TrackermasterDatabase,private val clock: ProgressClock) {
    private val life get()=db.lifeDao()
    fun observeAllEntries(): Flow<List<JournalEntry>> = combine(journalDao.observeEntries(),journalDao.observeAllAttachments()) { entries,attachments ->
        entries.map { e -> e.toDomain().copy(attachments=attachments.filter { it.entryId==e.id }.map { it.toDomain() }) }
    }
    fun observeEntries() = observeAllEntries().map { entries -> entries.filter { it.deletedAt==null }.sortedByDescending { it.contentDate } }
    fun versions(id: Long) = life.versions(id).map { rows -> rows.map { JournalRevision(it.id,it.savedAt,LifeJson.journal(it.payload)) } }
    private suspend fun read(id: Long) = journalDao.getById(id)?.toDomain()?.let { e -> e.copy(attachments=journalDao.getAttachments(id).map { it.toDomain() }) }

    suspend fun save(entry: JournalEntry): Long = db.withTransaction {
        require(entry.kind in listOf("PERSONAL","DREAM") && entry.contribution in 1..1440)
        val old=read(entry.id)
        if(old!=null && life.versions(old.id).first().isEmpty()) snapshot(old)
        val changed=entry.copy(createdAt=old?.createdAt ?: entry.createdAt,updatedAt=LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.wallMs()),clock.zone()))
        val entity=changed.entity()
        val id=if(old==null) journalDao.insert(entity) else { journalDao.update(entity);entry.id }
        life.deleteAttachments(id)
        changed.attachments.distinctBy { it.path }.forEach { journalDao.insertAttachment(it.copy(id=0,entryId=id).toEntity()) }
        updateContribution(changed.copy(id=id),old!=null)
        snapshot(changed.copy(id=id))
        id
    }
    suspend fun trash(id: Long) { read(id)?.let { save(it.copy(deletedAt=clock.wallMs())) } }
    suspend fun recover(id: Long) { read(id)?.let { save(it.copy(deletedAt=null)) } }
    suspend fun restore(id: Long,revisionId: Long) = db.withTransaction {
        val current=requireNotNull(read(id))
        val revision=life.versions(id).first().first { it.id==revisionId }
        save(LifeJson.journal(revision.payload).copy(id=id,createdAt=current.createdAt,deletedAt=current.deletedAt))
    }
    suspend fun permanentlyDelete(id: Long) = db.withTransaction {
        val current=requireNotNull(read(id));require(current.deletedAt!=null)
        updateContribution(current)
        life.deleteVersions(id);life.deleteAttachments(id);journalDao.getById(id)?.let { journalDao.delete(it) }
        // Files are retained: snapshots and older backups may still refer to the same paths.
    }
    private suspend fun snapshot(entry: JournalEntry) = life.saveVersion(JournalVersionEntity(entryId=entry.id,savedAt=clock.wallMs(),payload=LifeJson.journal(entry)))

    private suspend fun updateContribution(entry: JournalEntry,allowArchived: Boolean=true) {
        val old=life.contribution(entry.id)
        val affected=mutableSetOf<Pair<Long,Long>>()
        if(old!=null) affected+=old.habitId to old.day
        val before=affected.associateWith { (habit,day) -> life.contributions(habit,day) }.toMutableMap()
        life.deleteContribution(entry.id)
        if(entry.deletedAt==null && entry.habitId!=null) {
            // A deleted habit must not prevent recovering the entry and its complete history.
            val habit=db.habitDao().getById(requireNotNull(entry.habitId))?.toDomain()
            if(habit!=null) {
            val v=HabitProgressRules.versionOn(habit,entry.contentDate,db.progressDao().history(habit.id).map { it.toDomain() })
            require(!habit.archived || allowArchived) { "Selecciona un hábito activo" }
            if(v.progressType!=ProgressType.CHECKLIST && HabitProgressRules.isDue(habit,entry.contentDate,v)) {
                val key=habit.id to entry.contentDate.toEpochDay()
                if(key !in before) before[key]=life.contributions(key.first,key.second)
                affected+=key
                val timed=v.progressType in listOf(ProgressType.TIMER,ProgressType.STOPWATCH)
                life.saveContribution(JournalContributionEntity(entry.id,habit.id,key.second,if(timed) 0 else entry.contribution,if(timed) entry.contribution*60_000L else 0))
            }
            }
        }
        for((habitId,day) in affected) {
            val habit=db.habitDao().getById(habitId)?.toDomain() ?: continue
            val date=LocalDate.ofEpochDay(day)
            val v=HabitProgressRules.versionOn(habit,date,db.progressDao().history(habitId).map { it.toDomain() })
            val existing=db.habitLogDao().getForDate(habitId,date.toEpochMilli())?.toDomain()
            val prior=before[habitId to day].orEmpty();val after=life.contributions(habitId,day)
            val base=existing ?: HabitLog(habitId=habitId,date=date,progressTypeAtLog=v.progressType,targetCountAtLog=v.targetCount,targetDurationMsAtLog=v.targetDurationMs,checklistSizeAtLog=v.checklistSize)
            val count=(base.value-prior.sumOf { it.count }).coerceAtLeast(0)+after.sumOf { it.count }
            val duration=(base.durationMs-prior.sumOf { it.durationMs }).coerceAtLeast(0)+after.sumOf { it.durationMs }
            val independent=base.completed && base.completionSource!="journal"
            val changed=base.copy(value=count,durationMs=duration,completed=independent)
            val complete=independent || if(v.progressType==ProgressType.CHECKBOX) after.isNotEmpty() else HabitProgressRules.progress(changed,v)>=1.0
            val result=changed.copy(completed=complete,completionSource=if(independent) base.completionSource else "journal")
            if(existing==null) db.habitLogDao().insert(result.toEntity()) else db.habitLogDao().update(result.toEntity())
        }
    }
    fun writingStreak() = observeEntries().map { HabitScheduleLogic.journalWritingStreak(it.map { e -> e.contentDate }) }
}
private fun JournalEntry.entity() = JournalEntryEntity(id,title,richTextHtml,createdAt.toEpochMilli(),updatedAt.toEpochMilli(),moodLevel,
    kind,contentDate.toEpochDay(),tags,emotions,lucid,habitId,contribution,deletedAt)
