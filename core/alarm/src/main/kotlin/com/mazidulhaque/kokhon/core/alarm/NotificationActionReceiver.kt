package com.mazidulhaque.kokhon.core.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.ReminderStyle

/** Handles Done, Snooze and Turn off reminders from a notification, without opening the app. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, 0)
        val stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        val style = intent.getStringExtra(EXTRA_STYLE)?.let { n -> ReminderStyle.entries.firstOrNull { it.name == n } }
        NotificationManagerCompat.from(context).cancel(notificationId)
        val host = context.reminderHost
        runAsync {
            when (intent.action) {
                ACTION_DONE -> {
                    host.reminderCoordinator.setStatus(entryId, EntryStatus.COMPLETED)
                }

                ACTION_TURN_OFF -> {
                    host.reminderCoordinator.setRemindersEnabled(entryId, false)
                }

                ACTION_SNOOZE -> {
                    val settings = host.settingsRepository.current()
                    host.alarmScheduler.scheduleSnooze(
                        entryId = entryId,
                        stepIndex = stepIndex,
                        style = style ?: ReminderStyle.NOTIFICATION,
                        at = host.clock.instant().plus(settings.alarm.snoozeLength),
                        snoozeCount = snoozeCount + 1,
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.mazidulhaque.kokhon.action.DONE"
        const val ACTION_SNOOZE = "com.mazidulhaque.kokhon.action.SNOOZE"
        const val ACTION_TURN_OFF = "com.mazidulhaque.kokhon.action.TURN_OFF"
        private const val EXTRA_ENTRY_ID = "entry_id"
        private const val EXTRA_STEP_INDEX = "step_index"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_SNOOZE_COUNT = "snooze_count"
        private const val EXTRA_STYLE = "style"

        fun pendingIntent(
            context: Context,
            action: String,
            entryId: Long,
            stepIndex: Int,
            notificationId: Int,
            snoozeCount: Int,
            style: ReminderStyle,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_ENTRY_ID, entryId)
                    .putExtra(EXTRA_STEP_INDEX, stepIndex)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
                    .putExtra(EXTRA_STYLE, style.name)
            // Distinct request codes per action, so the three actions never overwrite each other.
            val code = notificationId * 4 + listOf(ACTION_DONE, ACTION_SNOOZE, ACTION_TURN_OFF).indexOf(action)
            return PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
