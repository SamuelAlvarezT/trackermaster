package com.trackermaster.feature.expense

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.*
import com.trackermaster.core.data.money.ReceiptSource
import com.trackermaster.core.domain.model.ThemeMode
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val dispatcher=UnconfinedTestDispatcher()
    private var store:ViewModelStore?=null
    @Before fun before(){Dispatchers.setMain(dispatcher);compose.activity.actionBar?.hide()}
    @After fun after(){store?.clear();Dispatchers.resetMain()}
    private fun settled(){dispatcher.scheduler.advanceUntilIdle();compose.waitForIdle()}
    private fun render(gateway:FakeReceiptGateway):ReceiptViewModel {
        val owner=ViewModelStore().also{store=it}
        val vm=ViewModelProvider(owner,object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=ReceiptViewModel(gateway,SavedStateHandle(),dispatcher) as T
        })[ReceiptViewModel::class.java]
        vm.receive(ReceiptSource.SharedText("proof"));dispatcher.scheduler.advanceUntilIdle()
        compose.setContent{val state by vm.state.collectAsState();MoneyLocale("en"){TrackermasterTheme(ThemeMode.LIGHT,0){ReceiptScreen(state,vm::onEvent,{},{})}}}
        settled();return vm
    }
    private fun scroll(tag:String){compose.onNodeWithTag("receipt_content").performScrollToNode(hasTestTag(tag))}
    @Test fun receiptContentWaitsForStoredSettingsAndBiometricAuthentication() {
        var state by mutableStateOf(ReceiptPreviewData.review().copy(loading=true))
        var authenticated by mutableStateOf(false)
        val locked=compose.activity.getString(R.string.r_auth_title)
        val failed=compose.activity.getString(R.string.r_error_read)
        compose.setContent{TrackermasterTheme(ThemeMode.LIGHT,0){ReceiptAccess(state,authenticated,{Text(locked)},failure={Text(failed)}) {ReceiptScreen(state,{},{},{})}}}
        compose.onNodeWithTag("receipt_content").assertDoesNotExist()
        compose.runOnIdle{state=state.copy(loading=false,settings=state.settings.copy(biometricEnabled=true))}
        compose.onNodeWithText(locked).assertExists();compose.onNodeWithTag("receipt_content").assertDoesNotExist()
        compose.runOnIdle{authenticated=true}
        compose.onNodeWithTag("receipt_content").assertExists();compose.onNodeWithText(locked).assertDoesNotExist()
        compose.runOnIdle{state=state.copy(workspace=null)}
        compose.onNodeWithTag("receipt_content").assertDoesNotExist();compose.onNodeWithText(failed).assertExists()
    }
    @Test fun unknownTypeRequiresUserChoiceFundingCategoryAndConfirmation() {
        val gateway=FakeReceiptGateway().apply{text="Nequi\nTransferencia exitosa\nMonto COP 50.000,00\nFecha 04/10/2026"}
        val vm=render(gateway)
        compose.onNodeWithTag("receipt_save").assertIsNotEnabled()
        scroll("receipt_kind:EXPENSE");compose.onNodeWithTag("receipt_kind:EXPENSE").performClick();settled()
        compose.onNodeWithTag("receipt_content").performScrollToNode(hasText("Choose an account"))
        compose.onNodeWithText("Choose an account").performClick();compose.onNodeWithText("Nequi").performClick();settled()
        compose.onNodeWithTag("receipt_content").performScrollToNode(hasText("Choose a category"))
        compose.onNodeWithText("Choose a category").performClick();compose.onNodeWithText("Food").performClick();settled()
        compose.onNodeWithTag("receipt_save").assertIsNotEnabled()
        scroll("receipt_confirm");compose.onNodeWithTag("receipt_confirm").performClick();settled()
        compose.onNodeWithTag("receipt_save").assertIsEnabled().performClick();settled()
        assertTrue(vm.state.value.saved);assertEquals(1,gateway.confirmed.size)
        assertEquals(ReceiptEntryKind.EXPENSE,gateway.confirmed.single().second.kind)
    }
    @Test fun duplicateWarningNeedsVisibleAcknowledgementBeforeSave() {
        val gateway=FakeReceiptGateway().apply{matches=listOf(ReceiptDuplicate(1,ReceiptDuplicateReason.FILE))}
        val vm=render(gateway)
        compose.runOnIdle{vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(accountId=1,categoryId=1,confirmed=true)))};settled()
        compose.onNodeWithTag("receipt_save").assertIsNotEnabled()
        scroll("receipt_duplicate_ack");compose.onNodeWithTag("receipt_duplicate_ack").performClick();settled()
        compose.onNodeWithTag("receipt_save").assertIsEnabled().performClick();settled()
        assertEquals(gateway.matches.fingerprint(),gateway.confirmed.single().second.duplicateAcknowledgement)
    }
    @Test fun pendingReceiptCannotBeConfirmedFromUi() {
        val vm=render(FakeReceiptGateway().apply{text="Nequi\nPendiente\nMonto COP 50.000,00\nFecha 04/10/2026"})
        compose.runOnIdle{vm.onEvent(ReceiptEvent.Update(vm.state.value.form.copy(kind=ReceiptEntryKind.EXPENSE,accountId=1,categoryId=1)))};settled()
        scroll("receipt_confirm");compose.onNodeWithTag("receipt_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("receipt_save").assertIsNotEnabled()
    }
    @Test fun privacyRemovesExtractedSourceTextAndOriginalViewerAction() {
        val gateway=FakeReceiptGateway();val vm=render(gateway)
        compose.onNodeWithText("Show extracted text").performClick();settled()
        compose.onNodeWithText(gateway.text).assertExists()
        compose.onNodeWithContentDescription("Hide amounts").performClick();settled()
        compose.onNodeWithText(gateway.text).assertDoesNotExist()
        compose.onNodeWithText("View original receipt").assertDoesNotExist()
        assertTrue(vm.state.value.hidden)
    }
    @Test fun nativeReceiptScreensSupportAllThemesLanguagesAndLargeFonts() {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var language by mutableStateOf("en")
        var scale by mutableStateOf(1f)
        var state by mutableStateOf(ReceiptPreviewData.review())
        compose.setContent {
            val base=LocalContext.current;val density=LocalDensity.current
            val config=remember(language,scale){Configuration(base.resources.configuration).apply{setLocale(Locale.forLanguageTag(language));fontScale=scale}}
            val context=remember(language,scale){base.createConfigurationContext(config)}
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,LocalDensity provides Density(density.density,scale)) {
                key(theme,language,scale,state){MoneyLocale(language){TrackermasterTheme(theme,0){ReceiptScreen(state,{},{},{})}}}
            }
        }
        val directory=File(requireNotNull(System.getProperty("money.screenshots")),"receipts").apply{mkdirs()}
        fun capture(name:String) {
            compose.waitForIdle();lateinit var bitmap:Bitmap
            compose.runOnIdle {val view=compose.activity.window.decorView;bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))}
            File(directory,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        }
        ThemeMode.entries.forEach{mode->listOf("en","es","zh").forEach{tag->
            compose.runOnIdle{theme=mode;language=tag}
            val config=Configuration(compose.activity.resources.configuration).apply{setLocale(Locale.forLanguageTag(tag))}
            compose.onNodeWithText(compose.activity.createConfigurationContext(config).getString(R.string.r_title)).assertExists()
            capture("review-${mode.name.lowercase()}-$tag")
        }}
        listOf("en","es","zh").forEach {tag->compose.runOnIdle{theme=ThemeMode.LIGHT;language=tag;scale=1.5f};capture("review-font150-$tag")}
        compose.runOnIdle{scale=1f;language="es";state=ReceiptUiState(loading=false)};capture("inbox")
        compose.runOnIdle{state=ReceiptPreviewData.review().copy(hidden=true)};capture("private")
        compose.runOnIdle{state=ReceiptPreviewData.review().copy(analysis=ReceiptAnalysis(status=ReceiptBankStatus.FAILED))};capture("failed")
    }
}
