package com.trackermaster.core.ui

import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** All three languages are bundled. Keep the Activity as the base for intents and permissions. */
@SuppressLint("AppBundleLocaleChanges")
@Composable
fun LocalizedAppContent(tag: String,content: @Composable ()->Unit) {
    val base=LocalContext.current
    val current=LocalConfiguration.current
    val configuration=remember(current,tag) { Configuration(current).apply { setLocale(Locale.forLanguageTag(tag)) } }
    val context=remember(base,configuration) {
        val localized=base.createConfigurationContext(configuration)
        object: ContextWrapper(base) {
            override fun getResources()=localized.resources
            override fun getAssets()=localized.assets
        }
    }
    CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides configuration,content=content)
}
