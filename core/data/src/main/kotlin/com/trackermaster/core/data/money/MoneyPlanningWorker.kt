package com.trackermaster.core.data.money

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trackermaster.core.data.R
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.money.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@HiltWorker
class MoneyPlanningWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters,
    private val planning: MoneyPlanningRepository, private val money: MoneyRepository,
    private val settings: SettingsRepository, private val commitments: MoneyCommitmentRepository) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (!notificationsAllowed(applicationContext)) Result.success() else {
            val locale = settings.settings.first().localeTag
            val today = LocalDate.now()
            val hour = LocalTime.now().hour
            val movements = money.observeMovements().first()
            val relevant = mutableSetOf<String>()
            planning.budgetSnapshot().forEach { budget ->
                val progress = BudgetRules.progress(budget, movements, today, Locale.forLanguageTag(locale))
                if (progress != null && progress.inPeriod && progress.status != BudgetStatus.OK) {
                    val key="money:budget:${budget.id}:${progress.from}:${budget.limit}:${budget.alertThreshold}:${progress.status}"
                    val tag=notificationTag("budget",budget.id.toString(),progress.from)
                    relevant+=tag
                    post(key,tag,"budgets",locale,requireNotNull(budget.limit).currencyCode,budget.spaceId)
                }
            }
            planning.planSnapshot().filter { it.active && !it.archived }.forEach { plan ->
                val reminder = PlanRules.reminderDate(plan)
                if (today.isAfter(reminder) || (today == reminder && hour >= 9)) {
                    val key="money:plan:${plan.id}:${plan.nextDue}:$reminder"
                    val tag=notificationTag("plan",plan.id,plan.nextDue)
                    relevant+=tag
                    post(key,tag,"plans",locale,plan.currency,plan.spaceId)
                }
            }
            commitments.dueDebts(today).filter { it.overdue || hour >= 9 }.forEach { progress ->
                val debt=progress.debt;val due=requireNotNull(debt.due)
                val tag=notificationTag("debt",debt.id,due)
                relevant+=tag
                post("money:debt:${debt.id}:$due:${debt.version}",tag,"debts",locale,debt.principal.currencyCode,debt.spaceId)
            }
            // Settled and archived records must not leave stale reminders visible.
            val manager=applicationContext.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.tag?.startsWith("money:")==true && it.tag !in relevant }
                .forEach { manager.cancel(it.tag,it.id) }
            Result.success()
        }
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { Result.retry() }

    // All UI languages are packaged; notification strings follow the selected app language.
    @SuppressLint("AppBundleLocaleChanges", "MissingPermission")
    private suspend fun post(key: String, tag: String, tab: String, locale: String, currency: String, space: Long?) {
        val token = planning.claimAlert(key, System.currentTimeMillis()) ?: return
        try {
            val context = applicationContext.createConfigurationContext(Configuration(applicationContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(locale))
            })
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.money_planning_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val intent = if(tab=="debts") Intent().setClassName(context,"com.trackermaster.feature.expense.MoneyCommitmentActivity")
                .putExtra("commitment_tab","debts").putExtra("commitment_currency",currency).putExtra("commitment_space",space?:0L)
            else Intent().setClassName(context, "com.trackermaster.feature.expense.MoneyPlanningActivity")
                .putExtra("planning_tab", tab).putExtra("planning_currency",currency).putExtra("planning_space",space?:0L)
            intent.setData("trackermaster://money/planning/$tag".toUri())
            val pending = PendingIntent.getActivity(context, tag.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.money_planning_notification_title))
                .setContentText(context.getString(when(tab){"debts"->R.string.money_debt_notification_due;"plans"->R.string.money_planning_notification_due;else->R.string.money_planning_notification_budget}))
                .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            // Generic content deliberately contains no financial amounts, names or notes.
            manager.notify(tag, 0, notification)
            planning.alertDelivered(key, token)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: Exception) { planning.releaseAlert(key, token); throw failure }
    }
    companion object {
        const val CHANNEL = "money_planning"
        // System-visible identities use only record IDs and dates, never financial amounts.
        internal fun notificationTag(kind: String, id: String, day: LocalDate): String {
            val digest=java.security.MessageDigest.getInstance("SHA-256")
                .digest("$kind:$id:$day".toByteArray(Charsets.UTF_8))
            return "money:"+digest.joinToString("") { "%02x".format(Locale.ROOT,it) }
        }
        fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
