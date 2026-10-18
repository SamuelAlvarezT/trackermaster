package com.trackermaster.feature.expense

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.ui.theme.TrackermasterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MoneyInvestmentActivity:FragmentActivity(){
    private val model by viewModels<MoneyInvestmentViewModel>()
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);actionBar?.hide()
        if(savedInstanceState==null) model.start(intent.getStringExtra("investment_currency").orEmpty(),intent.getLongExtra("investment_space",0).takeIf{it>0})
        setContent{
            val state by model.state.collectAsStateWithLifecycle()
            var authenticated by remember{mutableStateOf(false)}
            MoneyLocale(state.settings.localeTag){TrackermasterTheme(state.settings.themeMode,state.settings.accentIndex){
                BackHandler{if(!state.saving){if(state.settings.biometricEnabled && !authenticated)finish()else if(state.draft!=null || state.selected!=null)model.onAction(InvestmentAction.Close)else finish()}}
                MoneyPrivateAccess(state.loading,!state.loadError && state.workspace!=null,state.settings.biometricEnabled,authenticated,
                    gate={ReceiptAuthentication(this,{authenticated=true},{finish()},R.string.i_title,R.string.i_auth)},
                    failure={Column(Modifier.fillMaxSize().padding(24.dp)){Text(stringResource(R.string.m_load_error));MoneyTextButton({model.onAction(InvestmentAction.Retry)}){Text(stringResource(R.string.m_retry))}}},
                    content={MoneyInvestmentScreen(state,model::onAction,{finish()})})
            }}
        }
    }
}
