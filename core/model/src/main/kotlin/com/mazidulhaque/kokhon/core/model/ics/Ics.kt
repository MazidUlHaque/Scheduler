package com.mazidulhaque.kokhon.core.model.ics

import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderChain
import com.mazidulhaque.kokhon.core.model.ReminderStep
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reads and writes iCalendar (.ics, RFC 5545) files, so plans move to and from Google Calendar, Outlook and Apple Calendar.
 * Kokhon-specific details (type, importance, approximate time) travel in X-KOKHON-* properties that other apps ignore.
 */
object Ics {
    private const val CRLF = "\r\n"
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val localFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val ASSUMED_LENGTH: Duration = Duration.ofMinutes(30)

    fun write(
        entries: List<Entry>,
        now: Instant,
        zone: ZoneId,
    ): String {
        val out = StringBuilder()

        fun line(text: String) = out.append(fold(text)).append(CRLF)
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//Md. Mazid-Ul-Haque//Kokhon//EN")
        line("CALSCALE:GREGORIAN")
        entries.forEach { e ->
            line("BEGIN:VEVENT")
            line("UID:kokhon-${e.id}-${e.start.epochSecond}@kokhon.app")
            line("DTSTAMP:${utc.format(now)}")
            if (e.allDay) {
                val day = e.start.atZone(zone).toLocalDate()
                line("DTSTART;VALUE=DATE:${dateFormat.format(day)}")
                line("DTEND;VALUE=DATE:${dateFormat.format(day.plusDays(1))}")
            } else {
                line("DTSTART:${utc.format(e.start)}")
                line("DTEND:${utc.format(e.start.plus(e.duration?.takeIf { !it.isZero && !it.isNegative } ?: ASSUMED_LENGTH))}")
            }
            line("SUMMARY:${escape(e.title)}")
            e.location?.let { line("LOCATION:${escape(it)}") }
            val description = listOfNotNull(e.notes, e.source.originalText?.let { "From: \"$it\"" }).joinToString("\n")
            if (description.isNotBlank()) line("DESCRIPTION:${escape(description)}")
            line("STATUS:${if (e.manualStatus == EntryStatus.CANCELLED) "CANCELLED" else "CONFIRMED"}")
            line("X-KOKHON-TYPE:${e.type.name}")
            line("X-KOKHON-IMPORTANCE:${e.importance.name}")
            if (e.isApproximate) line("X-KOKHON-APPROXIMATE:TRUE")
            if (e.reminders.enabled) {
                e.reminders.orderedSteps.filter { it.enabled && it.style != ReminderStyle.SILENT }.forEach { step ->
                    line("BEGIN:VALARM")
                    line("ACTION:DISPLAY")
                    line("DESCRIPTION:${escape(e.title)}")
                    line("TRIGGER:${trigger(step.offset)}")
                    line("END:VALARM")
                }
            }
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    /** Reads every event in the file. Unknown properties are ignored; broken events are skipped. */
    fun read(
        text: String,
        defaultZone: ZoneId,
    ): List<Entry> {
        val lines = unfold(text)
        val entries = mutableListOf<Entry>()
        var props: MutableList<Pair<Property, String>>? = null
        var alarms = mutableListOf<Duration>()
        var inAlarm = false
        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line.equals("BEGIN:VEVENT", true) -> {
                    props = mutableListOf()
                    alarms = mutableListOf()
                }

                line.equals("BEGIN:VALARM", true) -> {
                    inAlarm = true
                }

                line.equals("END:VALARM", true) -> {
                    inAlarm = false
                }

                line.equals("END:VEVENT", true) -> {
                    props?.let { p -> runCatching { toEntry(p, alarms, defaultZone) }.getOrNull()?.let { entries += it } }
                    props = null
                }

                props != null -> {
                    val property = Property.parse(line) ?: continue
                    if (inAlarm) {
                        if (property.name == "TRIGGER") parseDuration(property.value)?.let { alarms += it.negated() }
                    } else {
                        props.add(property to property.value)
                    }
                }
            }
        }
        return entries
    }

