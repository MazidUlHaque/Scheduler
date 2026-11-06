package com.mazidulhaque.kokhon.core.parser

import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/*
 * Detectors scan a lower-cased copy of the message (same length as the original, so indices match)
 * and return "mentions". The parser then groups mentions into plans.
 */

internal enum class Meridiem { AM, PM }

/** Parts of the day. Each gives a default time and settles AM/PM for bare hours. */
internal enum class Period(
    val meridiem: Meridiem,
    val defaultHour: Int,
) {
    MORNING(Meridiem.AM, 9),
    NOON(Meridiem.PM, 13),
    AFTERNOON(Meridiem.PM, 15),
    EVENING(Meridiem.PM, 19),
    NIGHT(Meridiem.PM, 21),
}

internal sealed interface Mention {
    val start: Int
    val end: Int

    fun overlaps(other: Mention) = start < other.end && other.start < end
}

internal data class DateMention(
    override val start: Int,
    override val end: Int,
    val date: LocalDate,
    val guessed: Boolean = false,
    /** "tomorrow's meeting": the date identifies a plan rather than giving a new one. */
    val possessive: Boolean = false,
    val period: Period? = null,
) : Mention

internal data class TimeMention(
    override val start: Int,
    override val end: Int,
    val hour: Int,
    val minute: Int,
    val meridiem: Meridiem?,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val endMeridiem: Meridiem? = null,
    val approximate: Boolean = false,
    /** For relative times ("in 2 hours") the full moment is already known. */
    val absolute: ZonedDateTime? = null,
) : Mention

internal data class PeriodMention(
    override val start: Int,
    override val end: Int,
    val period: Period,
) : Mention

internal data class TypeMention(
    override val start: Int,
    override val end: Int,
    val type: EntryType,
    /** A more specific title word than the type label, e.g. "Lunch" for an EVENT. */
    val titleWord: String? = null,
    /** Implied part of the day, e.g. dinner is in the evening. */
    val period: Period? = null,
    /** Loose verbs like "sit for" or "call" lose to a clear noun like "meeting" in the same plan. */
    val weak: Boolean = false,
) : Mention

internal data class FlagMention(
    override val start: Int,
    override val end: Int,
    val kind: SpanKind,
    val importance: Importance? = null,
    val change: ChangeRequest.Kind? = null,
) : Mention

internal data class PeopleMention(
    override val start: Int,
    override val end: Int,
    val people: List<String>,
) : Mention

internal data class LocationMention(
    override val start: Int,
    override val end: Int,
    val place: String,
) : Mention

/** "remind me to X", "need to X": X becomes the task title. */
internal data class TaskMention(
    override val start: Int,
    override val end: Int,
    val titleStart: Int,
) : Mention

private const val B = "(?<![\\p{L}\\d])"
private const val E = "(?![\\p{L}\\d])"
private const val MERIDIEM = "(am|pm|a\\.m\\.?|p\\.m\\.?)"

/** Banglish suffixes people add to times: "9tay", "9.30e", "9 baje". */
private const val BN_SUFFIX = "(?:\\s*(?:baje|ta|tay|tai|te|e|er\\s+dike|dike))?"

private val NUMBER_WORDS =
    mapOf(
        "a" to 1,
        "an" to 1,
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
    )

private fun number(token: String): Int? = token.toIntOrNull() ?: NUMBER_WORDS[token]

private fun meridiemOf(token: String?): Meridiem? =
    when {
        token == null -> null
        token.startsWith("a") -> Meridiem.AM
        token.startsWith("p") -> Meridiem.PM
        else -> null
    }

private val MONTHS =
    mapOf(
        "january" to 1,
        "jan" to 1,
        "february" to 2,
        "feb" to 2,
        "march" to 3,
        "mar" to 3,
        "april" to 4,
        "apr" to 4,
        "may" to 5,
        "june" to 6,
        "jun" to 6,
        "july" to 7,
        "jul" to 7,
        "august" to 8,
        "aug" to 8,
        "september" to 9,
        "sept" to 9,
        "sep" to 9,
        "october" to 10,
        "oct" to 10,
        "november" to 11,
        "nov" to 11,
        "december" to 12,
        "dec" to 12,
    )
private val MONTH_PATTERN = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")

