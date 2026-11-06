package com.mazidulhaque.kokhon.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import com.mazidulhaque.kokhon.core.model.ScheduledReminder
import com.mazidulhaque.kokhon.core.model.scheduledReminders
import java.time.Clock
import java.time.Instant

/** Registers and cancels system alarms for reminder steps. Never polls. */
class AlarmScheduler(
    private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService())

    /** Replaces every alarm of [entry] with its current plan. */
    fun schedule(entry: Entry) {
        cancel(entry.id)
        entry.scheduledReminders(clock.instant()).forEach { register(it, snoozeCount = 0) }
    }

    fun cancel(entryId: Long) {
        if (entryId == 0L) return
        for (index in 0 until ScheduledReminder.MAX_STEPS) {
            val code = ScheduledReminder.requestCodeFor(entryId, index)
            existingIntent(code)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    /** Fires the same step again at [at]. Used by Snooze. */
    fun scheduleSnooze(
        entryId: Long,
        stepIndex: Int,
        style: ReminderStyle,
        at: Instant,
        snoozeCount: Int,
    ) {
        val reminder = ScheduledReminder(entryId, stepIndex, at, style, java.time.Duration.ZERO, isLast = false)
        register(reminder, snoozeCount)
    }

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun register(
        reminder: ScheduledReminder,
        snoozeCount: Int,
    ) {
        val operation =
            PendingIntent.getBroadcast(
                context,
                reminder.requestCode,
                ReminderReceiver.fireIntent(context, reminder, snoozeCount),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val at = reminder.fireAt.toEpochMilli()
        when {
            !canScheduleExactAlarms() -> {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }

            reminder.style == ReminderStyle.ALARM -> {
                // setAlarmClock is the most reliable path and shows the alarm icon in the status bar.
                val show =
                    PendingIntent.getActivity(
                        context,
                        reminder.requestCode,
                        context.reminderHost.openEntryIntent(context, reminder.entryId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
            }

            else -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        }
    }

    private fun existingIntent(code: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
}
