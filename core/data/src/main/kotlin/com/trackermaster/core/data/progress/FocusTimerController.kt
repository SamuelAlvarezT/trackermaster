package com.trackermaster.core.data.progress

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

object FocusTimerSignals {
    val error=MutableStateFlow(false)
    val running=MutableStateFlow(false)
}

class FocusTimerController @Inject constructor(@ApplicationContext private val context: Context) {
    fun send(command: String, category: String? = null, minutes: Int = 0, habitId: Long? = null) {
        context.startForegroundService(Intent(command).apply {
            component=ComponentName(context.packageName,"com.trackermaster.app.focus.FocusTimerService")
            putExtra("category",category);putExtra("minutes",minutes)
            habitId?.let { putExtra("habit_id",it) }
        })
    }
    companion object {
        const val START="com.trackermaster.focus.START"
        const val PAUSE="com.trackermaster.focus.PAUSE"
        const val RESUME="com.trackermaster.focus.RESUME"
        const val STOP="com.trackermaster.focus.STOP"
        const val RECOVER="com.trackermaster.focus.RECOVER"
    }
}
