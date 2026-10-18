package com.trackermaster.feature.expense

import androidx.compose.runtime.*
import android.content.res.Configuration
import android.annotation.SuppressLint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExpenseScreen(vm: MoneyViewModel = hiltViewModel(),quickEntry: String?=null,quickToken: String="",onQuickConsumed: ()->Unit={}) {
    MoneyEffects(vm)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale by vm.localeTag.collectAsStateWithLifecycle()
    LaunchedEffect(quickEntry,quickToken,state.load) {
        if(quickEntry!=null && vm.openFromWidget(quickToken,quickEntry)) onQuickConsumed()
    }
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
@SuppressLint("AppBundleLocaleChanges") // All app languages are bundled; no language splits.
private fun MoneyEffects(vm: MoneyViewModel) {
    val context = LocalContext.current
    LaunchedEffect(vm,context) { vm.effects.collect { effect -> when(effect) {
        is MoneyEffect.ShareReport -> try {
            if(!vm.uiState.value.preferences.hideAmounts) {
                val file = MoneyReportExporter.file(context,effect.filename)
                val uri = androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.money.receipts",file)
                val share = moneyReportShareIntent(uri,effect.pdf,file.name)
                val config = Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(vm.localeTag.value)) }
                context.startActivity(Intent.createChooser(share,context.createConfigurationContext(config).getString(R.string.x_share)))
            }
        } catch(_:Exception) { vm.onEvent(MoneyEvent.ExportFailed) }
        is MoneyEffect.Investments -> context.startActivity(Intent(context,MoneyInvestmentActivity::class.java)
            .putExtra("investment_currency",effect.currency).putExtra("investment_space",effect.space?:0L))
        is MoneyEffect.Commitments -> context.startActivity(Intent(context,MoneyCommitmentActivity::class.java)
            .putExtra("commitment_tab",effect.tab).putExtra("commitment_currency",effect.currency).putExtra("commitment_space",effect.space?:0L))
        is MoneyEffect.Planning -> context.startActivity(Intent(context,MoneyPlanningActivity::class.java)
            .putExtra("planning_tab",effect.tab).putExtra("planning_budget",effect.budgetId?:0L)
            .putExtra("planning_currency",effect.currency).putExtra("planning_space",effect.space?:0L))
        MoneyEffect.ImportReceipt -> context.startActivity(Intent(context,MoneyReceiptActivity::class.java))
        is MoneyEffect.ViewReceipt -> ReceiptViewer.open(context,effect.path,vm.localeTag.value)
    } } }
}

/** Share one content URI, with temporary read access and no write permission. */
internal fun moneyReportShareIntent(uri: android.net.Uri, pdf: Boolean, filename: String): Intent {
    require(uri.scheme == "content")
    return Intent(Intent.ACTION_SEND).setType(if(pdf) "application/pdf" else "text/csv")
        .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
            clipData = android.content.ClipData.newRawUri(filename,uri)
        }
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