    private fun toEntry(
        props: List<Pair<Property, String>>,
        alarms: List<Duration>,
        zone: ZoneId,
    ): Entry? {
        fun prop(name: String) = props.firstOrNull { it.first.name == name }?.first
        val startProp = prop("DTSTART") ?: return null
        val (start, allDay) = parseDate(startProp, zone) ?: return null
        val end = prop("DTEND")?.let { parseDate(it, zone)?.first }
        val duration =
            end?.let { Duration.between(start, it) }?.takeIf { !it.isNegative && !it.isZero && !allDay }
                ?: prop("DURATION")?.let { parseDuration(it.value) }
        val type = prop("X-KOKHON-TYPE")?.value?.let { v -> EntryType.entries.firstOrNull { it.name == v } } ?: EntryType.OTHER
        val importance =
            prop("X-KOKHON-IMPORTANCE")?.value?.let { v -> Importance.entries.firstOrNull { it.name == v } } ?: type.defaultImportance
        val reminders =
            if (alarms.isEmpty()) {
                ReminderChain.defaultFor(importance, type)
            } else {
                // The last alarm before the start rings when the plan is important, as Kokhon's own chains do.
                val ordered = alarms.filter { !it.isNegative }.distinct().sortedDescending()
                ReminderChain(
                    ordered.mapIndexed { i, offset ->
                        val ring = i == ordered.lastIndex && importance.ordinal >= Importance.IMPORTANT.ordinal
                        ReminderStep(offset, if (ring) ReminderStyle.ALARM else ReminderStyle.NOTIFICATION)
                    },
                )
            }
        val description =
            prop("DESCRIPTION")
                ?.value
                ?.let(::unescape)
                ?.lines()
                ?.filterNot { it.startsWith("From: \"") }
                ?.joinToString("\n")
        return Entry(
            title = prop("SUMMARY")?.value?.let(::unescape)?.ifBlank { null } ?: "Event",
            type = type,
            start = start,
            duration = duration,
            allDay = allDay,
            isApproximate = prop("X-KOKHON-APPROXIMATE")?.value.equals("TRUE", true),
            location = prop("LOCATION")?.value?.let(::unescape)?.ifBlank { null },
            notes = description?.ifBlank { null },
            importance = importance,
            reminders = reminders,
            manualStatus = if (prop("STATUS")?.value.equals("CANCELLED", true)) EntryStatus.CANCELLED else null,
        )
    }

    private fun parseDate(
        p: Property,
        zone: ZoneId,
    ): Pair<Instant, Boolean>? =
        runCatching {
            val v = p.value
            when {
                p.params["VALUE"].equals("DATE", true) || v.length == 8 -> {
                    LocalDate.parse(v, dateFormat).atStartOfDay(zone).toInstant() to true
                }

                v.endsWith("Z") -> {
                    LocalDateTime.parse(v.dropLast(1), localFormat).toInstant(ZoneOffset.UTC) to false
                }

                else -> {
                    val tz = p.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                    LocalDateTime.parse(v, localFormat).atZone(tz).toInstant() to false
                }
            }
        }.getOrNull()

    /** "-PT15M" style durations, including days and weeks. */
    internal fun parseDuration(value: String): Duration? {
        val m =
            Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").matchEntire(value.trim()) ?: return null
        val g = m.groupValues
        var d =
            Duration
                .ofDays((g[2].toLongOrNull() ?: 0) * 7 + (g[3].toLongOrNull() ?: 0))
                .plusHours(g[4].toLongOrNull() ?: 0)
                .plusMinutes(g[5].toLongOrNull() ?: 0)
                .plusSeconds(g[6].toLongOrNull() ?: 0)
        if (g[1] == "-") d = d.negated()
        return d
    }

    internal fun trigger(offset: Duration): String {
        if (offset.isZero) return "PT0M"
        val days = offset.toDays()
        val hours = offset.toHoursPart()
        val minutes = offset.toMinutesPart()
        return buildString {
            append("-P")
            if (days > 0) append(days).append('D')
            if (hours > 0 || minutes > 0) {
                append('T')
                if (hours > 0) append(hours).append('H')
                if (minutes > 0) append(minutes).append('M')
            }
        }
    }

    private fun escape(text: String) =
        text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")

    private fun unescape(text: String) =
        text.replace(Regex("\\\\([nN;,\\\\])")) { m ->
            when (m.groupValues[1]) {
                "n", "N" -> "\n"
                else -> m.groupValues[1]
            }
        }

    /** Lines longer than 75 bytes are folded onto continuation lines that start with a space. */
    private fun fold(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) return line
        val out = StringBuilder()
        var count = 0
        var limit = 75
        for (ch in line) {
            val size = ch.toString().toByteArray(Charsets.UTF_8).size
            if (count + size > limit) {
                out.append(CRLF).append(' ')
                count = 0
                limit = 74
            }
            out.append(ch)
            count += size
        }
        return out.toString()
    }

    private fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        text.split(Regex("\r?\n")).forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.substring(1)
            } else {
                result += line
            }
        }
        return result
    }

    internal data class Property(
        val name: String,
        val params: Map<String, String>,
        val value: String,
    ) {
        companion object {
            fun parse(line: String): Property? {
                // The value starts at the first colon that is not inside a quoted parameter.
                var quoted = false
                var colon = -1
                for ((i, c) in line.withIndex()) {
                    if (c == '"') quoted = !quoted
                    if (c == ':' && !quoted) {
                        colon = i
                        break
                    }
                }
                if (colon <= 0) return null
                val head = line.substring(0, colon).split(';')
                val params =
                    head
                        .drop(1)
                        .mapNotNull { p ->
                            val eq = p.indexOf('=')
                            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
                        }.toMap()
                return Property(head[0].uppercase(), params, line.substring(colon + 1))
            }
        }
    }
}
