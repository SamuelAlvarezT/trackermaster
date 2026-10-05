package com.trackermaster.feature.expense

import androidx.compose.runtime.*
import android.content.res.Configuration
import android.annotation.SuppressLint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Retains the existing app route without modifying MainActivity.
@Composable
fun ExpenseScreen(vm: MoneyViewModel = hiltViewModel()) {
    MoneyEffects(vm)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale by vm.localeTag.collectAsStateWithLifecycle()
    MoneyLocale(locale) { MoneyContent(state, vm::onEvent) }
}

@Composable
fun ExpenseReportsScreen(vm: MoneyViewModel = hiltViewModel()) {
    MoneyEffects(vm)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale by vm.localeTag.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.onEvent(MoneyEvent.Navigate(MoneyDestination.REPORTS)) }
    MoneyLocale(locale) { MoneyContent(state, vm::onEvent) }
}

@Composable
private fun MoneyEffects(vm: MoneyViewModel) {
    val context = LocalContext.current
    LaunchedEffect(vm,context) { vm.effects.collect { effect -> when(effect) {
        MoneyEffect.ImportReceipt -> context.startActivity(Intent(context,MoneyReceiptActivity::class.java))
        is MoneyEffect.ViewReceipt -> ReceiptViewer.open(context,effect.path,vm.localeTag.value)
    } } }
}

/** App-selected language also works on API 26–32 without changing the host Activity. */
// The app packages all languages with bundle.language.enableSplit=false.
@SuppressLint("AppBundleLocaleChanges")
@Composable
internal fun MoneyLocale(tag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val current = LocalConfiguration.current
    val configuration = remember(current, tag) { Configuration(current).apply {
        setLocale(java.util.Locale.forLanguageTag(tag))
    } }
    val context = remember(base, configuration) { base.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration, content = content)
}
