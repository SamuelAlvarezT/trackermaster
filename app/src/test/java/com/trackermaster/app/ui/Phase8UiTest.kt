package com.trackermaster.app.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.trackermaster.core.domain.habits.PendingHabit
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.domain.focus.FocusRunStatus
import com.trackermaster.core.data.progress.FocusRuntimeState
import com.trackermaster.core.ui.theme.TrackermasterTheme
import com.trackermaster.feature.focus.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="en-w393dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase8UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @get:Rule val name=org.junit.rules.TestName()
    private val day=LocalDate.of(2026,10,18)
    private val habit=Habit(id=7,name="Meditate",progressType=ProgressType.TIMER,scheduleType=ScheduleType.DAILY,targetDurationMs=1_800_000)
    @androidx.compose.runtime.Composable
    private fun FocusFixture(state: FocusUiState,onAction: (FocusAction)->Unit) {
        androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
            FocusContent(state,onAction)
        }
    }
    @Before fun setup() { compose.activity.actionBar?.hide() }
    @Test fun legacyTaskWithoutCreationTimestampNeverShowsAFabricatedAge() {
        val task=androidx.compose.runtime.mutableStateOf(Task(title="Legacy task",
            createdAt=java.time.Instant.EPOCH.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()))
        compose.setContent { TrackermasterTheme(ThemeMode.DARK,0) {
            com.trackermaster.app.tasks.TaskRow(task.value,onToggle={},onDelete={},onToggleSubtask={},onAddSubtask={})
        } }
        compose.onNodeWithText("Creation date unavailable").assertIsDisplayed()
        compose.runOnIdle {task.value=task.value.copy(completed=true)}
        compose.onNodeWithText("Completed").assertIsDisplayed()
        compose.onNodeWithText("Creation date unavailable").assertDoesNotExist()
    }
    @Test fun compactHomeCardGrowsToFitLargeTextInsteadOfClippingItsValue() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.3f)
            ) {
                TrackermasterTheme(ThemeMode.DARK,0) {
                    CompactStatCard("Available balance by currency","COP 139,845.00 · USD 9.00",
                        androidx.compose.ui.Modifier.width(160.dp).testTag("large_home_card"))
                }
            }
        }
        val bounds=compose.onNodeWithTag("large_home_card").fetchSemanticsNode().boundsInRoot
        assertTrue("The card must grow beyond its old fixed height",bounds.height>88f)
        compose.onNodeWithText("COP 139,845.00 · USD 9.00").assertIsDisplayed()
    }
    @After fun capture() {
        if(name.methodName=="missingBiometricHardwareNeverUnlocksTheAppAutomatically" || name.methodName=="focusSupportsThreeLanguagesWithLargeFonts") return
        captureScreen(name.methodName ?: "screen")
    }
    private fun captureScreen(filename: String) {
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val folder=java.io.File((System.getProperty("phase8.screenshots") ?: "build/phase8-screenshots")).apply { mkdirs() }
            java.io.File(folder,"$filename.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun pendingCardFormatsFractionsDistinguishesTodayAndOpensTheHabit() {
        var opened: Long?=null
        compose.setContent { TrackermasterTheme(ThemeMode.LIGHT,0) {
            PendingHabitsCard(PendingHabitsUiState(false,listOf(PendingHabit(habit,1.5,0.5,day.minusDays(1),true))),{opened=it})
        } }
        compose.onNodeWithText("Meditate").performClick()
        assertEquals(7L,opened)
        compose.onNodeWithText("1.5",substring=true).assertExists()
        compose.onNodeWithText("today",substring=true,ignoreCase=true).assertExists()
    }
    @Test fun pendingCardProvidesAnEmptyState() {
        compose.setContent { TrackermasterTheme(ThemeMode.OLED,0) { PendingHabitsCard(PendingHabitsUiState(false),{}) } }
        compose.onNodeWithText("No habits pending",substring=true,ignoreCase=true).assertExists()
    }
    @Test fun focusCanSelectAHabitAndStartAShorterSession() {
        val actions=mutableListOf<FocusAction>()
        compose.setContent { TrackermasterTheme(ThemeMode.DARK,0) {
            FocusFixture(FocusUiState(habits=listOf(habit),selectedHabitId=7,minutes="10",loading=false),actions::add)
        } }
        compose.onNodeWithTag("focus_habit").performScrollTo().performClick()
        compose.onNodeWithText("No linked habit").performClick()
        assertEquals(FocusAction.HabitSelected(null),actions.last())
        compose.onNodeWithTag("focus_duration").performScrollTo().performTextReplacement("20")
        assertEquals(FocusAction.Duration("20"),actions.last())
        compose.onNodeWithText("30",substring=true).assertExists()
        compose.onNodeWithText("Start").performScrollTo().performClick()
        assertEquals(FocusAction.Start,actions.last())
    }
    @Test fun pausedFocusDisablesConfigurationAndOffersResumeAndStop() {
        val actions=mutableListOf<FocusAction>()
        compose.setContent { TrackermasterTheme(ThemeMode.LIGHT,0) {
            FocusFixture(FocusUiState(runtime=FocusRuntimeState(1,7,FocusCategoryType.STUDY,600_000,120_000,FocusRunStatus.PAUSED),
                habits=listOf(habit),loading=false),actions::add)
        } }
        compose.onNodeWithTag("focus_duration").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Resume").performScrollTo().performClick();assertEquals(FocusAction.Resume,actions.last())
        compose.onNodeWithText("Finish early").performClick();assertEquals(FocusAction.Stop,actions.last())
    }
    @Test fun finishedSessionShowsTheConfiguredDurationForTheNextSession() {
        compose.setContent { TrackermasterTheme(ThemeMode.LIGHT,0) {
            FocusFixture(FocusUiState(runtime=FocusRuntimeState(1,7,FocusCategoryType.STUDY,600_000,600_000,FocusRunStatus.COMPLETED),
                habits=listOf(habit),selectedHabitId=7,minutes="20",loading=false),{})
        } }
        compose.onNodeWithText("20:00").assertExists()
        compose.onNodeWithTag("focus_duration").assertIsEnabled()
    }
    @Test fun missingBiometricHardwareNeverUnlocksTheAppAutomatically() {
        val activity=org.robolectric.Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java).setup()
        var unlocked=false
        val visible=androidx.compose.runtime.mutableStateOf(true)
        try {
            compose.setContent { TrackermasterTheme(ThemeMode.LIGHT,0) {
                if(visible.value) com.trackermaster.app.security.BiometricGate(activity.get()) { unlocked=true }
            } }
            compose.waitForIdle()
            assertFalse(unlocked)
            compose.onNodeWithText("Retry authentication").assertExists().performClick()
            compose.waitForIdle();assertFalse(unlocked)
        } finally {
            compose.runOnIdle { visible.value=false };compose.waitForIdle()
            activity.pause().stop().destroy()
        }
    }
    @Test fun focusSupportsThreeLanguagesWithLargeFonts() {
        val selected=androidx.compose.runtime.mutableStateOf("en" to ThemeMode.LIGHT)
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.5f)) {
                com.trackermaster.core.ui.LocalizedAppContent(selected.value.first) {
                    TrackermasterTheme(selected.value.second,0) {
                        FocusFixture(FocusUiState(habits=listOf(habit),selectedHabitId=7,minutes="10",loading=false),{})
                    }
                }
            }
        }
        for(option in listOf("en" to ThemeMode.LIGHT,"es" to ThemeMode.DARK,"zh" to ThemeMode.OLED)) {
            compose.runOnIdle { selected.value=option }
            val localized=compose.activity.createConfigurationContext(android.content.res.Configuration(compose.activity.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(option.first))
            })
            val start=localized.getString(com.trackermaster.core.ui.R.string.p8_focus_start)
            compose.onNodeWithText(start).performScrollTo().assertIsEnabled()
            compose.onNodeWithText(localized.getString(com.trackermaster.core.ui.R.string.p8_focus_title)).performScrollTo()
            captureScreen("focus-${option.first}-${option.second.name}-font150")
        }
    }
}
