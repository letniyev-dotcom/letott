package com.letify.app.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.letify.app.MainActivity
import com.letify.app.data.LetifyDataStore
import com.letify.app.ui.state.TaskItem
import java.time.LocalDateTime

/**
 * Fires when a task's reminder alarm goes off. The app process may well be
 * dead at this point, so this reads the task straight from
 * [LetifyDataStore] rather than trusting [AppState] to be around — that way
 * a task edited or deleted since the alarm was scheduled is picked up
 * correctly (or silently skipped, if it's gone).
 */
class TaskReminderReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission") // guarded by notificationsPermitted() below
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TaskReminders.EXTRA_TASK_ID, -1)
        if (taskId < 0) return
        val task = LetifyDataStore(context).loadTasks()?.firstOrNull { it.id == taskId }

        if (task != null && task.remind && TaskReminders.notificationsPermitted(context)) {
            showNotification(context, task)
        }
        // A recurring task always keeps exactly one future alarm pending —
        // reschedule the next occurrence right after this one fires (no-op
        // if the task was deleted or turned reminders off).
        if (task != null) {
            TaskReminders.schedule(context, task, LocalDateTime.now())
        }
    }

    private fun showNotification(context: Context, task: TaskItem) {
        TaskReminders.ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            task.id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (task.remindMinutesBefore <= 0) {
            "Начинается сейчас · ${task.startTime}"
        } else {
            "Через ${task.remindMinutesBefore} мин · ${task.startTime}"
        }

        val notification = NotificationCompat.Builder(context, TaskReminders.CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(task.name)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_BASE + task.id, notification)
    }

    private companion object {
        const val NOTIF_ID_BASE = 20_000
    }
}
