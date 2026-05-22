package com.trackermaster.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trackermaster.app.MainActivity
import com.trackermaster.app.R
import com.trackermaster.core.data.repository.HabitRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val habitRepository: HabitRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habitName = inputData.getString(KEY_HABIT_NAME) ?: "Habit"
        val habitId = inputData.getLong(KEY_HABIT_ID, 0)
        showNotification(habitName, habitId)
        return Result.success()
    }

    private fun showNotification(habitName: String, habitId: Long) {
        val channelId = "habit_reminders"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Habit Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val openIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneIntent = PendingIntent.getBroadcast(
            applicationContext, habitId.toInt(),
            Intent(applicationContext, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_DONE
                putExtra(KEY_HABIT_ID, habitId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            applicationContext, (habitId + 1000).toInt(),
            Intent(applicationContext, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_SNOOZE
                putExtra(KEY_HABIT_ID, habitId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.reminder_title, habitName))
            .setContentText(applicationContext.getString(R.string.reminder_body))
            .setContentIntent(openIntent)
            .addAction(0, applicationContext.getString(R.string.mark_done), doneIntent)
            .addAction(0, applicationContext.getString(R.string.snooze), snoozeIntent)
            .build()
        nm.notify(habitId.toInt(), notification)
    }

    companion object {
        const val KEY_HABIT_ID = "habit_id"
        const val KEY_HABIT_NAME = "habit_name"
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(ReminderWorker.KEY_HABIT_ID, 0)
        when (intent.action) {
            ACTION_DONE -> { /* logged via app open */ }
            ACTION_SNOOZE -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(habitId.toInt())
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.trackermaster.DONE"
        const val ACTION_SNOOZE = "com.trackermaster.SNOOZE"
    }
}
