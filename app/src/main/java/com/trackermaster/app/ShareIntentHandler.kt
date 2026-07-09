package com.trackermaster.app

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SharedContent(
    val text: String? = null,
    val imageUri: Uri? = null,
)

object ShareIntentHandler {
    private val _sharedContent = MutableStateFlow<SharedContent?>(null)
    val sharedContent: StateFlow<SharedContent?> = _sharedContent.asStateFlow()

    fun update(content: SharedContent?) {
        _sharedContent.value = content
    }

    fun clear() {
        _sharedContent.value = null
    }
}
