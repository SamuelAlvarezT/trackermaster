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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoneyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dispatcher = UnconfinedTestDispatcher()
    private var store: ViewModelStore? = null
    @Before fun before() { Dispatchers.setMain(dispatcher); compose.activity.actionBar?.hide() }
    @After fun after() { store?.clear(); Dispatchers.resetMain() }
    private fun render(gateway: FakeMoneyGateway = FakeMoneyGateway()): MoneyViewModel {
        val owner = ViewModelStore().also { store = it }
        val vm = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MoneyViewModel(gateway, SavedStateHandle(), dispatcher) as T
        })[MoneyViewModel::class.java]
        compose.setContent { TrackermasterTheme(ThemeMode.LIGHT, 0) {
            val state by vm.uiState.collectAsState(); val language by vm.localeTag.collectAsState()
            MoneyLocale(language) { MoneyContent(state, vm::onEvent) }
        } }
        compose.waitForIdle()
        return vm
    }
    @Test fun customKeypadSavesExactlyOneExpenseAndUpdatesBalance() {
        val gateway = FakeMoneyGateway(); val vm = render(gateway); val before = vm.uiState.value.overview.balance.minorUnits
        compose.onNodeWithTag("money_add_expense").performClick()
        compose.onNodeWithTag("money_save_entry").assertIsNotEnabled()
        listOf("5", "0", "0", "0").forEach { compose.onNodeWithTag("money_key:$it").performClick() }
        compose.onNodeWithTag("money_save_entry").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, gateway.recorded.size); assertEquals(500000L, gateway.recorded.single().amount.minorUnits)
        assertEquals(before - 500000, vm.uiState.value.overview.balance.minorUnits)
        compose.onNodeWithTag("money_amount_input").assertDoesNotExist()
    }
    @Test fun privacyRemovesExactBalanceFromSemanticsImmediately() {
        val vm = render()
        val before = vm.uiState.value.overview.balance.format(Locale.ENGLISH)
        compose.onAllNodes(hasText(before) or hasContentDescription(before)).assertCountEquals(1)
        compose.onNodeWithTag("money_privacy").performClick()
        compose.onAllNodes(hasText(before) or hasContentDescription(before)).assertCountEquals(0)
        assertTrue(vm.uiState.value.preferences.hideAmounts)
        compose.onNodeWithTag("money_privacy").performClick()
        compose.onAllNodes(hasText(before) or hasContentDescription(before)).assertCountEquals(1)
    }
    @Test fun numericFieldAcceptsHardwareKeyboardWithOwnKeypadVisible() {
        val vm = render()
        compose.onNodeWithTag("money_add_expense").performClick()
        compose.onNodeWithTag("money_amount_input").performClick().performKeyInput {
            pressKey(Key.Five); pressKey(Key.Zero); pressKey(Key.Zero); pressKey(Key.Zero)
        }
        compose.onNodeWithTag("money_keypad").assertExists()
        compose.onNodeWithTag("money_save_entry").assertIsEnabled()
        compose.runOnIdle { assertEquals("5000", vm.uiState.value.controls.editor!!.amount) }
    }
    @Test fun historySearchAndMenuDeleteUndoExerciseUdf() {
        val gateway = FakeMoneyGateway(); val vm = render(gateway)
        compose.runOnIdle { vm.onEvent(MoneyEvent.Navigate(MoneyDestination.MOVEMENTS)) }
        compose.onNodeWithTag("money_search").performTextInput("Almuerzo")
        compose.waitForIdle(); assertEquals(1, vm.uiState.value.history.groups.flatMap { it.rows }.size)
        compose.onNodeWithContentDescription("Transaction options").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle(); assertTrue(gateway.data.value.ledger.movements.any { it.id == 1L })
    }
    @Test fun sectionsSettingsAndAccountNavigationKeepScope() {
        val vm = render()
        compose.runOnIdle { vm.onEvent(MoneyEvent.Appearance(true, true)) }
        compose.onNodeWithTag("money_sections").assertExists()
        compose.runOnIdle { vm.onEvent(MoneyEvent.Navigate(MoneyDestination.ACCOUNTS)) }
        compose.onNodeWithContentDescription("Overview").performClick()
        compose.onNodeWithTag("money_sections").assertExists()
    }
    @Test fun existingMovementCanBeEditedFromItsMenu() {
        val gateway = FakeMoneyGateway(); val vm = render(gateway)
        compose.runOnIdle { vm.onEvent(MoneyEvent.Navigate(MoneyDestination.MOVEMENTS)) }
        compose.onNodeWithTag("money_search").performTextInput("Almuerzo")
        compose.onNodeWithContentDescription("Transaction options").performClick()
        compose.onNodeWithText("Edit").performClick()
        // Edit through the same keypad used in the app; numeric fields suppress the system IME.
        repeat(vm.uiState.value.controls.editor!!.amount.length) {
            compose.onNodeWithTag("money_key:delete").performClick()
        }
        "20000".forEach { compose.onNodeWithTag("money_key:$it").performClick() }
        compose.onNodeWithTag("money_save_entry").performClick()
        compose.waitForIdle()
        assertEquals(2000000L, gateway.data.value.ledger.movements.single { it.id == 1L }.amount.minorUnits)
        assertEquals("Almuerzo", gateway.data.value.ledger.movements.single { it.id == 1L }.note)
        assertEquals(7, gateway.data.value.ledger.movements.size)
    }
    @Test fun fxKeypadEditsReceivedAmountAndRequiresExplicitUsdValue() {
        val gateway = FakeMoneyGateway(); val vm = render(gateway)
        compose.runOnIdle { vm.onEvent(MoneyEvent.OpenEntry(MoneyEntryKind.TRANSFER)) }
        compose.runOnIdle { vm.onEvent(MoneyEvent.Editor(vm.uiState.value.controls.editor!!.copy(targetAccountId = 3, amount = "4000"))) }
        compose.onNodeWithTag("money_save_entry").assertIsNotEnabled()
        compose.onNodeWithTag("money_received_input").performScrollTo().performClick()
        compose.onNodeWithTag("money_key:1").performClick()
        compose.runOnIdle {
            assertEquals("4000", vm.uiState.value.controls.editor!!.amount)
            assertEquals("1", vm.uiState.value.controls.editor!!.receivedAmount)
        }
        compose.onNodeWithTag("money_save_entry").assertIsEnabled().performClick()
        compose.waitForIdle()
        val pair = gateway.data.value.ledger.movements.takeLast(2)
        assertEquals("COP", pair[0].amount.currencyCode); assertEquals(400000L, pair[0].amount.minorUnits)
        assertEquals("USD", pair[1].amount.currencyCode); assertEquals(100L, pair[1].amount.minorUnits)
        assertNull(vm.uiState.value.controls.editor)
    }
    @Test fun restoredDraftDoesNotCoverReadErrorOrItsRetryAction() {
        val state = MoneyPreviewData.state().let { it.copy(load = MoneyLoadState.ERROR,
            controls = it.controls.copy(editor = MoneyEditorUiState(requestId = "restored", amount = "100", accountId = 3))) }
        var retry = false
        compose.setContent { TrackermasterTheme(ThemeMode.LIGHT, 0) { MoneyContent(state) { if (it == MoneyEvent.Retry) retry = true } } }
        compose.onNodeWithTag("money_amount_input").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retry)
        assertEquals("restored", state.controls.editor!!.requestId)
    }
    @Test fun nativeScreensRenderInAllThemesAndLanguages() {
        var destination by mutableStateOf(MoneyDestination.SUMMARY)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var language by mutableStateOf("es")
        var privateMode by mutableStateOf(false)
        var editor by mutableStateOf(false)
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            val baseContext = LocalContext.current
            val density = LocalDensity.current
            val configuration = remember(language, fontScale) { Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language)); this.fontScale = fontScale
            } }
            val context = remember(language, fontScale) { baseContext.createConfigurationContext(configuration) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale)) {
                key(language, mode, fontScale, editor, destination) { MoneyLocale(language) { TrackermasterTheme(mode, 0) {
                    if (editor) MoneyEditorContent(MoneyPresenter().build(MoneyPreviewData.workspace(),
                        MoneyControls(editor = MoneyEditorUiState(requestId = "capture", accountId = 1, categoryId = 1, amount = "18900")))) {}
                    else MoneyContent(MoneyPreviewData.state(destination, privateMode)) {}
                } } }
            }
        }
        val directory = File(requireNotNull(System.getProperty("money.screenshots"))).apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            // Robolectric has no hardware window frame for PixelCopy. Render the actual
            // Android view hierarchy with its native Skia Canvas instead.
            lateinit var bitmap: Bitmap
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
            }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue(bitmap.width > 300); assertTrue(bitmap.height > 600)
        }
        ThemeMode.entries.forEach { theme -> listOf("en", "es", "zh").forEach { locale ->
            compose.runOnIdle { mode = theme; language = locale; destination = MoneyDestination.SUMMARY }
            val configuration = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) }
            val title = compose.activity.createConfigurationContext(configuration).getString(R.string.m_title)
            compose.onNodeWithText(title).assertExists()
            capture("summary-${theme.name.lowercase()}-$locale")
        } }
        compose.runOnIdle { mode = ThemeMode.LIGHT; language = "es" }
        listOf(MoneyDestination.MOVEMENTS, MoneyDestination.ACCOUNTS, MoneyDestination.REPORTS, MoneyDestination.BUDGETS).forEach {
            compose.runOnIdle { destination = it }; capture(it.name.lowercase())
        }
        compose.runOnIdle { privateMode = true; destination = MoneyDestination.SUMMARY }; capture("privacy")
        compose.runOnIdle { privateMode = false; editor = true }; capture("editor")
        compose.runOnIdle { fontScale = 1.5f }; capture("editor-font150")
        compose.runOnIdle { editor = false }; capture("summary-font150")
    }
}
