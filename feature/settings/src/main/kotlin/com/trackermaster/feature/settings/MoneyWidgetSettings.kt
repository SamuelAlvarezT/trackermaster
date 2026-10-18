package com.trackermaster.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.data.money.*
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.R as P8R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoneyWidgetSettingsState(val preferences: MoneyPreferences=MoneyPreferences(),
    val accounts: List<MoneyAccount> = emptyList(),val spaces: List<MoneySpace> = emptyList(),val ready: Boolean=false,val error: Boolean=false)

@HiltViewModel
class MoneyWidgetSettingsViewModel @Inject constructor(private val preferences: MoneyPreferencesRepository,
    money: MoneyRepository): ViewModel() {
    private val error=MutableStateFlow(false)
    val state=combine(preferences.preferences,money.observeWorkspace(),error) { prefs,data,failed ->
        MoneyWidgetSettingsState(prefs,data.ledger.snapshot.accounts.map { it.account },data.spaces,true,failed)
    }.catch { emit(MoneyWidgetSettingsState(error=true)) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),MoneyWidgetSettingsState())
    fun select(currency: String?,space: Long?,account: Long?)=viewModelScope.launch {
        try { preferences.setWidgetSelection(currency,space,account);error.value=false }
        catch(_: Exception) { error.value=true }
    }
}

@Composable
fun MoneyWidgetSettingsSection(vm: MoneyWidgetSettingsViewModel=hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val prefs=state.preferences
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(P8R.string.p8_widget_settings),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(P8R.string.p8_widget_settings_help),style=MaterialTheme.typography.bodySmall)
            if(!state.ready && !state.error) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.ready) {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf<String?>(null,"COP","USD").forEach { code ->
                        FilterChip(prefs.widgetCurrency==code,onClick={vm.select(code,prefs.widgetSpaceId,prefs.widgetAccountId)},
                            label={Text(code ?: stringResource(P8R.string.p8_widget_all))})
                    }
                }
                Box {
                    OutlinedButton(onClick={open=true}) {
                        Text(when {
                            prefs.widgetAccountId!=null->state.accounts.find { it.id==prefs.widgetAccountId }?.name ?: stringResource(P8R.string.p8_widget_missing)
                            prefs.widgetSpaceId!=null->state.spaces.find { it.id==prefs.widgetSpaceId }?.let { widgetSpaceName(it) } ?: stringResource(P8R.string.p8_widget_missing)
                            else->stringResource(P8R.string.p8_widget_all_spaces)
                        })
                    }
                    DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                        DropdownMenuItem(text={Text(stringResource(P8R.string.p8_widget_all_spaces))},onClick={vm.select(prefs.widgetCurrency,null,null);open=false})
                        state.spaces.forEach { space -> DropdownMenuItem(text={Text(widgetSpaceName(space))},onClick={vm.select(prefs.widgetCurrency,space.id,null);open=false}) }
                        HorizontalDivider()
                        state.accounts.forEach { account -> DropdownMenuItem(text={Text(account.name+" · "+account.initialBalance.currencyCode)},onClick={vm.select(prefs.widgetCurrency,null,account.id);open=false}) }
                    }
                }
            }
            if(state.error) Text(stringResource(P8R.string.p8_widget_settings_error),color=MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun widgetSpaceName(space: MoneySpace): String=when(space.kind) {
    MoneySpaceKind.PERSONAL->stringResource(P8R.string.p8_widget_personal)
    MoneySpaceKind.BUSINESS->stringResource(P8R.string.p8_widget_business)
    MoneySpaceKind.SAVINGS->stringResource(P8R.string.p8_widget_savings)
    MoneySpaceKind.TRAVEL->stringResource(P8R.string.p8_widget_wyt)
    MoneySpaceKind.UNASSIGNED->stringResource(P8R.string.p8_widget_unassigned)
    MoneySpaceKind.CUSTOM->space.name
}
