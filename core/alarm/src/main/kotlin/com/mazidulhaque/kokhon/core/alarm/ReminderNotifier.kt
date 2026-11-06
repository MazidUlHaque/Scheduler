package com.mazidulhaque.kokhon.core.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.mazidulhaque.kokhon.core.data.settings.ReminderSettings
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderPurpose
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import com.mazidulhaque.kokhon.core.model.ScheduledReminder
import java.time.Duration

/** Turns a due reminder into a notification or a ringing alarm, respecting discreet and silent modes. */
class ReminderNotifier(
    private val context: Context,
) {
    private val clock = context.reminderHost.clock

    fun deliver(
        entry: Entry,
        reminder: ScheduledReminder,
        snoozeCount: Int,
        settings: ReminderSettings,
    ) {
        ensureChannels(context)
        val now = clock.instant()
        val muted =
            settings.isDiscreetAt(now) ||
                (isPhoneQuiet() && !(entry.importance == Importance.CRITICAL && settings.criticalBypassesSilent))
        when {
            reminder.style == ReminderStyle.ALARM && !settings.isDiscreetAt(now) -> {
                AlarmService.start(context, entry.id, reminder.stepIndex, snoozeCount, withSound = !muted)
            }

            else -> {
                val channel =
                    when {
                        settings.isDiscreetAt(now) -> CHANNEL_DISCREET
                        reminder.style == ReminderStyle.NOTIFICATION -> CHANNEL_REMINDERS
                        else -> CHANNEL_HEADS_UP
                    }
                notify(entry, reminder, snoozeCount, settings, channel)
            }
        }
    }

    private fun notify(
        entry: Entry,
        reminder: ScheduledReminder,
        snoozeCount: Int,
        settings: ReminderSettings,
        channel: String,
    ) {
        if (!canPostNotifications(context)) return
        val id = reminder.requestCode
        val open =
            PendingIntent.getActivity(
                context,
                id,
                context.reminderHost.openEntryIntent(context, entry.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val style = reminder.style
        val builder =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_kokhon)
                .setContentTitle(titleFor(context, entry, reminder.stepIndex))
                .setContentText(describeStart(context, entry, clock.instant()))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(
                    if (channel ==
                        CHANNEL_REMINDERS
                    ) {
                        NotificationCompat.PRIORITY_DEFAULT
                    } else {
                        NotificationCompat.PRIORITY_HIGH
                    },
                ).setContentIntent(open)
                .setAutoCancel(true)
                .setWhen(entry.start.toEpochMilli())
                .setShowWhen(true)
                .addAction(
                    0,
                    context.getString(R.string.action_done),
                    NotificationActionReceiver.pendingIntent(
                        context,
                        NotificationActionReceiver.ACTION_DONE,
                        entry.id,
                        reminder.stepIndex,
                        id,
                        snoozeCount,
                        style,
                    ),
                )
        if (settings.alarm.canSnooze(snoozeCount)) {
            // Smart snooze: far from the start, "remind me 10 min before" is more useful than another 5 minutes.
            val farAway = Duration.between(clock.instant(), entry.start) > Duration.ofMinutes(20)
            val (label, action) =
                if (farAway) {
                    context.getString(R.string.action_remind_before) to NotificationActionReceiver.ACTION_SNOOZE_UNTIL_BEFORE
                } else {
                    context.getString(
                        R.string.action_snooze_minutes,
                        settings.alarm.snoozeLength
                            .toMinutes()
                            .toInt(),
                    ) to
                        NotificationActionReceiver.ACTION_SNOOZE
                }
            builder.addAction(
                0,
                label,
                NotificationActionReceiver.pendingIntent(context, action, entry.id, reminder.stepIndex, id, snoozeCount, style),
            )
        }
        builder.addAction(
            0,
            context.getString(R.string.action_turn_off),
            NotificationActionReceiver.pendingIntent(
                context,
                NotificationActionReceiver.ACTION_TURN_OFF,
                entry.id,
                reminder.stepIndex,
                id,
                snoozeCount,
                style,
            ),
        )
        @Suppress("MissingPermission") // checked by canPostNotifications
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** Shown when an alarm stopped ringing by itself after the ring limit. */
    fun notifyWentQuiet(entry: Entry) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val open =
            PendingIntent.getActivity(
                context,
                quietNotificationId(entry.id),
                context.reminderHost.openEntryIntent(context, entry.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_QUIET)
                .setSmallIcon(R.drawable.ic_stat_kokhon)
                .setContentTitle(context.getString(R.string.alarm_went_quiet, entry.title))
                .setContentText(context.getString(R.string.alarm_went_quiet_body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(quietNotificationId(entry.id), notification)
    }

    private fun isPhoneQuiet(): Boolean {
        val audio = context.getSystemService<AudioManager>()
        val notifications = context.getSystemService<NotificationManager>()
        val ringerQuiet = audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        val dndOn =
            notifications != null &&
                notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        return ringerQuiet || dndOn
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_HEADS_UP = "reminders_heads_up"
        const val CHANNEL_DISCREET = "reminders_discreet"
        const val CHANNEL_ALARMS = "alarms"
        const val CHANNEL_QUIET = "alarms_went_quiet"
        const val CHANNEL_SUMMARY = "daily_summary"

        fun quietNotificationId(entryId: Long): Int = -(entryId.toInt() + 1)

        /** "Get ready: Dinner with Nabila" when the step has a purpose, otherwise just the title. */
        fun titleFor(
            context: Context,
            entry: Entry,
            stepIndex: Int,
        ): String {
            val res =
                when (
                    entry.reminders.orderedSteps
                        .getOrNull(stepIndex)
                        ?.purpose
                ) {
                    ReminderPurpose.GET_READY -> R.string.purpose_get_ready
                    ReminderPurpose.LEAVE_NOW -> R.string.purpose_leave_now
                    ReminderPurpose.PACK -> R.string.purpose_pack
                    ReminderPurpose.CHECK_IN -> R.string.purpose_check_in
                    ReminderPurpose.PREPARE -> R.string.purpose_prepare
                    ReminderPurpose.BUY_GIFT -> R.string.purpose_buy_gift
                    null -> return entry.title
                }
            return context.getString(res, entry.title)
        }

        fun canPostNotifications(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (
                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                )

        /** "Starts in 15 min · 9:30 PM", with "about" for approximate times. */
        fun describeStart(
            context: Context,
            entry: Entry,
            now: java.time.Instant,
        ): String {
            val time = TimeFormat.time(context, entry.start)
            val at = context.getString(if (entry.isApproximate) R.string.about_time else R.string.at_time, time)
            val until = Duration.between(now, entry.start)
            val lead =
                if (until.toMinutes() >=
                    1
                ) {
                    context.getString(R.string.starts_in, TimeFormat.duration(context, until))
                } else {
                    context.getString(R.string.starts_now)
                }
            return "$lead · $at"
        }

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService<NotificationManager>() ?: return
            val channels =
                listOf(
                    NotificationChannel(
                        CHANNEL_REMINDERS,
                        context.getString(R.string.channel_reminders),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = context.getString(R.string.channel_reminders_desc)
                    },
                    NotificationChannel(
                        CHANNEL_HEADS_UP,
                        context.getString(R.string.channel_heads_up),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = context.getString(R.string.channel_heads_up_desc)
                    },
                    NotificationChannel(
                        CHANNEL_DISCREET,
                        context.getString(R.string.channel_heads_up),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = context.getString(R.string.channel_heads_up_desc)
                        setSound(null, null)
                        enableVibration(true)
                    },
                    NotificationChannel(
                        CHANNEL_ALARMS,
                        context.getString(R.string.channel_alarms),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = context.getString(R.string.channel_alarms_desc)
                        setSound(null, null)
                        enableVibration(false)
                        setBypassDnd(true)
                    },
                    NotificationChannel(
                        CHANNEL_QUIET,
                        context.getString(R.string.channel_quiet),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.channel_quiet_desc)
                    },
                )
            val summary =
                NotificationChannel(
                    CHANNEL_SUMMARY,
                    context.getString(R.string.channel_summary),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_summary_desc)
                }
            manager.createNotificationChannels(channels + summary)
        }
    }
}
