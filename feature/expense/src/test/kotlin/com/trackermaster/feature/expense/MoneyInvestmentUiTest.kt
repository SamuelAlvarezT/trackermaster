package com.trackermaster.feature.expense

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.domain.money.InvestmentEventKind
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
class MoneyInvestmentUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val dispatcher=UnconfinedTestDispatcher()
    private var store:ViewModelStore?=null
    @Before fun before(){Dispatchers.setMain(dispatcher);compose.activity.actionBar?.hide()}
    @After fun after(){store?.clear();Dispatchers.resetMain()}
    private fun render(g:FakeInvestmentGateway):MoneyInvestmentViewModel {
        val owner=ViewModelStore().also{store=it}
        val vm=ViewModelProvider(owner,object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):T=MoneyInvestmentViewModel(g,SavedStateHandle(),dispatcher) as T})[MoneyInvestmentViewModel::class.java]
        compose.setContent{val state by vm.state.collectAsState();MoneyLocale("en"){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyInvestmentScreen(state,vm::onAction,{})}}}
        return vm
    }
    @Test fun createInvestmentFromTheVisibleAction(){val g=FakeInvestmentGateway();render(g)
        compose.onNodeWithText("Add investment").performScrollTo().performClick()
        compose.onNodeWithTag("investment_save").assertIsNotEnabled()
        compose.onNodeWithTag("investment_name").performTextInput("Work and Travel fund")
        compose.onNodeWithTag("investment_save").assertIsEnabled().performClick();compose.waitForIdle()
        assertEquals("Work and Travel fund",g.saves.single().name);assertNull(g.saves.single().kind)
    }
    @Test fun contributionRequiresAmountAndSavesSelectedFunding(){val g=FakeInvestmentGateway();render(g)
        compose.onNodeWithText("View details").performScrollTo().performClick()
        compose.onNodeWithText("Contribute").performScrollTo().performClick()
        compose.onNodeWithTag("investment_amount").performScrollTo().performTextInput("5000")
        compose.onNodeWithTag("investment_save").assertIsEnabled().performClick();compose.waitForIdle()
        assertEquals("5000",g.saves.single().amount);assertEquals(1L,g.saves.single().account)
    }
    @Test fun reportsDisableSharingWhenPrivacyIsEnabled(){val state=MoneyPreviewData.state(MoneyDestination.REPORTS,true)
        compose.setContent{MoneyLocale("en"){TrackermasterTheme(ThemeMode.LIGHT,0){MoneyReportsScreen(state){}}}}
        compose.onNodeWithTag("report_csv").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("report_pdf").performScrollTo().assertIsNotEnabled()
    }
    @Test fun privacyAlsoMasksOpeningAmountsWhileEditing(){val g=FakeInvestmentGateway();g.preferences.value=g.preferences.value.copy(hideAmounts=true);val vm=render(g)
        compose.runOnIdle{vm.onAction(InvestmentAction.Edit(g.book.value.positions.single()))}
        val password=SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password)
        compose.onNodeWithTag("investment_amount").performScrollTo().assert(password)
        compose.onNodeWithTag("investment_cost").performScrollTo().assert(password)
        compose.onNodeWithTag("investment_privacy").performClick()
        compose.onNodeWithTag("investment_amount").performScrollTo().assert(!password)
    }
    @Test fun nativeInvestmentAndReportScreensAcrossThemesAndLanguages(){
        var theme by mutableStateOf(ThemeMode.LIGHT);var language by mutableStateOf("es");var scale by mutableStateOf(1f)
        var privateMode by mutableStateOf(false);var reports by mutableStateOf(false);var editor by mutableStateOf(false)
        var operation by mutableStateOf<InvestmentEventKind?>(null)
        compose.setContent{val base=LocalContext.current;val density=LocalDensity.current
            val config=remember(language,scale){Configuration(base.resources.configuration).apply{setLocale(Locale.forLanguageTag(language));fontScale=scale}}
            val context=remember(language,scale){base.createConfigurationContext(config)}
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,LocalDensity provides Density(density.density,scale)){
                key(theme,language,scale,privateMode,reports,editor,operation){MoneyLocale(language){TrackermasterTheme(theme,0){
                    if(reports)MoneyReportsScreen(MoneyPreviewData.state(MoneyDestination.REPORTS,privateMode)){}
                    else MoneyInvestmentScreen(InvestmentPreviewData.state(privateMode).let{if(editor)it.copy(draft=InvestmentDraft(id="fund",kind=operation,name="Mi inversión",amount="50000",cost="45000",account=1,category=6),canSave=true)else it},{},{})
                }}}
            }
        }
        val directory=File(requireNotNull(System.getProperty("money.screenshots")),"investments").apply{mkdirs()}
        fun capture(name:String){compose.waitForIdle();lateinit var bitmap:Bitmap
            compose.runOnIdle{val view=compose.activity.window.decorView;bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))}
            File(directory,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        }
        ThemeMode.entries.forEach{mode->listOf("en","es","zh").forEach{tag->compose.runOnIdle{theme=mode;language=tag};capture("investments-${mode.name.lowercase()}-$tag")}}
        listOf("en","es","zh").forEach{tag->compose.runOnIdle{theme=ThemeMode.LIGHT;language=tag;scale=1.5f};compose.onNodeWithTag("investment_privacy").assertIsDisplayed();capture("investments-font150-$tag");compose.onNodeWithTag("investment_list").performScrollToNode(hasTestTag("investment_card:fund"));capture("investment-card-font150-$tag")}
        compose.runOnIdle{language="es";scale=1f;privateMode=true};capture("investments-private")
        compose.runOnIdle{privateMode=false;editor=true};capture("investment-editor")
        listOf("en","es","zh").forEach{tag->listOf(InvestmentEventKind.DIVIDEND,InvestmentEventKind.VALUATION).forEach{kind->
            compose.runOnIdle{language=tag;scale=1.5f;operation=kind};compose.onNodeWithTag("investment_privacy").assertIsDisplayed();capture("editor-${kind.name.lowercase()}-font150-$tag")
        }}
        listOf("en","es","zh").forEach{tag->compose.runOnIdle{editor=false;reports=true;language=tag;scale=1f;operation=null};compose.onNodeWithTag("report_csv").assertIsEnabled();capture("reports-$tag")}
    }
}
