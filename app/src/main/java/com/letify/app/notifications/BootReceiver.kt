package com.letify.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager wipes every pending alarm on reboot — without this, task
 * reminders would silently stop working after the phone restarts until the
 * user happened to add or edit a task again. Puts every task's next alarm
 * back in place.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            TaskReminders.rescheduleAll(context.applicationContext)
        }
    }
}
