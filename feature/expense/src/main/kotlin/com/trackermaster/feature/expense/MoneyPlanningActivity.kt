package com.trackermaster.feature.expense

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackermaster.core.ui.theme.TrackermasterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MoneyPlanningActivity : FragmentActivity() {
    private val planning by viewModels<MoneyPlanningViewModel>()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()){planning.onEvent(PlanningEvent.Refresh)}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);actionBar?.hide()
        if(savedInstanceState==null)planning.start(intent.getStringExtra("planning_tab").orEmpty(),intent.getStringExtra("planning_currency").orEmpty(),
            intent.getLongExtra("planning_space",0).takeIf{it>0},intent.getLongExtra("planning_budget",0).takeIf{it>0})
        setContent {
            val state by planning.state.collectAsStateWithLifecycle()
            var authenticated by remember{mutableStateOf(false)}
            MoneyLocale(state.settings.localeTag){TrackermasterTheme(state.settings.themeMode,state.settings.accentIndex){
                BackHandler {
                    if(!state.controls.saving){
                        if(state.settings.biometricEnabled && !authenticated)finish()
                        else if(state.controls.budget!=null || state.controls.plan!=null || state.controls.settlement!=null)planning.onEvent(PlanningEvent.CloseEditor)
                        else finish()
                    }
                }
                MoneyPrivateAccess(state.loading,state.workspace!=null,state.settings.biometricEnabled,authenticated,
                    gate={ReceiptAuthentication(this,{authenticated=true},{finish()},R.string.p_auth_title,R.string.p_auth_subtitle)},
                    failure={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(Modifier.padding(24.dp)){
                        Text(stringResource(R.string.m_load_error));MoneyTextButton({planning.onEvent(PlanningEvent.Retry)}){Text(stringResource(R.string.m_retry))}
                    }}},content={MoneyPlanningScreen(state,planning::onEvent,{finish()},::requestNotifications,::openNotificationSettings)})
            }}
        }
    }
    override fun onResume(){super.onResume();planning.onEvent(PlanningEvent.Refresh)}
    private fun requestNotifications(){
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openNotificationSettings()
    }
    private fun openNotificationSettings(){startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
}