private val WEEKDAYS =
    mapOf(
        "monday" to DayOfWeek.MONDAY,
        "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "tues" to DayOfWeek.TUESDAY,
        "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY,
        "thur" to DayOfWeek.THURSDAY,
        "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
        "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "sun" to DayOfWeek.SUNDAY,
        // Bangla
        "shombar" to DayOfWeek.MONDAY,
        "sombar" to DayOfWeek.MONDAY,
        "mongolbar" to DayOfWeek.TUESDAY,
        "mangalbar" to DayOfWeek.TUESDAY,
        "budhbar" to DayOfWeek.WEDNESDAY,
        "budbar" to DayOfWeek.WEDNESDAY,
        "brihospotibar" to DayOfWeek.THURSDAY,
        "bisudbar" to DayOfWeek.THURSDAY,
        "shukrobar" to DayOfWeek.FRIDAY,
        "sukrobar" to DayOfWeek.FRIDAY,
        "shonibar" to DayOfWeek.SATURDAY,
        "sonibar" to DayOfWeek.SATURDAY,
        "robibar" to DayOfWeek.SUNDAY,
    )
private val WEEKDAY_PATTERN = WEEKDAYS.keys.sortedByDescending { it.length }.joinToString("|")

/** Short forms that are also ordinary English words; they count only after "on", "this" or "next". */
private val AMBIGUOUS_WEEKDAYS = setOf("sat", "sun", "wed", "mon")

/** Adds [mention] unless it overlaps one already found (earlier detectors win). */
private fun <T : Mention> MutableList<T>.addIfFree(
    mention: T,
    taken: List<Mention>,
) {
    if (taken.none { it.overlaps(mention) } && none { it.overlaps(mention) }) add(mention)
}

