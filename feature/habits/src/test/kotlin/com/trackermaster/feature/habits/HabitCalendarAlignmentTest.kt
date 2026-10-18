package com.trackermaster.feature.habits

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.TrackermasterDatabase
import com.trackermaster.core.data.progress.ProgressClock
import com.trackermaster.core.data.repository.HabitRepository
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="w393dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabitCalendarAlignmentTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: TrackermasterDatabase
    private val owner=ViewModelStore()
    @After fun close() { owner.clear();if(::db.isInitialized)db.close() }
    private fun checkBothTabs(scale: Float,theme: ThemeMode) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,TrackermasterDatabase::class.java).allowMainThreadQueries().build()
        val repo=HabitRepository(db.habitDao(),db.habitLogDao(),db.achievementDao(),db,ProgressClock(context))
        val ids=runBlocking {
            val active=repo.saveHabit(Habit(name="An active habit",progressType=ProgressType.CHECKBOX,scheduleType=ScheduleType.DAILY))
            val archived=repo.saveHabit(Habit(name="An archived habit",progressType=ProgressType.CHECKBOX,scheduleType=ScheduleType.DAILY))
            repo.archiveHabit(archived,true);active to archived
        }
        val vm=ViewModelProvider(owner,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=HabitsViewModel(repo) as T
        })[HabitsViewModel::class.java]
        compose.activity.actionBar?.hide()
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                TrackermasterTheme(theme,0) { HabitsScreen(vm) }
            }
        }
        for((tab,id) in listOf(0 to ids.first,1 to ids.second)) {
            compose.runOnIdle { vm.selectTab(tab) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("habit_circle:$id:0",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            val centers=(0..6).map { index ->
                val header=compose.onNodeWithTag("habit_weekday:$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                val circle=compose.onNodeWithTag("habit_circle:$id:$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertEquals("tab=$tab column=$index",header.center.x,circle.center.x,0.6f)
                circle.center.x
            }
            val gaps=centers.zipWithNext { a,b -> b-a }
            // Equal dp columns share the remaining integer pixels (at most one pixel apart).
            gaps.forEach { assertEquals(gaps.first(),it,1f) }
            assertTrue(centers.last()>centers.first())
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val folder=java.io.File((System.getProperty("phase8.screenshots") ?: "build/phase8-screenshots")).apply { mkdirs() }
                java.io.File(folder,"calendar-${theme.name}-$scale-tab$tab.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
    }
    @Test fun bothTabsUseSameCentersAt393dp()=checkBothTabs(1f,ThemeMode.LIGHT)
    @Test @Config(qualifiers="w320dp-h900dp-mdpi") fun narrowScreenAndLargeFontsRemainAligned()=checkBothTabs(1.5f,ThemeMode.DARK)
    @Test @Config(qualifiers="w600dp-h1000dp-mdpi") fun tabletAndOledRemainAligned()=checkBothTabs(1f,ThemeMode.OLED)
}
