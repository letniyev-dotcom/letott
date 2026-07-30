package com.letify.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.letify.app.data.LetifyDataStore
import com.letify.app.ui.state.TaskItem
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules local, on-device reminder notifications for tasks.
 *
 * There is no server / push backend here — the per-task "напомнить заранее"
 * toggle in the task editor already existed in the UI, it just never wired
 * up to the OS. This is that wiring: an exact [AlarmManager] alarm fires at
 * (start time − lead time), a [TaskReminderReceiver] shows the notification,
 * then immediately schedules the task's *next* occurrence — so one call
 * after add/edit keeps a recurring task's reminders going indefinitely
 * without any background service.
 *
 * One task ⇒ at most one pending alarm at a time (its next upcoming one),
 * keyed by task id.
 */
object TaskReminders {

    const val CHANNEL_ID = "task_reminders"
    const val EXTRA_TASK_ID = "task_id"
    private const val ACTION = "com.letify.app.action.TASK_REMINDER"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания о задачах",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о начале задач из «Плана»"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    /** Whether the app is currently allowed to post notifications at all. */
    fun notificationsPermitted(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Exact alarms are opt-in "special access" on Android 12+; irrelevant
     *  (always true) on older versions. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context, taskId: Int): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("letify://task-reminder/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, taskId, intent, flags)
    }

    fun cancel(context: Context, taskId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, taskId))
    }

    /**
     * (Re)schedules the next occurrence of [task]'s reminder. Cancels any
     * pending alarm instead if the task no longer wants reminders (remind
     * off, or no scheduled days left).
     */
    fun schedule(context: Context, task: TaskItem, from: LocalDateTime = LocalDateTime.now()) {
        if (!task.remind || task.days.isEmpty()) {
            cancel(context, task.id)
            return
        }
        val next = nextTrigger(task, from)
        if (next == null) {
            cancel(context, task.id)
            return
        }
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(context, task.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call — fall back
            // to an inexact alarm rather than crash.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /**
     * Re-derives every task's next alarm from scratch. AlarmManager clears
     * all pending alarms on reboot (and can lose them across an app
     * update), so this is called on `BOOT_COMPLETED` and once on app start.
     */
    fun rescheduleAll(context: Context) {
        val tasks = LetifyDataStore(context).loadTasks() ?: return
        val now = LocalDateTime.now()
        tasks.forEach { schedule(context, it, now) }
    }

    /** Cancels every task's pending alarm — used when the user turns the
     *  master «Привычки и задачи» notification switch off. */
    fun cancelAll(context: Context) {
        val tasks = LetifyDataStore(context).loadTasks() ?: return
        tasks.forEach { cancel(context, it.id) }
    }

    /**
     * The next [LocalDateTime] (today or up to 7 days out) at which
     * [task]'s reminder should fire, honoring its lead time and scheduled
     * days. A lead time bigger than the start time (e.g. task starts 00:10,
     * remind 30 min before) rolls the reminder into the previous calendar
     * day, same as any alarm app.
     */
    fun nextTrigger(task: TaskItem, from: LocalDateTime): LocalDateTime? {
        if (task.days.isEmpty()) return null
        val leadRaw = task.startMinutes - task.remindMinutesBefore
        val dayShift = if (leadRaw < 0) -1L else 0L
        val remindMinutes = ((leadRaw % (24 * 60)) + 24 * 60) % (24 * 60)
        for (offset in 0..7) {
            val taskDate = from.toLocalDate().plusDays(offset.toLong())
            if (taskDate.dayOfWeek.value !in task.days) continue
            val fireDate = taskDate.plusDays(dayShift)
            val candidate = fireDate.atStartOfDay().plusMinutes(remindMinutes.toLong())
            if (candidate.isAfter(from)) return candidate
        }
        return null
    }
}
