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
import com.trackermaster.core.domain.money.*
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
class MoneyCommitmentUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val dispatcher=UnconfinedTestDispatcher()
    private var store:ViewModelStore?=null
    @Before fun before(){Dispatchers.setMain(dispatcher);compose.activity.actionBar?.hide()}
    @After fun after(){store?.clear();Dispatchers.resetMain()}
    private fun render(g:FakeCommitmentGateway):MoneyCommitmentViewModel {
        val owner=ViewModelStore().also{store=it}
        val vm=ViewModelProvider(owner,object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):T=MoneyCommitmentViewModel(g,SavedStateHandle(),dispatcher) as T
        })[MoneyCommitmentViewModel::class.java]
        compose.setContent{val state by vm.state.collectAsState();MoneyLocale("en"){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyCommitmentScreen(state,vm::onEvent,{})}}}
        return vm
    }
    @Test fun createExistingDebtRequiresFieldsAndDoesNotDisburse(){val g=FakeCommitmentGateway();render(g)
        compose.onNodeWithText("Add debt").performScrollTo().performClick()
        compose.onNodeWithTag("commitment_save").assertIsNotEnabled()
        compose.onNodeWithTag("commitment_name").performTextInput("Sister")
        compose.onNodeWithTag("commitment_amount").performTextInput("2500")
        compose.onNodeWithTag("commitment_save").assertIsEnabled().performClick()
        compose.waitForIdle();assertEquals("Sister",g.debts.single().first.person);assertNull(g.debts.single().second)
    }
    @Test fun repaymentCanLinkReceiptAndLocksFieldsBeforeSaving(){val g=FakeCommitmentGateway();val vm=render(g)
        val tx=g.workspace.value.ledger.movements.first();val d=g.commitments.value.debts.first{it.id=="friend"}.copy(kind=DebtKind.PAYABLE)
        compose.runOnIdle{g.commitments.value=g.commitments.value.copy(debts=listOf(d));vm.onEvent(CommitmentEvent.Repay(d));vm.onEvent(CommitmentEvent.Draft(vm.state.value.controls.draft!!.copy(amount=tx.amount.decimal.toPlainString())))}
        compose.onNodeWithText("Link this movement").performScrollTo().performClick()
        compose.onNodeWithTag("commitment_amount").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("commitment_save").performClick();compose.waitForIdle()
        assertEquals(tx.id,g.repayments.single().first.linked)
    }
    @Test fun goalContributionUsesAllocationAction(){val g=FakeCommitmentGateway();val vm=render(g)
        compose.runOnIdle{vm.onEvent(CommitmentEvent.Tab("goals"))}
        compose.onNodeWithText("Contribute").performScrollTo().performClick()
        compose.onNodeWithTag("commitment_amount").performTextInput("5000")
        compose.onNodeWithTag("commitment_save").assertIsEnabled().performClick();compose.waitForIdle()
        assertEquals(500000L,g.allocations.single().second.minorUnits);assertFalse(g.allocations.single().first.withdraw)
    }
    @Test fun privacyHidesGoalAmountsAndProgressSemantics(){val g=FakeCommitmentGateway();val vm=render(g)
        compose.runOnIdle{vm.onEvent(CommitmentEvent.Tab("goals"));vm.onEvent(CommitmentEvent.Privacy)}
        compose.onNodeWithTag("goal:travel").performScrollTo()
        compose.onAllNodesWithText("Amount hidden",substring=true).onFirst().assertExists()
        compose.onAllNodes(hasText("of target reserved",substring=true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("of target reserved",substring=true)).assertCountEquals(0)
    }
    @Test fun nativeScreensForThemesLanguagesAndLargeFonts(){
        var theme by mutableStateOf(ThemeMode.LIGHT);var language by mutableStateOf("es");var scale by mutableStateOf(1f)
        var state by mutableStateOf(CommitmentPreviewData.state("goals"))
        compose.setContent{val base=LocalContext.current;val density=LocalDensity.current
            val config=remember(language,scale){Configuration(base.resources.configuration).apply{setLocale(Locale.forLanguageTag(language));fontScale=scale}}
            val context=remember(language,scale){base.createConfigurationContext(config)}
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,LocalDensity provides Density(density.density,scale)){
                key(theme,language,scale,state){MoneyLocale(language){TrackermasterTheme(theme,0){MoneyCommitmentScreen(state,{},{})}}}
            }
        }
        val directory=File(requireNotNull(System.getProperty("money.screenshots")),"commitments").apply{mkdirs()}
        fun capture(name:String){compose.waitForIdle();lateinit var bitmap:Bitmap
            compose.runOnIdle{val view=compose.activity.window.decorView;bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))}
            File(directory,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        }
        ThemeMode.entries.forEach{mode->listOf("en","es","zh").forEach{tag->compose.runOnIdle{theme=mode;language=tag};capture("goals-${mode.name.lowercase()}-$tag")}}
        listOf("en","es","zh").forEach{tag->
            compose.runOnIdle{theme=ThemeMode.LIGHT;language=tag;scale=1.5f}
            compose.onNodeWithTag("commitment_privacy").assertIsDisplayed()
            compose.onNodeWithTag("commitment_notifications").assertIsDisplayed()
            capture("goals-font150-$tag")
            compose.onNodeWithTag("commitment_list").performScrollToNode(hasTestTag("goal:travel"))
            capture("goal-card-font150-$tag")
        }
        compose.runOnIdle{language="es";scale=1f;state=CommitmentPreviewData.state()};capture("debts")
        compose.onNodeWithTag("debt:friend").performScrollTo();capture("debt-card")
        compose.runOnIdle{state=CommitmentPreviewData.state("goals",true)};capture("private")
        listOf(CommitmentEditor.DEBT,CommitmentEditor.GOAL,CommitmentEditor.REPAYMENT,CommitmentEditor.ALLOCATION).forEach{editor->
            compose.runOnIdle{state=CommitmentPreviewData.state().copy(controls=CommitmentControls(draft=CommitmentDraft(editor,name="Mi viaje",amount="50000",account=1)),canSave=true)};capture("editor-${editor.name.lowercase()}")
        }
    }
}
