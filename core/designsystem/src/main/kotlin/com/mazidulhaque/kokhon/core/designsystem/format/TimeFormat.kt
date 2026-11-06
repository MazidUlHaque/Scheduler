package com.mazidulhaque.kokhon.core.designsystem.format

import android.content.Context
import android.text.format.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats times the way the user set their phone: 12-hour ("9:30 PM") or 24-hour ("21:30"). */
object TimeFormat {
    private fun timePattern(context: Context) = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

    fun time(
        context: Context,
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = DateTimeFormatter.ofPattern(timePattern(context), Locale.getDefault()).format(instant.atZone(zone))

    /** "Sat, 7 Nov" */
    fun shortDate(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(instant.atZone(zone))

    /** "Saturday 7 November" */
    fun longDate(date: LocalDate): String = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(date)

    /** "1 h 20 min", "5 min", "2 days". Used after "in" or before "ago". */
    fun duration(value: Duration): String {
        val minutes = value.abs().toMinutes().coerceAtLeast(1)
        val days = minutes / (60 * 24)
        val hours = (minutes % (60 * 24)) / 60
        val mins = minutes % 60
        return when {
            days >= 2 -> "$days days"
            days == 1L -> if (hours > 0) "1 day $hours h" else "1 day"
            hours > 0 && mins > 0 -> "$hours h $mins min"
            hours > 0 -> "$hours h"
            else -> "$mins min"
        }
    }
}
