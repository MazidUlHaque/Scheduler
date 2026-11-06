package com.mazidulhaque.kokhon.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.model.Entry
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * A short list of the day's plans: today's in the morning, tomorrow's in the evening.
 * Uses inexact alarms (a few minutes late is fine), so it costs almost no battery.
 */
class DailySummary(
    private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService())

    /** Registers the next morning and evening summaries according to the settings. */
    fun scheduleNext(
        morning: Boolean,
        evening: Boolean,
    ) {
        schedule(KIND_MORNING, MORNING, morning)
        schedule(KIND_EVENING, EVENING, evening)
    }

    private fun schedule(
        kind: String,
        time: LocalTime,
        enabled: Boolean,
    ) {
        val intent = pendingIntent(kind)
        alarmManager.cancel(intent)
        if (!enabled) return
        val now = ZonedDateTime.now(clock)
        var next = now.toLocalDate().atTime(time).atZone(clock.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), WINDOW_MILLIS, intent)
    }

    private fun pendingIntent(kind: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            kind.hashCode(),
            Intent(context, DailySummaryReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KIND, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Posts the summary, or nothing when the day has no plans. */
    fun post(
        kind: String,
        entries: List<Entry>,
    ) {
        if (entries.isEmpty() || !ReminderNotifier.canPostNotifications(context)) return
        ReminderNotifier.ensureChannels(context)
        val title =
            context.resources.getQuantityString(
                if (kind == KIND_MORNING) R.plurals.summary_today else R.plurals.summary_tomorrow,
                entries.size,
                entries.size,
            )
        val first = entries.first()
        val style = NotificationCompat.InboxStyle()
        entries.take(6).forEach { style.addLine(TimeFormat.time(context, it.start) + "  " + it.title) }
        val open =
            PendingIntent.getActivity(
                context,
                kind.hashCode(),
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, ReminderNotifier.CHANNEL_SUMMARY)
                .setSmallIcon(R.drawable.ic_stat_kokhon)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.summary_first, first.title, TimeFormat.time(context, first.start)))
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission") // checked by canPostNotifications
        NotificationManagerCompat.from(context).notify(kind.hashCode(), notification)
    }

    companion object {
        const val ACTION = "com.mazidulhaque.kokhon.action.DAILY_SUMMARY"
        const val EXTRA_KIND = "kind"
        const val KIND_MORNING = "morning"
        const val KIND_EVENING = "evening"
        private val MORNING: LocalTime = LocalTime.of(7, 30)
        private val EVENING: LocalTime = LocalTime.of(21, 0)
        private const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}

class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != DailySummary.ACTION) return
        val kind = intent.getStringExtra(DailySummary.EXTRA_KIND) ?: return
        val host = context.reminderHost
        runAsync {
            val zone = host.clock.zone
            val today = LocalDate.now(host.clock)
            val day = if (kind == DailySummary.KIND_MORNING) today else today.plusDays(1)
            val from = day.atStartOfDay(zone).toInstant()
            val to = day.plusDays(1).atStartOfDay(zone).toInstant()
            val now = host.clock.instant()
            val plans =
                host.entryRepository
                    .startingFrom(from)
                    .filter { it.start.isBefore(to) && !it.isFinishedAt(now) }
                    .sortedBy { it.start }
            val summary = DailySummary(context, host.clock)
            summary.post(kind, plans)
            val settings = host.settingsRepository.current()
            summary.scheduleNext(settings.morningSummary, settings.eveningSummary)
        }
    }
}
