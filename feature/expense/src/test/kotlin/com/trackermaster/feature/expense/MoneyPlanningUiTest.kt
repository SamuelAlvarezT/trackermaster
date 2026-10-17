package com.trackermaster.feature.expense

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.ui.theme.TrackermasterTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="w393dp-h852dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoneyPlanningUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val dispatcher=UnconfinedTestDispatcher()
    private var store:ViewModelStore?=null
    @Before fun before(){Dispatchers.setMain(dispatcher);compose.activity.actionBar?.hide()}
    @After fun after(){store?.clear();Dispatchers.resetMain()}
    private fun render(g:FakePlanningGateway):MoneyPlanningViewModel {
        val owner=ViewModelStore().also{store=it}
        val vm=ViewModelProvider(owner,object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel>create(modelClass:Class<T>):T=MoneyPlanningViewModel(g,SavedStateHandle(),dispatcher) as T
        })[MoneyPlanningViewModel::class.java]
        compose.setContent{val state by vm.state.collectAsState();MoneyLocale("en"){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyPlanningScreen(state,vm::onEvent,{})}}}
        return vm
    }
    @Test fun createBudgetRequiresAmountAndSavesWithoutConfirmingAPayment(){
        val g=FakePlanningGateway();render(g)
        compose.onAllNodesWithText("Create budget").onFirst().performClick()
        compose.onNodeWithTag("planning_save").assertIsNotEnabled()
        compose.onNodeWithTag("budget_amount").performTextInput("250")
        compose.onNodeWithTag("planning_save").assertIsEnabled().performClick()
        compose.waitForIdle();assertEquals(25000L,g.budgets.single().limit!!.minorUnits);assertTrue(g.confirmations.isEmpty())
    }
    @Test fun createRecurringPlanAndConfirmItsActualMovementThroughUi(){
        val g=FakePlanningGateway();val vm=render(g)
        compose.onNodeWithText("Payments and reminders").performClick()
        compose.onNodeWithText("New payment or reminder").performClick()
        compose.onNodeWithTag("plan_name").performTextInput("Data")
        compose.onNodeWithTag("plan_amount").performTextInput("15")
        compose.onNodeWithTag("planning_save").assertIsEnabled().performClick()
        compose.waitForIdle();assertEquals("Data",g.plans.single().name);assertTrue(g.confirmations.isEmpty())
        compose.runOnIdle{vm.onEvent(PlanningEvent.OpenSettlement(g.data.value.plans.first()))}
        compose.onNodeWithTag("settlement_amount").performTextClearance()
        compose.onNodeWithTag("settlement_amount").performTextInput("16")
        compose.onNodeWithTag("planning_save").performClick();compose.waitForIdle()
        assertEquals(1600L,g.confirmations.single().second.amount.minorUnits)
    }
    @Test fun linkAnExistingMovementInsteadOfRecordingItTwice(){
        val g=FakePlanningGateway();val vm=render(g)
        val tx=g.data.value.ledger.movements.first{it.transferId==null && it.direction==com.trackermaster.core.domain.money.MoneyDirection.EXPENSE}
        compose.runOnIdle{vm.onEvent(PlanningEvent.OpenSettlement(g.data.value.plans.first().copy(amount=tx.amount,categoryId=tx.categoryId)))}
        compose.onNodeWithText("Link this movement").performScrollTo().performClick()
        compose.onNodeWithText("Existing movement selected. The balance will stay unchanged.").assertExists()
        compose.onNodeWithTag("settlement_amount").assertIsNotEnabled()
        compose.onNodeWithText("Record a new movement").performScrollTo().performClick()
        val originalAmount=vm.state.value.controls.settlement!!.amount
        compose.onNodeWithTag("settlement_amount").performTextClearance()
        compose.onNodeWithTag("planning_save").assertIsNotEnabled()
        compose.onNodeWithTag("settlement_amount").performTextInput(originalAmount)
        compose.onNodeWithText("Link this movement").performScrollTo().performClick()
        compose.onNodeWithTag("planning_save").performClick();compose.waitForIdle()
        assertEquals(tx.id,g.confirmations.single().first.linked)
    }
    @Test fun deniedNotificationsLeavePlansVisibleAndPrivacyMasksBudgetNumbers(){
        val g=FakePlanningGateway();val vm=render(g)
        compose.onNodeWithText("Notifications are off. Your pending items remain available here.").assertExists()
        compose.onNodeWithText("Enable notifications").assertExists()
        compose.runOnIdle{vm.onEvent(PlanningEvent.Privacy)}
        compose.onAllNodesWithText("Amount hidden").onFirst().assertExists()
        compose.onAllNodes(hasText("Spent:",substring=true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Remaining:",substring=true)).assertCountEquals(0)
    }
    @Test fun nativeScreensForThreeThemesLanguagesAndFont150(){
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var language by mutableStateOf("es")
        var scale by mutableStateOf(1f)
        var state by mutableStateOf(PlanningPreviewData.state())
        compose.setContent{
            val base=LocalContext.current;val density=LocalDensity.current
            val config=remember(language,scale){Configuration(base.resources.configuration).apply{setLocale(Locale.forLanguageTag(language));fontScale=scale}}
            val context=remember(language,scale){base.createConfigurationContext(config)}
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,LocalDensity provides Density(density.density,scale)){
                key(theme,language,scale,state){MoneyLocale(language){TrackermasterTheme(theme,0){MoneyPlanningScreen(state,{},{})}}}
            }
        }
        val directory=File(requireNotNull(System.getProperty("money.screenshots")),"planning").apply{mkdirs()}
        fun capture(name:String){compose.waitForIdle();lateinit var bitmap:Bitmap
            compose.runOnIdle{val view=compose.activity.window.decorView;bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))}
            File(directory,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        }
        ThemeMode.entries.forEach{mode->listOf("en","es","zh").forEach{tag->
            compose.runOnIdle{theme=mode;language=tag};capture("budgets-${mode.name.lowercase()}-$tag")
        }}
        listOf("en","es","zh").forEach{tag->compose.runOnIdle{theme=ThemeMode.LIGHT;language=tag;scale=1.5f};capture("budgets-font150-$tag")}
        compose.runOnIdle{language="es";scale=1f;state=PlanningPreviewData.state("plans")};capture("plans")
        compose.runOnIdle{state=PlanningPreviewData.state(hidden=true)};capture("private")
        compose.runOnIdle{state=PlanningPreviewData.state().copy(controls=PlanningControls(budget=BudgetForm(amount="25000",space=2)),canSave=true)};capture("budget-editor")
        compose.runOnIdle{state=PlanningPreviewData.state().copy(controls=PlanningControls(plan=PlanForm(name="Plan de datos",amount="32000",space=2)),canSave=true)};capture("plan-editor")
        compose.runOnIdle{state=PlanningPreviewData.state().copy(controls=PlanningControls(settlement=SettlementForm("data",java.time.LocalDate.now().toString(),1,"Plan de datos","COP",com.trackermaster.core.domain.money.MoneyDirection.EXPENSE,"32000",1,null,4,space=2)),canSave=true)};capture("confirm")
    }
}
