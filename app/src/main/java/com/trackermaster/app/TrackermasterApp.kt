package com.trackermaster.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.trackermaster.core.data.repository.ExpenseRepository
import com.trackermaster.core.data.money.MoneyPlanningScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@HiltAndroidApp
class TrackermasterApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var expenseRepository: ExpenseRepository
    @Inject lateinit var habitRepository: com.trackermaster.core.data.repository.HabitRepository
    @Inject lateinit var moneyPlanningScheduler: MoneyPlanningScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        com.trackermaster.core.data.backup.RestoreBootstrap.applyPending(base)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeHabitWidget() {
        appScope.launch {
            habitRepository.observeDay().flatMapLatest { habitRepository.observeTodayHabits(it) }
                .distinctUntilChanged().collect {
                    val manager = android.appwidget.AppWidgetManager.getInstance(this@TrackermasterApp)
                    val receiver = android.content.ComponentName(this@TrackermasterApp, com.trackermaster.core.widgets.HabitProgressWidgetReceiver::class.java)
                    if (manager.getAppWidgetIds(receiver).isNotEmpty()) {
                        runCatching { com.trackermaster.core.widgets.WidgetRefresh.refreshHabits(this@TrackermasterApp) }
                    }
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        observeHabitWidget()
        appScope.launch {
            com.trackermaster.core.data.backup.RestoreBootstrap.cleanup(this@TrackermasterApp)
            expenseRepository.seedDefaultCategories()
            val refresh=java.io.File(noBackupFilesDir,"restore-refresh-required")
            if(refresh.exists()) {
                moneyPlanningScheduler.refresh()
                runCatching { com.trackermaster.core.widgets.WidgetRefresh.refreshAll(this@TrackermasterApp) }.onSuccess { refresh.delete() }
            } else moneyPlanningScheduler.ensureScheduled()
        }
    }
}
