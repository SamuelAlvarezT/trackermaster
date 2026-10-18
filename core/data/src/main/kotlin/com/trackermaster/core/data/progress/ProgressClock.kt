package com.trackermaster.core.data.progress

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ProgressClock @Inject constructor(@ApplicationContext private val context: Context) {
    open fun wallMs(): Long = System.currentTimeMillis()
    open fun elapsedMs(): Long = SystemClock.elapsedRealtime()
    open fun bootCount(): Int = Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0)
    open fun zone(): ZoneId = ZoneId.systemDefault()
    open fun today(): LocalDate = Instant.ofEpochMilli(wallMs()).atZone(zone()).toLocalDate()
    fun days() = flow { while (true) { emit(today()); delay(15_000) } }.distinctUntilChanged()
}
