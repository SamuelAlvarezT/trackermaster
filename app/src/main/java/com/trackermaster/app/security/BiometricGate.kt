package com.trackermaster.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.trackermaster.app.R
import com.trackermaster.core.ui.R as P8R

@Composable
fun BiometricGate(activity: FragmentActivity,onUnlocked: ()->Unit) {
    val context=LocalContext.current
    val unlock by rememberUpdatedState(onUnlocked)
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val keyguard=remember(activity) { activity.getSystemService(KeyguardManager::class.java) }
    val credential=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(it.resultCode==Activity.RESULT_OK) unlock() else failed=true
    }
    val prompt=remember(activity) { BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),
        object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlock() }
            override fun onAuthenticationError(errorCode: Int,errString: CharSequence) { failed=true }
        }) }
    DisposableEffect(prompt) { onDispose { prompt.cancelAuthentication() } }
    LaunchedEffect(attempt) {
        failed=false
        val allowed=BiometricManager.Authenticators.BIOMETRIC_STRONG or
            if(Build.VERSION.SDK_INT>=30) BiometricManager.Authenticators.DEVICE_CREDENTIAL else 0
        if(BiometricManager.from(activity).canAuthenticate(allowed)==BiometricManager.BIOMETRIC_SUCCESS) {
            val info=BiometricPrompt.PromptInfo.Builder().setTitle(context.getString(R.string.biometric_title))
                .setSubtitle(context.getString(R.string.biometric_subtitle)).setAllowedAuthenticators(allowed).apply {
                    if(Build.VERSION.SDK_INT<30) setNegativeButtonText(context.getString(R.string.cancel))
                }.build()
            prompt.authenticate(info)
        } else failed=true
    }
    Surface(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.biometric_title),style=MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.biometric_subtitle))
            if(failed) {
                Text(stringResource(P8R.string.p8_security_blocked))
                Button(onClick={attempt++}) { Text(stringResource(P8R.string.p8_security_retry)) }
                if(Build.VERSION.SDK_INT<30 && keyguard.isDeviceSecure) OutlinedButton(onClick={
                    @Suppress("DEPRECATION")
                    val intent=keyguard.createConfirmDeviceCredentialIntent(context.getString(R.string.biometric_title),context.getString(R.string.biometric_subtitle))
                    if(intent!=null) credential.launch(intent)
                }) { Text(stringResource(P8R.string.p8_security_credential)) }
                TextButton(onClick={activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))}) { Text(stringResource(P8R.string.p8_security_settings)) }
            }
        }
    }
    }
}
