package com.mazidulhaque.kokhon.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import com.mazidulhaque.kokhon.core.model.ScheduledReminder
import com.mazidulhaque.kokhon.core.model.reminderForFiredAlarm
import java.time.Instant

/** Receives a system alarm for one reminder step and decides how to present it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_FIRE) return
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, 0)
        val stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, -1)
        val fireAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_FIRE_AT, 0))
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        val snoozedStyle =
            intent.getStringExtra(EXTRA_STYLE)?.let { name ->
                ReminderStyle.entries.firstOrNull {
                    it.name ==
                        name
                }
            }
        val host = context.reminderHost
        runAsync {
            val entry = host.entryRepository.get(entryId) ?: return@runAsync
            val reminder =
                if (snoozeCount > 0) {
                    // A snoozed reminder no longer matches its original fire time; only check it is still wanted.
                    entry
                        .reminderForFiredAlarm(
                            stepIndex,
                            entry.reminders.orderedSteps
                                .getOrNull(stepIndex)
                                ?.fireTime(entry.start) ?: fireAt,
                        )?.copy(fireAt = fireAt, style = snoozedStyle ?: ReminderStyle.NOTIFICATION)
                } else {
                    entry.reminderForFiredAlarm(stepIndex, fireAt)
                } ?: return@runAsync
            ReminderNotifier(context).deliver(entry, reminder, snoozeCount, host.settingsRepository.current())
        }
    }

    companion object {
        const val ACTION_FIRE = "com.mazidulhaque.kokhon.action.REMINDER_FIRE"
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_STEP_INDEX = "step_index"
        const val EXTRA_FIRE_AT = "fire_at"
        const val EXTRA_SNOOZE_COUNT = "snooze_count"
        const val EXTRA_STYLE = "style"

        fun fireIntent(
            context: Context,
            reminder: ScheduledReminder,
            snoozeCount: Int,
        ): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_ENTRY_ID, reminder.entryId)
                .putExtra(EXTRA_STEP_INDEX, reminder.stepIndex)
                .putExtra(EXTRA_FIRE_AT, reminder.fireAt.toEpochMilli())
                .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
                .putExtra(EXTRA_STYLE, reminder.style.name)
    }
}
