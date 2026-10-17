package com.trackermaster.core.data.money

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyPlanningScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureScheduled() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("money-planning-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MoneyPlanningWorker>(1, TimeUnit.HOURS).build())
    }
    fun refresh() {
        ensureScheduled()
        WorkManager.getInstance(context).enqueueUniqueWork("money-planning-refresh", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MoneyPlanningWorker>().build())
    }
}
