package com.trackermaster.feature.expense

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.ui.theme.TrackermasterTheme
import com.trackermaster.core.data.money.MoneyPlanningScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MoneyCommitmentActivity : FragmentActivity() {
    @Inject lateinit var scheduler: MoneyPlanningScheduler
    private val model by viewModels<MoneyCommitmentViewModel>()
    override fun onResume() { super.onResume(); scheduler.refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); actionBar?.hide()
        if(savedInstanceState==null)model.start(intent.getStringExtra("commitment_tab").orEmpty(),intent.getStringExtra("commitment_currency").orEmpty(),intent.getLongExtra("commitment_space",0).takeIf{it>0})
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            var authenticated by remember{mutableStateOf(false)}
            MoneyLocale(state.settings.localeTag){TrackermasterTheme(state.settings.themeMode,state.settings.accentIndex){
                BackHandler {
                    if(!state.controls.saving){
                        if(state.settings.biometricEnabled && !authenticated)finish()
                        else if(state.controls.draft!=null || state.controls.detail!=null)model.onEvent(CommitmentEvent.Close)
                        else finish()
                    }
                }
                MoneyPrivateAccess(state.loading,state.workspace!=null,state.settings.biometricEnabled,authenticated,
                    gate={ReceiptAuthentication(this,{authenticated=true},{finish()},R.string.c_auth_title,R.string.c_auth_subtitle)},
                    failure={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(Modifier.padding(24.dp)){Text(stringResource(R.string.m_load_error));MoneyTextButton({model.onEvent(CommitmentEvent.Retry)}){Text(stringResource(R.string.m_retry))}}}},
                    content={MoneyCommitmentScreen(state,model::onEvent,{finish()}){
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
                    }})
            }}
        }
    }
}
