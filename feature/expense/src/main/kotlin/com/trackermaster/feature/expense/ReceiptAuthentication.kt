package com.trackermaster.feature.expense

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Stored settings must arrive before any private financial content is composed. */
@Composable internal fun ReceiptAccess(state:ReceiptUiState,authenticated:Boolean,gate: @Composable () -> Unit,
    failure: @Composable () -> Unit = {},content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {when {
        state.loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        state.workspace==null -> failure()
        state.settings.biometricEnabled && !authenticated -> gate()
        else -> content()
    }}
}
@Composable internal fun ReceiptAuthentication(activity:FragmentActivity,onUnlocked:()->Unit,onClose:()->Unit) {
    val unlocked by rememberUpdatedState(onUnlocked)
    var unavailable by remember {mutableStateOf(false)}
    val title=stringResource(R.string.r_auth_title)
    val subtitle=stringResource(R.string.r_auth_subtitle)
    val cancel=stringResource(R.string.m_cancel)
    val prompt=remember(activity) {BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),object:BiometricPrompt.AuthenticationCallback(){
        override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){unlocked()}
        override fun onAuthenticationError(errorCode:Int,errString:CharSequence){unavailable=true}
    })}
    val info=remember(title,subtitle,cancel) {BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText(cancel).build()}
    fun authenticate() {
        unavailable=false
        if(BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS)
            prompt.authenticate(info)
        else unavailable=true
    }
    LaunchedEffect(prompt){authenticate()}
    DisposableEffect(prompt){onDispose{prompt.cancelAuthentication()}}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall)
        Text(subtitle)
        if(unavailable) Text(stringResource(R.string.r_auth_unavailable),color=MaterialTheme.colorScheme.error)
        MoneyOutlinedButton({authenticate()}){Text(stringResource(R.string.m_retry))}
        MoneyTextButton(onClose){Text(stringResource(R.string.r_close))}
    }}
}