internal class Detectors(
    private val text: String,
    private val now: ZonedDateTime,
    private val config: ParserConfig,
) {
    /** Lower-cased, same length as [text]. */
    val lc: String = buildString(text.length) { text.forEach { append(it.lowercaseChar()) } }
    private val today: LocalDate = now.toLocalDate()

    private fun find(pattern: String) = Regex(pattern).findAll(lc)

    // ---------------------------------------------------------------- times

    fun times(): List<TimeMention> {
        val found = mutableListOf<TimeMention>()

        // Ranges: "7-9 pm", "from 7 to 9", "7:00 - 9:30"
        find("$B(from\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*$MERIDIEM?\\s*(?:-|–|to|till|until)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*$MERIDIEM?$BN_SUFFIX$E")
            .forEach { m ->
                val g = m.groupValues
                val hasCue = g[1].isNotEmpty() || g[3].isNotEmpty() || g[4].isNotEmpty() || g[6].isNotEmpty() || g[7].isNotEmpty()
                val h1 = g[2].toInt()
                val h2 = g[5].toInt()
                val m1 = g[3].toIntOrNull() ?: 0
                val m2 = g[6].toIntOrNull() ?: 0
                if (hasCue && h1 <= 23 && h2 <= 23 && m1 < 60 && m2 < 60 && !followedByUnit(m.range.last + 1)) {
                    val endMer = meridiemOf(g[7].ifEmpty { null })
                    // "7-9 pm": the pm applies to both ends.
                    val startMer = meridiemOf(g[4].ifEmpty { null }) ?: endMer?.takeIf { h1 <= h2 || h2 == 12 }
                    found.addIfFree(TimeMention(m.range.first, m.range.last + 1, h1, m1, startMer, h2, m2, endMer), emptyList())
                }
            }

        // "9:30 pm", "21:30", "9.30e"
        find("$B(?<![\\d][./])(\\d{1,2})[:.](\\d{2})(?![./]\\d)\\s*$MERIDIEM?$BN_SUFFIX$E").forEach { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h <= 23 && min < 60 && !followedByUnit(m.range.last + 1)) {
                found.addIfFree(TimeMention(m.range.first, m.range.last + 1, h, min, meridiemOf(m.groupValues[3].ifEmpty { null })), emptyList())
            }
        }

        // "9pm", "9 p.m."
        find("$B(\\d{1,2})\\s*$MERIDIEM$E").forEach { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) found.addIfFree(TimeMention(m.range.first, m.range.last + 1, h, 0, meridiemOf(m.groupValues[2])), emptyList())
        }

        // "9 o'clock", "9 baje", "9tay", "9ish"
        find("$B(\\d{1,2})\\s*(o'?clock|o’clock|baje|tay|tai|ish)$E").forEach { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) {
                val approx = m.groupValues[2] == "ish"
                found.addIfFree(TimeMention(m.range.first, m.range.last + 1, h, 0, null, approximate = approx), emptyList())
            }
        }

        // "half past 9", "quarter to 10", Bangla "sare 9" (half past)
        find("$B(half|quarter)\\s+(past|to)\\s+(\\d{1,2}|${NUMBER_WORDS.keys.joinToString("|")})$E").forEach { m ->
            val h = number(m.groupValues[3]) ?: return@forEach
            val (hour, minute) =
                when {
                    m.groupValues[1] == "half" && m.groupValues[2] == "past" -> h to 30
                    m.groupValues[2] == "past" -> h to 15
                    m.groupValues[1] == "half" -> (h - 1 + 24) % 24 to 30
                    else -> (h - 1 + 24) % 24 to 45
                }
            found.addIfFree(TimeMention(m.range.first, m.range.last + 1, hour, minute, null), emptyList())
        }
        find("$B(sare|shade|sharhe|share)\\s+(\\d{1,2})$BN_SUFFIX$E").forEach { m ->
            val h = m.groupValues[2].toInt()
            if (h in 1..12) found.addIfFree(TimeMention(m.range.first, m.range.last + 1, h, 30, null), emptyList())
        }

        // "noon", "midday", "midnight"
        find("$B(noon|midday|midnight)$E").forEach { m ->
            val hour = if (m.groupValues[1] == "midnight") 0 else 12
            found.addIfFree(TimeMention(m.range.first, m.range.last + 1, hour, 0, if (hour == 0) Meridiem.AM else Meridiem.PM), emptyList())
        }

        // "in 2 hours", "after 30 minutes", "in half an hour"
        find("$B(in|after)\\s+(half\\s+an?|\\d{1,3}|${NUMBER_WORDS.keys.joinToString("|")})\\s*(hours?|hrs?|h|minutes?|mins?|m)$E").forEach { m ->
            val amountText = m.groupValues[2]
            val minutes =
                when {
                    amountText.startsWith("half") -> 30L
                    m.groupValues[3].startsWith("h") -> (number(amountText) ?: return@forEach) * 60L
                    else -> (number(amountText) ?: return@forEach).toLong()
                }
            val at = now.plusMinutes(minutes)
            found.addIfFree(
                TimeMention(m.range.first, m.range.last + 1, at.hour, at.minute, if (at.hour < 12) Meridiem.AM else Meridiem.PM, absolute = at),
                emptyList(),
            )
        }

        // "make it 10", "10 instead"
        find("$B(?:make\\s+it|do\\s+it(?:\\s+at)?)\\s+(\\d{1,2})(?:[:.](\\d{2}))?$E|$B(\\d{1,2})(?:[:.](\\d{2}))?\\s+instead$E").forEach { m ->
            val g = m.groups
            val hourGroup = g[1] ?: g[3] ?: return@forEach
            val h = hourGroup.value.toInt()
            val min = (g[2] ?: g[4])?.value?.toInt() ?: 0
            if (h in 0..23 && min < 60) {
                found.addIfFree(TimeMention(hourGroup.range.first, (g[2] ?: g[4])?.range?.last?.plus(1) ?: (hourGroup.range.last + 1), h, min, null), emptyList())
            }
        }

        // "move it to 9": "to" is a time cue only when the message is about changing a plan.
        if (Regex("$B(move|shift|postpone|reschedule|push|delay|prepone|change|pichiye|egiye)").containsMatchIn(lc)) {
            find("${B}to\\s+(\\d{1,2})$BN_SUFFIX$E").forEach { m ->
                val h = m.groupValues[1].toInt()
                if (h in 0..23 && !followedByUnit(m.range.last + 1)) {
                    found.addIfFree(TimeMention(m.groups[1]!!.range.first, m.range.last + 1, h, 0, null), emptyList())
                }
            }
        }

        // A bare hour after a cue word: "at 9", "around 10", "by 5"
        find("$B(at|around|by|@|~|approx\\.?|about|before|after|from)\\s*(\\d{1,2})$BN_SUFFIX$E").forEach { m ->
            val h = m.groupValues[2].toInt()
            val numberStart = m.groups[2]!!.range.first
            if (h in 0..23 && !followedByUnit(m.range.last + 1)) {
                found.addIfFree(TimeMention(numberStart, m.range.last + 1, h, 0, null), emptyList())
            }
        }
        return found.sortedBy { it.start }
    }

    /** True when the number is a quantity, not a time ("3 days", "5 people", "10 tk"). */
    private fun followedByUnit(index: Int): Boolean {
        val rest = lc.substring(index.coerceAtMost(lc.length)).trimStart()
        return Regex("^(days?|weeks?|months?|years?|people|persons?|guys|mins?|minutes?|hours?|hrs?|%|tk|taka|bdt|usd|\\$|k$E|th$E|st$E|nd$E|rd$E)")
            .containsMatchIn(rest)
    }

    // ---------------------------------------------------------------- dates

    fun dates(times: List<TimeMention>): List<DateMention> {
        val found = mutableListOf<DateMention>()

        fun add(
            start: Int,
            end: Int,
            date: LocalDate,
            guessed: Boolean = false,
            period: Period? = null,
        ) {
            val possessive = Regex("^(?:'s|’s|er)$E").containsMatchIn(lc.substring(end))
            val realEnd = if (possessive) end + (Regex("^(?:'s|’s|er)").find(lc.substring(end))?.value?.length ?: 0) else end
            found.addIfFree(DateMention(start, realEnd, date, guessed, possessive, period), times)
        }

        find("$B(?:the\\s+)?day\\s+after\\s+(?:tomorrow|tmrw|tmr)$E|$B(porshu|porshudin|porsu|poroshu)$E").forEach {
            add(it.range.first, it.range.last + 1, today.plusDays(2))
        }
        find("$B(tonight|tonite|2nite|aj\\s*raa?te?|aaj\\s*raa?te?|ajke\\s*raa?te?)$E").forEach {
            add(it.range.first, it.range.last + 1, today, period = Period.NIGHT)
        }
        find("$B(tomorrow|tomorow|tommorow|tommorrow|tmrw|tmr|tmrow|2moro|2morrow|agamikal|agami\\s+kal|kalke|kal)$E").forEach {
            add(it.range.first, it.range.last + 1, today.plusDays(1))
        }
        find("$B(today|aj|aaj|ajke|aajke)$E").forEach { add(it.range.first, it.range.last + 1, today) }
        find("$B(this)\\s+(morning|afternoon|evening)$E").forEach {
            val period =
                when (it.groupValues[2]) {
                    "morning" -> Period.MORNING
                    "afternoon" -> Period.AFTERNOON
                    else -> Period.EVENING
                }
            add(it.range.first, it.range.last + 1, today, period = period)
        }

        // "in 3 days", "after 2 weeks"
        find("$B(in|after)\\s+(\\d{1,2}|${NUMBER_WORDS.keys.joinToString("|")})\\s+(days?|weeks?)$E").forEach { m ->
            val n = number(m.groupValues[2]) ?: return@forEach
            val days = if (m.groupValues[3].startsWith("week")) n * 7L else n.toLong()
            add(m.range.first, m.range.last + 1, today.plusDays(days))
        }

        // Weekdays: "friday", "this friday", "next friday", "on sat"
        find("$B(?:(this|next|coming|upcoming|on|ei|agami)\\s+)?($WEEKDAY_PATTERN)$E").forEach { m ->
            val prefix = m.groupValues[1]
            val word = m.groupValues[2]
            if (word in AMBIGUOUS_WEEKDAYS && prefix.isEmpty()) return@forEach
            val day = WEEKDAYS.getValue(word)
            val upcoming = today.with(TemporalAdjusters.nextOrSame(day))
            val (date, guessed) =
                when (prefix) {
                    // "next friday" usually means the one in the coming week when this week's is close.
                    "next", "agami" -> {
                        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                        (if (!upcoming.isAfter(endOfWeek)) upcoming.plusWeeks(1) else upcoming) to true
                    }

                    else -> {
                        upcoming to false
                    }
                }
            add(m.range.first, m.range.last + 1, date, guessed)
        }

        // Weekend
        find("$B(?:this\\s+|next\\s+|the\\s+|on\\s+the\\s+)?weekend$E").forEach { m ->
            var date = today.with(TemporalAdjusters.nextOrSame(config.weekendStart))
            if (m.value.contains("next")) date = date.plusWeeks(1)
            add(m.range.first, m.range.last + 1, date, guessed = true)
        }

        // "next week"
        find("${B}next\\s+week$E").forEach { m ->
            add(m.range.first, m.range.last + 1, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), guessed = true)
        }

        // ISO "2026-11-15"
        find("$B(\\d{4})-(\\d{1,2})-(\\d{1,2})$E").forEach { m ->
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { add(m.range.first, m.range.last + 1, it) }
        }
        // "15/11", "15/11/2026", "15-11-26", "15.11.2026" (day first, as in Bangladesh and the UK)
        find("$B(\\d{1,2})([/\\-.])(\\d{1,2})(?:\\2(\\d{2,4}))?$E").forEach { m ->
            if (m.groupValues[2] == "." && m.groupValues[4].isEmpty()) return@forEach // "9.30" is a time
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[3].toInt()
            val year = m.groupValues[4].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            resolveDayMonth(day, month, year)?.let { add(m.range.first, m.range.last + 1, it) }
        }
        // "15th november", "15 nov 2026", "15th of nov"
        find("$B(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTH_PATTERN)(?:\\s*,?\\s*(\\d{4}))?$E").forEach { m ->
            resolveDayMonth(m.groupValues[1].toInt(), MONTHS.getValue(m.groupValues[2]), m.groupValues[3].toIntOrNull())
                ?.let { add(m.range.first, m.range.last + 1, it) }
        }
        // "november 15", "nov 15th, 2026"
        find("$B($MONTH_PATTERN)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?$E").forEach { m ->
            resolveDayMonth(m.groupValues[2].toInt(), MONTHS.getValue(m.groupValues[1]), m.groupValues[3].toIntOrNull())
                ?.let { add(m.range.first, m.range.last + 1, it) }
        }
        // "on the 15th"
        find("$B(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(st|nd|rd|th)$E").forEach { m ->
            val day = m.groupValues[1].toInt()
            var date = runCatching { today.withDayOfMonth(day) }.getOrNull()
            if (date == null || date.isBefore(today)) date = runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
            date?.let { add(m.range.first, m.range.last + 1, it) }
        }
        return found.sortedBy { it.start }
    }

    private fun date(
        year: Int,
        month: Int,
        day: Int,
    ): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /** Without a year, a date that has already passed this year means next year. */
    private fun resolveDayMonth(
        day: Int,
        month: Int,
        year: Int?,
    ): LocalDate? {
        if (year != null) return date(year, month, day)
        val thisYear = date(today.year, month, day) ?: return null
        return if (thisYear.isBefore(today)) date(today.year + 1, month, day) else thisYear
    }

    // ---------------------------------------------------------------- words

    fun periods(): List<PeriodMention> =
        listOf(
            "$B(?:in\\s+the\\s+)?(morning|sokal|shokal|sokale|shokale)$E" to Period.MORNING,
            "$B(?:in\\s+the\\s+)?(afternoon|bikal|bikel|bikale|bikele)$E" to Period.AFTERNOON,
            "$B(dupur|dupure)$E" to Period.NOON,
            "$B(?:in\\s+the\\s+)?(evening|shondha|sondha|shondhay|sondhay|sondhar)$E" to Period.EVENING,
            "$B(?:at\\s+)?(night|rat|raat|rate|raate|ratre)$E" to Period.NIGHT,
        ).flatMap { (pattern, period) -> find(pattern).map { PeriodMention(it.range.first, it.range.last + 1, period) }.toList() }

    fun types(): List<TypeMention> {
        val rules =
            listOf(
                Triple("dinner\\s+date|date\\s+night|on\\s+a\\s+date|candle\\s*light", EntryType.DINNER_DATE, null),
                Triple("dinner|supper|iftar", EntryType.DINNER, null),
                Triple("lunch", EntryType.EVENT, "Lunch"),
                Triple("breakfast|brunch", EntryType.EVENT, "Breakfast"),
                Triple("meeting|meet\\s*up|meetup|meet|sit\\s+together|sit\\s+for|sync|stand\\s*up|standup|discussion|interview|call|zoom|google\\s+meet", EntryType.MEETING, null),
                Triple("match|game|cricket|football|futsal|badminton|tournament", EntryType.MATCH, null),
                Triple("tour|trip|travel|picnic|vacation|holiday|excursion|outing|journey|ghurte", EntryType.TOUR, null),
                Triple("birthday|bday|b'day|jonmodin", EntryType.BIRTHDAY, null),
                Triple("doctor|dentist|appointment|checkup|check-up|clinic|hospital|therapy", EntryType.APPOINTMENT, null),
                Triple("flight|plane|airport|boarding", EntryType.FLIGHT, null),
                Triple("exam|quiz|viva|midterm|mid-term|final\\s+exam|test", EntryType.EXAM, null),
                Triple("deadline|submit|submission|pay|send|finish|complete|buy|pick\\s+up|return", EntryType.TASK, null),
                Triple("party|wedding|biye|concert|program|programme|seminar|workshop|class|event|prayer|jummah|namaz", EntryType.EVENT, null),
            )
        val found = mutableListOf<TypeMention>()
        rules.forEach { (pattern, type, word) ->
            find("$B(?:$pattern)$E").forEach { m ->
                val period = if (type == EntryType.DINNER || type == EntryType.DINNER_DATE) Period.EVENING else null
                val titleWord = word ?: m.value.takeIf { type == EntryType.EVENT && it !in setOf("event", "program", "programme") }
                val weak = Regex("^(meet|sit\\s+together|sit\\s+for|call|sync|send|pay|buy|return|finish|complete|pick\\s+up|test)$").matches(m.value)
                found.addIfFree(TypeMention(m.range.first, m.range.last + 1, type, titleWord?.replaceFirstChar { it.uppercase() }, period, weak), emptyList())
            }
        }
        // "lunch" implies early afternoon, "breakfast" the morning.
        return found
            .map {
                when (it.titleWord) {
                    "Lunch" -> it.copy(period = Period.NOON)
                    "Breakfast" -> it.copy(period = Period.MORNING)
                    else -> it
                }
            }.sortedBy { it.start }
    }

    fun approximations(): List<FlagMention> =
        find("$B(around|about|approx\\.?|approximately|roughly|maybe|probably|tentatively|perhaps|let'?s\\s+try|lets\\s+try|try\\s+to|hoyto|mone\\s+hoy)$E|~")
            .map { FlagMention(it.range.first, it.range.last + 1, SpanKind.APPROXIMATE) }
            .toList()

    fun importance(): List<FlagMention> {
        val found = mutableListOf<FlagMention>()
        find("$B(very\\s+important|critical|must\\s+not\\s+miss|can'?t\\s+miss|cannot\\s+miss|life\\s+or\\s+death)$E").forEach {
            found.addIfFree(FlagMention(it.range.first, it.range.last + 1, SpanKind.IMPORTANCE, importance = Importance.CRITICAL), emptyList())
        }
        find("$B(important|urgent|asap|must|don'?t\\s+forget|do\\s+not\\s+forget|dont\\s+forget|zaruri|jaruri|joruri|onek\\s+important)$E").forEach {
            found.addIfFree(FlagMention(it.range.first, it.range.last + 1, SpanKind.IMPORTANCE, importance = Importance.IMPORTANT), emptyList())
        }
        return found
    }

    fun changes(): List<FlagMention> {
        val found = mutableListOf<FlagMention>()
        find(
            "$B(cancel|cancell?ed|called\\s+off|call\\s+off|is\\s+off|it'?s\\s+off|not\\s+happening|won'?t\\s+happen|" +
                "will\\s+not\\s+happen|hobe\\s+na|bad\\s+(?:dao|dilam|dite\\s+hobe)|batil)$E",
        ).forEach {
            found.addIfFree(FlagMention(it.range.first, it.range.last + 1, SpanKind.CHANGE, change = ChangeRequest.Kind.CANCEL), emptyList())
        }
        find(
            "$B(move|moved|shift|shifted|postpone|postponed|reschedule|rescheduled|push|pushed|delay|delayed|prepone|preponed|" +
                "change|changed|instead|make\\s+it|let'?s\\s+do\\s+it|bring\\s+(?:it\\s+)?forward|pichiye|egiye)$E",
        ).forEach {
            found.addIfFree(FlagMention(it.range.first, it.range.last + 1, SpanKind.CHANGE, change = ChangeRequest.Kind.MOVE), emptyList())
        }
        return found.sortedBy { it.start }
    }

    /** "with Nabila", "with my team", "with Rafi and Tania". Names keep their original capitalisation. */
    fun people(): List<PeopleMention> {
        val stop =
            setOf(
                "at",
                "on",
                "in",
                "tomorrow",
                "today",
                "tonight",
                "around",
                "about",
                "from",
                "for",
                "to",
                "by",
                "this",
                "next",
                "and",
                "the",
                "after",
                "before",
                "it",
                "us",
                "me",
                "you",
                "him",
                "her",
                "them",
            )
        return find("$B(with|sathe|shathe)\\s+")
            .mapNotNull { m ->
                val words = mutableListOf<String>()
                var index = m.range.last + 1
                val wordRegex = Regex("^[\\p{L}][\\p{L}'.-]*")
                var end = index
                while (words.size < 4) {
                    val rest = text.substring(index)
                    val word = wordRegex.find(rest)?.value ?: break
                    val lower = word.lowercase().trimEnd('.', '\'')
                    if (lower in stop && !(lower == "and" && words.isNotEmpty())) break
                    if (lower in setOf("my", "our", "the")) {
                        index += word.length
                        index += text.substring(index).takeWhile { it == ' ' }.length
                        continue
                    }
                    words += word.trimEnd('.')
                    end = index + word.length
                    index = end + text.substring(end).takeWhile { it == ' ' }.length
                    if (index >= text.length) break
                }
                val names =
                    words
                        .joinToString(" ")
                        .split(Regex("\\s+(?:and|&)\\s+|,\\s*"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && it.lowercase() !in stop }
                if (names.isEmpty()) null else PeopleMention(m.range.first, end, names)
            }.toList()
    }

    /** "at Gulshan 2", "@ Cafe Mango", "at the office". */
    fun locations(taken: List<Mention>): List<LocationMention> {
        val found = mutableListOf<LocationMention>()
        val known = "office|home|campus|university|varsity|school|college|restaurant|cafe|café|gym|mosque|masjid|club|hall|library|airport|hospital"
        Regex("(?<![\\p{L}\\d])(?i:at|@|in)\\s+((?:the\\s+|my\\s+|our\\s+)?(?:$known))(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
            .findAll(text)
            .forEach { m -> found.addIfFree(LocationMention(m.range.first, m.range.last + 1, m.groupValues[1].removePrefix("the ").trim()), taken) }
        val excluded = WEEKDAYS.keys + MONTHS.keys + setOf("i", "noon", "night", "morning", "evening", "the", "sha", "shaa", "sha allah", "shaa allah")
        Regex("(?<![\\p{L}\\d])(?:[Aa]t|@|[Ii]n)\\s+([A-Z][\\p{L}'-]*(?:\\s+(?:[A-Z][\\p{L}'-]*|\\d{1,3}(?![:.\\d]|\\s*(?:am|pm|AM|PM)))){0,3})")
            .findAll(text)
            .forEach { m ->
                val place = m.groupValues[1].trim()
                if (place.lowercase() !in excluded) found.addIfFree(LocationMention(m.range.first, m.range.last + 1, place), taken)
            }
        return found
    }

    fun tasks(): List<TaskMention> =
        find("$B(remind\\s+me\\s+to|don'?t\\s+forget\\s+to|dont\\s+forget\\s+to|remember\\s+to|need\\s+to|have\\s+to|must)\\s+")
            .map { TaskMention(it.range.first, it.range.last + 1, it.range.last + 1) }
            .toList()
}

/** Parser settings. [weekendStart] decides what "this weekend" means (Friday in Bangladesh, Saturday elsewhere). */
data class ParserConfig(
    val weekendStart: DayOfWeek = DayOfWeek.SATURDAY,
)
