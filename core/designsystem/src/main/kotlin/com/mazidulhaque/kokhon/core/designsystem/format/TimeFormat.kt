package com.mazidulhaque.kokhon.core.designsystem.format

import android.content.Context
import android.text.format.DateFormat
import com.mazidulhaque.kokhon.core.designsystem.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** Formats times the way the user set their phone: 12-hour ("9:30 PM") or 24-hour ("21:30"). */
object TimeFormat {
    /** Uses the language's own digits too, e.g. Bangla digits in Bangla. */
    private fun formatter(pattern: String): DateTimeFormatter {
        val locale = Locale.getDefault()
        return DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(locale))
    }

    private fun timePattern(context: Context) = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

    fun time(
        context: Context,
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = formatter(timePattern(context)).format(instant.atZone(zone))

    /** "Sat, 7 Nov" */
    fun shortDate(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = formatter("EEE, d MMM").format(instant.atZone(zone))

    /** "Saturday 7 November" */
    fun longDate(date: LocalDate): String = formatter("EEEE d MMMM").format(date)

    /** "1 h 20 min", "5 min", "2 days", in the app's language. */
    fun duration(
        context: Context,
        value: Duration,
    ): String {
        val res = context.resources
        val minutes = value.abs().toMinutes().coerceAtLeast(1)
        val days = minutes / (60 * 24)
        val hours = ((minutes % (60 * 24)) / 60).toInt()
        val mins = (minutes % 60).toInt()
        return when {
            days >= 2 -> res.getQuantityString(R.plurals.duration_days, days.toInt(), days.toInt())
            days == 1L && hours > 0 -> res.getString(R.string.duration_day_hours, hours)
            days == 1L -> res.getQuantityString(R.plurals.duration_days, 1, 1)
            hours > 0 && mins > 0 -> res.getString(R.string.duration_hours_minutes, hours, mins)
            hours > 0 -> res.getString(R.string.duration_hours, hours)
            else -> res.getString(R.string.duration_minutes, mins)
        }
    }
}
