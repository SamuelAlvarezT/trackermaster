package com.trackermaster.core.widgets

import android.content.Context
import android.content.Intent
import com.trackermaster.core.data.repository.DashboardRepository
import com.trackermaster.core.data.repository.ExpenseRepository
import com.trackermaster.core.data.repository.HabitRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetHiltEntryPoint {
    fun habitRepository(): HabitRepository
    fun expenseRepository(): ExpenseRepository
    fun dashboardRepository(): DashboardRepository

    companion object {
        fun get(context: Context): WidgetHiltEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetHiltEntryPoint::class.java)

        fun launchIntent(context: Context, tab: String): Intent =
            Intent().apply {
                setClassName(context.packageName, "com.trackermaster.app.MainActivity")
                putExtra("tab", tab)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
