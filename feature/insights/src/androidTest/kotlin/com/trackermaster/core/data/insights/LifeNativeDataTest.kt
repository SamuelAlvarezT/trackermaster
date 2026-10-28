package com.trackermaster.core.data.insights

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.data.mapper.toEpochMilli
import com.trackermaster.core.data.repository.JournalRepository
import com.trackermaster.core.database.*
import com.trackermaster.core.database.entity.*
import com.trackermaster.core.domain.insights.*
import com.trackermaster.core.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

class LifeNativeDataTest {
    private lateinit var db: TrackermasterDatabase
    private lateinit var journal: JournalRepository
    private lateinit var repo: LifeRepository
    private val day=LocalDate.of(2026,10,24)
    private lateinit var clock: ProgressClock
    @Before fun setup() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        db=Room.inMemoryDatabaseBuilder(context,TrackermasterDatabase::class.java).allowMainThreadQueries().build()
        clock=object: ProgressClock(context) {
            override fun today()=day
            override fun zone()=ZoneId.of("America/Bogota")
            override fun wallMs()=day.atTime(12,0).atZone(zone()).toInstant().toEpochMilli()
        }
        journal=JournalRepository(db.journalDao(),db,clock);repo=LifeRepository(db,journal,clock)
    }
    @After fun close() {db.close()}
    private suspend fun counter() = db.habitDao().insert(HabitEntity(name="Secret habit",progressType="COUNTER",scheduleType="DAILY",targetCount=10,colorArgb=0,createdEpochDay=day.minusDays(5).toEpochDay(),creationDateKnown=true))
    private fun nativeImage(): String {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.filesDir,"native-life-${java.util.UUID.randomUUID()}.png")
        val bitmap=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        return file.absolutePath
    }
    private fun entry(habit: Long?,count: Int=2) = JournalEntry(title="Secret title",richTextHtml="Secret personal body",createdAt=day.atTime(10,0),updatedAt=day.atTime(10,0),contentDate=day,habitId=habit,contribution=count,attachments=listOf(Attachment(path=nativeImage(),mimeType="image/jpeg")))
    @Test fun editingMovingTrashingAndRestoringOnlyChangesOwnContribution() = runBlocking {
        val habit=counter()
        db.habitLogDao().insert(HabitLogEntity(habitId=habit,dateEpoch=day.toEpochMilli(),value=3))
        val id=journal.save(entry(habit))
        val original=journal.versions(id).first().first()
        assertEquals(5,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        val saved=journal.observeEntries().first().first()
        journal.save(saved);journal.save(saved)
        assertEquals(5,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        assertEquals(1,db.journalDao().getAttachments(id).size)
        val other=journal.save(entry(habit,5))
        assertTrue(db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.completed)
        journal.save(saved.copy(contentDate=day.minusDays(1),attachments=emptyList()))
        assertEquals(8,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        assertFalse(db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.completed)
        assertEquals(2,db.habitLogDao().getForDate(habit,day.minusDays(1).toEpochMilli())!!.value)
        journal.restore(id,original.id)
        assertEquals(10,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        assertEquals(1,journal.observeEntries().first().first {it.id==id}.attachments.size)
        assertTrue(journal.versions(id).first().size>=5)
        journal.trash(id);assertEquals(8,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        journal.recover(id);assertEquals(10,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        journal.trash(id);journal.permanentlyDelete(id)
        journal.trash(other)
        assertEquals(3,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        assertTrue(journal.versions(id).first().isEmpty())
    }
    @Test fun checkboxKeepsManualCompletionAndIndependentEntries() = runBlocking {
        val habit=db.habitDao().insert(HabitEntity(name="Write",progressType="CHECKBOX",scheduleType="DAILY",colorArgb=0))
        val a=journal.save(entry(habit));val b=journal.save(entry(habit))
        journal.trash(a);assertTrue(db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.completed)
        val log=db.habitLogDao().getForDate(habit,day.toEpochMilli())!!
        db.habitLogDao().update(log.copy(completed=true,completionSource="manual"))
        journal.trash(b);assertTrue(db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.completed)
    }
    @Test fun timerJournalAndFocusContributionsAreIndependentAndDontDoubleCountFocus() = runBlocking {
        val habit=db.habitDao().insert(HabitEntity(name="Private timer",progressType="TIMER",scheduleType="DAILY",targetDurationMs=1_800_000,colorArgb=0,createdEpochDay=day.toEpochDay(),creationDateKnown=true))
        val wall=clock.wallMs()
        val linked=db.focusDao().insert(FocusSessionEntity(category="WORK",plannedMinutes=30,actualSeconds=900,actualDurationMs=900_000,habitId=habit,startedAtEpoch=wall,completedAtEpoch=wall))
        db.progressDao().saveContribution(FocusHabitContributionEntity(linked,day.toEpochDay(),habit,900_000))
        db.focusDao().insert(FocusSessionEntity(category="STUDY",plannedMinutes=10,actualSeconds=600,actualDurationMs=600_000,startedAtEpoch=wall,completedAtEpoch=wall))
        db.habitLogDao().insert(HabitLogEntity(habitId=habit,dateEpoch=day.toEpochMilli(),durationMs=900_000,completionSource="focus"))
        val id=journal.save(entry(habit,15));assertTrue(db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.completed)
        val g=LifeGoal(title="Secret goal",source=GoalSource.FOCUS,linkedIds=listOf(habit.toString(),"WORK"),start=day,end=day,target=30.0)
        repo.save(g)
        val report=repo.report(LifePeriod.LAST_SEVEN,day).first()
        assertEquals(25.0,report.metrics.first {it.key=="focus"}.value,0.0)
        assertEquals(15.0,report.goals.first().second,0.0)
        val payload=AggregatePayload.encode(report)
        for(secret in listOf("Secret","Private","photo.jpg","personal body")) assertFalse(payload.contains(secret))
        journal.trash(id)
        val log=db.habitLogDao().getForDate(habit,day.toEpochMilli())!!
        assertEquals(900_000L,log.durationMs);assertFalse(log.completed)
    }
    @Test fun independentDreamVersionsPreserveAllFieldsAndRestoreCreatesNewRevision() = runBlocking {
        val first=entry(null).copy(kind="DREAM",emotions="Calm",tags="water",lucid=true,moodLevel=4)
        val id=journal.save(first);val version=journal.versions(id).first().first()
        val current=journal.observeEntries().first().first()
        journal.save(current.copy(title="Changed",emotions="Joy",tags="sky",attachments=emptyList(),lucid=false))
        journal.restore(id,version.id)
        val restored=journal.observeEntries().first().first()
        assertEquals(first.title,restored.title);assertEquals("Calm",restored.emotions);assertEquals("water",restored.tags);assertTrue(restored.lucid)
        assertEquals(first.attachments.first().path,restored.attachments.first().path)
        assertTrue(java.io.File(restored.attachments.first().path).isFile)
        assertEquals(3,journal.versions(id).first().size)
    }
    @Test fun moneySeparatesCurrenciesTransfersAndBudgetConsumptionFromCashBalance() = runBlocking {
        val usd=db.expenseDao().insertAccount(AccountEntity(name="Private USD",type="CASH",currencyCode="USD"))
        val cop=db.expenseDao().insertAccount(AccountEntity(name="Private COP",type="CASH",currencyCode="COP"))
        suspend fun movement(account: Long,type: String,minor: Long,transfer: String?=null)=db.expenseDao().insertTransaction(TransactionEntity(accountId=account,amount=0.0,type=type,categoryId=1,dateEpoch=day.toEpochMilli(),amountMinor=minor,dateEpochDay=day.toEpochDay(),transferId=transfer))
        movement(usd,"INCOME",10000);movement(usd,"EXPENSE",2500);movement(cop,"INCOME",1000000)
        db.moneyDao().insertTransfer(MoneyTransferEntity("transfer",usd,cop))
        movement(usd,"EXPENSE",5000,"transfer");movement(cop,"INCOME",20000000,"transfer")
        db.expenseDao().insertBudget(BudgetEntity(categoryId=0,limitAmount=50.0,limitAmountMinor=5000,currencyCode="USD",period="CUSTOM",startDateEpochDay=day.toEpochDay(),endDateEpochDay=day.toEpochDay()))
        val report=repo.report(LifePeriod.MONTH,day).first()
        assertEquals(75.0,report.metrics.first {it.key=="balance_USD"}.value,0.0)
        assertEquals(10000.0,report.metrics.first {it.key=="balance_COP"}.value,0.0)
        assertEquals(50.0,report.metrics.first {it.key.startsWith("budget_")}.value,0.0)
        assertFalse(report.metrics.any {it.key=="balance"})
    }
    @Test fun failedAiWithoutKeyPreservesSavedReviewAndIdenticalDataReusesSnapshot() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val service=LifeReviews(repo,db,clock,LifeApiKey(context))
        val original=service.generate(LifePeriod.WEEK,day)
        val reused=service.generate(LifePeriod.WEEK,day)
        assertEquals(original.id,reused.id)
        assertTrue(runCatching {service.generate(LifePeriod.WEEK,day,withAi=true)}.isFailure)
        assertEquals(original.id,repo.reviews().first().first().id)
    }
    @Test fun trashCanBeRecoveredAfterItsLinkedHabitWasDeleted() = runBlocking {
        val habit=counter();val id=journal.save(entry(habit))
        journal.trash(id)
        db.habitDao().getById(habit)?.let {db.habitDao().delete(it)}
        journal.recover(id)
        assertEquals(id,journal.observeEntries().first().first().id)
        assertTrue(journal.versions(id).first().isNotEmpty())
    }
    @Test fun changingJournalTypeKeepsIdentityVersionsAndSingleHabitContribution() = runBlocking {
        val habit=counter()
        val id=journal.save(entry(habit))
        val original=journal.versions(id).first().first()
        val saved=journal.observeEntries().first().first()
        journal.save(saved.copy(kind="DREAM",emotions="Calm",lucid=true))
        assertEquals(id,journal.observeEntries().first().first().id)
        assertEquals("DREAM",journal.observeEntries().first().first().kind)
        assertEquals(2,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        journal.restore(id,original.id)
        assertEquals("PERSONAL",journal.observeEntries().first().first().kind)
        assertEquals(2,db.habitLogDao().getForDate(habit,day.toEpochMilli())!!.value)
        assertEquals(3,journal.versions(id).first().size)
    }
    @Test fun goalHierarchyUsesNormalizedChildrenRejectsCyclesAndKeepsIndependentStates() = runBlocking {
        val parent=LifeGoal(id="annual",title="Synthetic annual",period=LifePeriod.YEAR,start=day.withDayOfYear(1),end=day.withDayOfYear(365),source=GoalSource.CHILDREN,target=100.0)
        repo.save(parent)
        val child=LifeGoal(id="weekly",title="Synthetic child",start=day,end=day,target=10.0,manualProgress=5.0,parentId=parent.id)
        repo.save(child)
        assertEquals(50.0,repo.goalProgress().first()[parent.id]!!,0.0)
        assertTrue(runCatching {repo.save(parent.copy(parentId=child.id))}.isFailure)
        repo.save(child.copy(status="COMPLETED",manualProgress=10.0,completedOn=day))
        assertEquals("ACTIVE",repo.goals().first().first {it.id==parent.id}.status)
        assertEquals(100.0,repo.goalProgress().first()[parent.id]!!,0.0)
        repo.delete(parent.id)
        assertNull(repo.goals().first().first {it.id==child.id}.parentId)
    }
}
