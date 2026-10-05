package com.trackermaster.core.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.glance.appwidget.updateAll
import com.trackermaster.core.data.money.MoneyPreferencesRepository
import kotlinx.coroutines.*

/** Mask cached launcher views before starting the asynchronous Glance refresh. */
class MoneyRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MoneyPreferencesRepository.ACTION_CHANGED) return
        if (intent.getBooleanExtra(MoneyPreferencesRepository.EXTRA_HIDE, false)) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(ExpenseBalanceWidgetReceiver::class.java, DashboardComboWidgetReceiver::class.java).forEach { type ->
                val ids = manager.getAppWidgetIds(ComponentName(context, type))
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, RemoteViews(context.packageName, R.layout.money_private_widget))
            }
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ExpenseBalanceWidget().updateAll(context)
                DashboardComboWidget().updateAll(context)
            } catch (_: Exception) {
                // Keep the masked view if launcher refresh fails. No financial data is logged.
            } finally { pending.finish() }
        }
    }
}
