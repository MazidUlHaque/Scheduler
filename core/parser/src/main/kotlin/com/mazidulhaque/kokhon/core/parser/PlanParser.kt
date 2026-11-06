package com.mazidulhaque.kokhon.core.parser

import com.mazidulhaque.kokhon.core.model.EntryType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Turns a chat message into plans, entirely on the device.
 *
 * "let's try to sit for a meeting tomorrow around 9:30 pm" becomes a Meeting tomorrow at 21:30,
 * marked approximate, with each understood word highlighted so the user can check it.
 */
class PlanParser(
    private val config: ParserConfig = ParserConfig(),
) {
    /**
     * @param allowChange when false, a message like "move the meeting to 10" is read as a new plan instead of a change.
     */
    fun parse(
        text: String,
        now: ZonedDateTime,
        allowChange: Boolean = true,
    ): ParseResult {
        if (text.isBlank()) return ParseResult(text, emptyList(), null)
        val d = Detectors(text, now, config)
        val times = d.times()
        val dates = d.dates(times)
        val temporal: List<Mention> = times + dates
        val periods = d.periods().filter { p -> temporal.none { it.overlaps(p) } }
        val types = d.types().filter { t -> temporal.none { it.overlaps(t) } }
        val approx = d.approximations().filter { a -> temporal.none { it.overlaps(a) } }
        val importance = d.importance()
        val changes = d.changes()
        val people = d.people()
        val locations = d.locations(temporal + types)
        val tasks = d.tasks()
        val all = Mentions(times, dates, periods, types, approx, importance, changes, people, locations, tasks)

        val change = if (allowChange) changeRequest(d, all, now) else null
        val drafts = mutableListOf<PlanDraft>()
        if (change == null) {
            chunks(text, all).forEach { chunk ->
                // "meeting at 10am tomorrow and dinner at 8pm": the dinner is tomorrow too.
                val inherited = drafts.lastOrNull()?.takeIf { !it.dateGuessed && chunk.mentions.dates.isEmpty() }?.date
                draft(text, d.lc, chunk, now, inherited)?.let { drafts += it }
            }
        }
        return ParseResult(text, drafts, change)
    }

    internal data class Mentions(
        val times: List<TimeMention>,
        val dates: List<DateMention>,
        val periods: List<PeriodMention>,
        val types: List<TypeMention>,
        val approx: List<FlagMention>,
        val importance: List<FlagMention>,
        val changes: List<FlagMention>,
        val people: List<PeopleMention>,
        val locations: List<LocationMention>,
        val tasks: List<TaskMention>,
    ) {
        fun within(range: IntRange): Mentions {
            fun <T : Mention> List<T>.inside() = filter { it.start in range }
            return Mentions(
                times.inside(),
                dates.inside(),
                periods.inside(),
                types.inside(),
                approx.inside(),
                importance.inside(),
                changes.inside(),
                people.inside(),
                locations.inside(),
                tasks.inside(),
            )
        }

        val hasWhen: Boolean get() = times.isNotEmpty() || dates.isNotEmpty()
    }

    private data class Chunk(
        val range: IntRange,
        val mentions: Mentions,
    )

    /**
     * Splits the message into one chunk per plan: by sentence, and within a sentence when it holds
     * two separate times each with its own kind of plan ("meeting at 10 and dinner at 8").
     */
    private fun chunks(
        text: String,
        all: Mentions,
    ): List<Chunk> {
        val boundaries = Regex("(?<!\\d)[.!?;\\n]+(?=\\s|$)|\\n").findAll(text).map { it.range.last + 1 }.toList()
        val sentenceRanges = mutableListOf<IntRange>()
        var start = 0
        (boundaries + text.length).forEach { end ->
            if (end > start) sentenceRanges += start until end
            start = end
        }
        val pieces = mutableListOf<IntRange>()
        sentenceRanges.forEach { range ->
            val m = all.within(range)
            if (m.times.size >= 2 && m.types.size >= 2) {
                // Cut just before the second kind of plan, at a preceding "and" / "," / "then" if there is one.
                val secondType = m.types[1].start
                val cut =
                    Regex("(?:,|\\band\\b|\\bthen\\b)[^,]*$")
                        .find(text.substring(range.first, secondType))
                        ?.range
                        ?.first
                        ?.plus(range.first) ?: secondType
                pieces += range.first until cut
                pieces += cut..range.last
            } else {
                pieces += range
            }
        }

        // Merge pieces that only make sense together: "Tomorrow. Meeting at 9" or a sentence with no time at all.
        // A separate line with no time and no kind of plan ("haha that was fun" in a chat) is ignored.
        fun ownLine(range: IntRange) = range.first == 0 || text.substring(0, range.first).trimEnd(' ', '\t').endsWith('\n')

        fun isChatter(range: IntRange): Boolean {
            val m = all.within(range)
            return !m.hasWhen && m.types.isEmpty() && m.tasks.isEmpty() && text.contains('\n') && ownLine(range)
        }
        val merged = mutableListOf<IntRange>()
        pieces.filterNot { isChatter(it) }.forEach { piece ->
            val previous = merged.lastOrNull()
            if (previous == null) {
                merged += piece
                return@forEach
            }
            val a = all.within(previous)
            val b = all.within(piece)
            val complements =
                (a.dates.isNotEmpty() && a.times.isEmpty() && b.times.isNotEmpty() && b.dates.isEmpty()) ||
                    (a.times.isNotEmpty() && a.dates.isEmpty() && b.dates.isNotEmpty() && b.times.isEmpty())
            if (complements || !b.hasWhen || !a.hasWhen) {
                merged[merged.lastIndex] = previous.first..piece.last
            } else {
                merged += piece
            }
        }
        return merged.map { Chunk(it, all.within(it)) }.filter { it.mentions.hasWhen }
    }

    private fun draft(
        text: String,
        lc: String,
        chunk: Chunk,
        now: ZonedDateTime,
        inheritedDate: LocalDate? = null,
    ): PlanDraft? {
        val m = chunk.mentions
        val dateMention = m.dates.firstOrNull()
        val timeMention = m.times.firstOrNull()
        if (dateMention == null && timeMention == null) return null
        // With several kinds of plan mentioned ("our game ... a meeting tomorrow"), the one next to the date or time wins.
        val anchors: List<Mention> = listOfNotNull(dateMention, timeMention)
        val typeMention =
            m.types
                .filter { !it.weak }
                .ifEmpty { m.types }
                // A specific kind (birthday, meeting) beats a general one (party, event) mentioned alongside it.
                .let { list -> list.filter { it.type != EntryType.EVENT }.ifEmpty { list } }
                .minByOrNull { t -> anchors.minOfOrNull { kotlin.math.abs(it.start - t.start) } ?: t.start }
        val task = m.tasks.firstOrNull()
        val type = typeMention?.type ?: if (task != null) EntryType.TASK else EntryType.OTHER
        val period = dateMention?.period ?: m.periods.firstOrNull()?.period ?: typeMention?.period

        val spans = mutableListOf<Span>()
        var dateGuessed = false
        var timeGuessed = false
        var alternative: LocalTime? = null

        // ---- time
        var time: LocalTime
        var endTime: LocalTime? = null
        var dayOffset = 0L
        if (timeMention?.absolute != null) {
            time =
                timeMention.absolute
                    .toLocalTime()
                    .withSecond(0)
                    .withNano(0)
        } else if (timeMention != null) {
            val resolved =
                resolveMeridiem(
                    timeMention.hour,
                    timeMention.minute,
                    timeMention.meridiem,
                    period,
                    type,
                    dateMention?.date ?: inheritedDate,
                    now,
                )
            time = resolved.time
            alternative = resolved.alternative
            timeGuessed = resolved.alternative != null
            if (timeMention.hour == 0 && timeMention.meridiem == Meridiem.AM &&
                lc.substring(timeMention.start, timeMention.end) == "midnight"
            ) {
                dayOffset = 1
            }
            if (timeMention.endHour != null) {
                endTime =
                    resolveMeridiem(
                        timeMention.endHour,
                        timeMention.endMinute ?: 0,
                        timeMention.endMeridiem ?: resolved.meridiem,
                        period,
                        type,
                        null,
                        now,
                    ).time
            }
        } else {
            // No time given: dinner means 8 PM; otherwise a stated part of the day, then the plan's usual time.
            val statedPeriod = dateMention?.period ?: m.periods.firstOrNull()?.period
            val hour =
                when {
                    type == EntryType.DINNER || type == EntryType.DINNER_DATE -> 20
                    statedPeriod != null -> statedPeriod.defaultHour
                    typeMention?.period != null -> typeMention.period.defaultHour
                    else -> type.defaultHour()
                }
            time = LocalTime.of(hour, 0)
            timeGuessed = true
        }

        // ---- date
        val date: LocalDate =
            when {
                timeMention?.absolute != null -> {
                    timeMention.absolute.toLocalDate()
                }

                dateMention != null -> {
                    dateGuessed = dateMention.guessed
                    dateMention.date.plusDays(dayOffset)
                }

                inheritedDate != null -> {
                    inheritedDate.plusDays(dayOffset)
                }

                else -> {
                    // A time without a date: today if it is still ahead, otherwise tomorrow.
                    val today = now.toLocalDate().plusDays(dayOffset)
                    if (today.atTime(time).isBefore(now.toLocalDateTime().minusMinutes(5))) {
                        dateGuessed = true
                        today.plusDays(1)
                    } else {
                        today
                    }
                }
            }

        dateMention?.let { spans += Span(it.start, it.end, SpanKind.DATE, it.guessed) }
        timeMention?.let { spans += Span(it.start, it.end, SpanKind.TIME, timeGuessed) }
        m.periods.forEach { spans += Span(it.start, it.end, SpanKind.TIME, guessed = timeMention == null) }
        typeMention?.let { spans += Span(it.start, it.end, SpanKind.TYPE) }

        // ---- approximate: "around 9", "9ish", "maybe", "let's try"
        val approxMentions =
            m.approx.filter { a ->
                val word = lc.substring(a.start, a.end)
                val nearTime = timeMention != null && a.end <= timeMention.start && timeMention.start - a.end <= 12
                nearTime || word !in setOf("around", "about", "~")
            }
        val approximate = approxMentions.isNotEmpty() || timeMention?.approximate == true
        approxMentions.forEach { spans += Span(it.start, it.end, SpanKind.APPROXIMATE) }

        // ---- importance
        val stated = m.importance.maxByOrNull { it.importance!!.ordinal }
        stated?.let { spans += Span(it.start, it.end, SpanKind.IMPORTANCE) }
        val importance = listOfNotNull(stated?.importance, type.defaultImportance).maxBy { it.ordinal }

        // ---- people and place
        val people = m.people.flatMap { it.people }
        m.people.forEach { spans += Span(it.start, it.end, SpanKind.PEOPLE) }
        val location = m.locations.firstOrNull()
        location?.let { spans += Span(it.start, it.end, SpanKind.LOCATION) }

        val title = title(text, chunk, type, typeMention, task, people, location)

        var confidence = 0.35f
        if (dateMention != null) confidence += 0.2f
        if (timeMention != null) confidence += 0.25f
        if (typeMention != null) confidence += 0.15f
        if (timeGuessed) confidence -= 0.1f
        if (dateGuessed) confidence -= 0.05f

        return PlanDraft(
            title = title,
            type = type,
            date = date,
            time = time,
            endTime = endTime,
            dateGuessed = dateGuessed,
            timeGuessed = timeGuessed,
            alternativeTime = alternative,
            approximate = approximate,
            location = location?.place,
            people = people,
            importance = importance,
            spans = spans.sortedBy { it.start },
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    private data class Resolved(
        val time: LocalTime,
        val meridiem: Meridiem?,
        val alternative: LocalTime?,
    )

    /**
     * Decides AM or PM for a time like "9:30". In order: the message said it; the hour is unambiguous (13-23, 0);
     * a part of the day ("tonight", "morning") or the kind of plan (dinner) settles it; 1-6 is almost always PM;
     * otherwise pick the next one that is still ahead and offer the other as an alternative.
     */
    private fun resolveMeridiem(
        hour: Int,
        minute: Int,
        meridiem: Meridiem?,
        period: Period?,
        type: EntryType,
        date: LocalDate?,
        now: ZonedDateTime,
    ): Resolved {
        fun at(
            h: Int,
            mer: Meridiem?,
        ) = Resolved(LocalTime.of(h % 24, minute), mer, null)

        if (meridiem != null) {
            val h24 =
                when {
                    meridiem == Meridiem.AM && hour == 12 -> 0
                    meridiem == Meridiem.PM && hour < 12 -> hour + 12
                    else -> hour
                }
            return at(h24, meridiem)
        }
        if (hour == 0 || hour > 12) return at(hour, null)
        val hint = period?.meridiem
        if (hint != null) {
            return when {
                hint == Meridiem.AM -> at(if (hour == 12) 0 else hour, Meridiem.AM)
                hour == 12 -> at(12, Meridiem.PM)
                else -> at(hour + 12, Meridiem.PM)
            }
        }
        if (hour == 12) return at(12, Meridiem.PM)
        val am = LocalTime.of(hour, minute)
        val pm = LocalTime.of(hour + 12, minute)
        if (hour in 1..6) return Resolved(pm, Meridiem.PM, am)
        // Work-type plans at 7-11 are almost always in the morning.
        if (type in MORNING_TYPES) return Resolved(am, Meridiem.AM, pm)
        // 7-11: if the morning time has already passed today, the evening one is meant.
        val day = date ?: now.toLocalDate()
        val amPassed = day == now.toLocalDate() && am.isBefore(now.toLocalTime())
        val pmPassed = day == now.toLocalDate() && pm.isBefore(now.toLocalTime())
        return if (amPassed && !pmPassed) Resolved(pm, Meridiem.PM, am) else Resolved(am, Meridiem.AM, pm)
    }

    private fun EntryType.defaultHour(): Int =
        when (this) {
            EntryType.DINNER, EntryType.DINNER_DATE -> 20
            EntryType.MATCH -> 16
            EntryType.BIRTHDAY -> 19
            EntryType.TASK -> 10
            else -> 9
        }

    private fun title(
        text: String,
        chunk: Chunk,
        type: EntryType,
        typeMention: TypeMention?,
        task: TaskMention?,
        people: List<String>,
        location: LocationMention?,
    ): String {
        val m = chunk.mentions
        val segment = text.substring(chunk.range.first, chunk.range.last + 1)

        // "Bangladesh vs Sri Lanka"
        Regex(
            "([\\p{L}][\\p{L}.'-]*(?:\\s+[A-Z][\\p{L}.'-]*)*)\\s+(?:vs\\.?|v\\.?|versus)\\s+([\\p{L}][\\p{L}.'-]*(?:\\s+[A-Z][\\p{L}.'-]*)*)",
        ).find(segment)
            ?.let { return "${capitalise(it.groupValues[1])} vs ${capitalise(it.groupValues[2])}" }

        // "Rafi's birthday"
        if (type == EntryType.BIRTHDAY) {
            Regex("([\\p{L}]+)(?:'s|’s)\\s+(?:birthday|bday|b'day)", RegexOption.IGNORE_CASE).find(segment)?.let {
                return "${capitalise(it.groupValues[1])}'s birthday"
            }
        }

        // "remind me to send the budget sheet tomorrow" -> "Send the budget sheet"
        if (task != null) {
            val removable: List<Mention> = m.times + m.dates + m.periods + m.approx + m.importance + m.people + m.locations
            val raw =
                buildString {
                    var i = task.titleStart
                    val end = chunk.range.last + 1
                    while (i < end) {
                        val hit = removable.firstOrNull { i >= it.start && i < it.end }
                        if (hit != null) {
                            append(' ')
                            i = hit.end
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                }
            val cleaned = cleanup(raw)
            if (cleaned.isNotBlank()) return capitalise(cleaned)
        }

        // A message written in Bangla script gets a Bangla title.
        val bangla = segment.any { it in '\u0980'..'\u09FF' }
        val base =
            (if (bangla) banglaLabel(type) else null)
                ?: typeMention?.titleWord
                ?: typeLabel(type).takeIf { type != EntryType.TASK }
        if (base != null) {
            return when {
                bangla -> base
                people.isNotEmpty() -> "$base with ${people.joinToString(" & ")}"
                location != null && type != EntryType.MEETING -> "$base at ${location.place}"
                else -> base
            }
        }
        // Unknown kind: use the message itself, without the date and time words.
        val removable: List<Mention> = m.times + m.dates + m.periods + m.approx + m.importance + m.changes + m.locations
        val raw =
            buildString {
                var i = chunk.range.first
                while (i <= chunk.range.last) {
                    val hit = removable.firstOrNull { i >= it.start && i < it.end }
                    if (hit != null) {
                        append(' ')
                        i = hit.end
                    } else {
                        append(text[i])
                        i++
                    }
                }
            }
        val cleaned = cleanup(raw)
        return if (cleaned.isBlank()) "Plan" else capitalise(cleaned.take(48).trim())
    }

    private fun banglaLabel(type: EntryType): String? =
        when (type) {
            EntryType.MEETING -> "মিটিং"
            EntryType.EVENT -> "অনুষ্ঠান"
            EntryType.DINNER -> "ডিনার"
            EntryType.DINNER_DATE -> "ডিনার ডেট"
            EntryType.TOUR -> "ভ্রমণ"
            EntryType.MATCH -> "খেলা"
            EntryType.BIRTHDAY -> "জন্মদিন"
            EntryType.APPOINTMENT -> "অ্যাপয়েন্টমেন্ট"
            EntryType.FLIGHT -> "ফ্লাইট"
            EntryType.EXAM -> "পরীক্ষা"
            EntryType.TASK, EntryType.OTHER -> null
        }

    private fun typeLabel(type: EntryType): String? =
        when (type) {
            EntryType.MEETING -> "Meeting"
            EntryType.EVENT -> "Event"
            EntryType.DINNER -> "Dinner"
            EntryType.DINNER_DATE -> "Dinner date"
            EntryType.TOUR -> "Trip"
            EntryType.MATCH -> "Match"
            EntryType.BIRTHDAY -> "Birthday"
            EntryType.APPOINTMENT -> "Appointment"
            EntryType.FLIGHT -> "Flight"
            EntryType.EXAM -> "Exam"
            EntryType.TASK -> "Task"
            EntryType.OTHER -> null
        }

    private val fillers =
        Regex(
            "(?i)(?<![\\p{L}])(let'?s|lets|we|we'll|will|can|could|should|shall|please|pls|plz|ok|okay|guys|hey|hi|" +
                "so|then|also|just|try\\s+to|going\\s+to|gonna|i|i'll|on|at|for|by|the|a|an|is|are|be|there)(?![\\p{L}])",
        )

    private fun cleanup(raw: String): String {
        var s = raw.replace(Regex("[\\p{So}\\p{Sk}]"), " ")
        // Trim filler words from both ends only, so the middle of a title stays readable.
        repeat(6) {
            s = s.trim(' ', ',', '.', '!', '?', ';', ':', '-', '\n')
            s = s.replace(Regex("^(?:${fillers.pattern})\\s+"), "").replace(Regex("\\s+(?:${fillers.pattern})$"), "")
        }
        return s.replace(Regex("\\s{2,}"), " ").trim(' ', ',', '.', '!', '?', ';', ':', '-')
    }

    private fun capitalise(value: String): String = value.trim().replaceFirstChar { it.uppercaseChar() }

    // ---------------------------------------------------------------- changes

    /** Recognises "shift tomorrow's meeting to 10 pm instead" or "the dinner is cancelled". */
    private fun changeRequest(
        d: Detectors,
        m: Mentions,
        now: ZonedDateTime,
    ): ChangeRequest? {
        val keyword = m.changes.firstOrNull() ?: return null
        val kind = if (m.changes.any { it.change == ChangeRequest.Kind.CANCEL }) ChangeRequest.Kind.CANCEL else ChangeRequest.Kind.MOVE
        // A "change" word alone ("I changed my mind") is not enough: there must be something to change.
        if (kind == ChangeRequest.Kind.MOVE && m.times.isEmpty() && m.dates.isEmpty() && m.types.isEmpty()) return null

        val keywordPos = keyword.start
        val keywordIsTrailing = m.changes.all { it.start > (m.times.maxOfOrNull { t -> t.start } ?: -1) } // "10 pm instead"
        val newTimeMention =
            m.times.firstOrNull { it.start > keywordPos } ?: m.times.lastOrNull().takeIf { keywordIsTrailing }
        val identifying =
            m.dates.firstOrNull { it.possessive }
                ?: m.dates.firstOrNull { it.start < keywordPos && newTimeMention != null && !keywordIsTrailing }
        val newDateMention = m.dates.firstOrNull { it != identifying && !it.possessive && (it.start > keywordPos || keywordIsTrailing) }

        val type = m.types.firstOrNull()?.type
        val period = newDateMention?.period ?: m.periods.firstOrNull { it.start > keywordPos }?.period
        var newTime: LocalTime? = null
        var ambiguous = false
        if (newTimeMention != null) {
            if (newTimeMention.absolute != null) {
                newTime =
                    newTimeMention.absolute
                        .toLocalTime()
                        .withSecond(0)
                        .withNano(0)
            } else {
                val hour = newTimeMention.hour
                ambiguous = newTimeMention.meridiem == null && period == null && hour in 1..12
                newTime =
                    resolveMeridiem(hour, newTimeMention.minute, newTimeMention.meridiem, period, type ?: EntryType.OTHER, null, now).time
            }
        }
        val words =
            Regex("[\\p{L}]{3,}")
                .findAll(d.lc)
                .map { it.value }
                .filter { it !in MATCH_STOP_WORDS }
                .toSet()

        val spans =
            buildList {
                m.changes.forEach { add(Span(it.start, it.end, SpanKind.CHANGE)) }
                newTimeMention?.let { add(Span(it.start, it.end, SpanKind.TIME, guessed = ambiguous)) }
                newDateMention?.let { add(Span(it.start, it.end, SpanKind.DATE, it.guessed)) }
                identifying?.let { add(Span(it.start, it.end, SpanKind.DATE)) }
                m.types.firstOrNull()?.let { add(Span(it.start, it.end, SpanKind.TYPE)) }
                m.people.forEach { add(Span(it.start, it.end, SpanKind.PEOPLE)) }
            }.sortedBy { it.start }

        return ChangeRequest(
            kind = kind,
            newDate = newDateMention?.date,
            newTime = newTime,
            newTimeAmbiguous = ambiguous,
            identifyingDate = identifying?.date,
            type = type,
            people = m.people.flatMap { it.people },
            words = words,
            spans = spans,
        )
    }

    internal companion object {
        val MORNING_TYPES = setOf(EntryType.MEETING, EntryType.APPOINTMENT, EntryType.EXAM, EntryType.TASK, EntryType.FLIGHT)

        val MATCH_STOP_WORDS =
            setOf(
                "the",
                "and",
                "let",
                "lets",
                "let's",
                "shift",
                "move",
                "moved",
                "make",
                "instead",
                "cancel",
                "cancelled",
                "canceled",
                "postpone",
                "postponed",
                "reschedule",
                "tomorrow",
                "today",
                "tonight",
                "guys",
                "our",
                "with",
                "for",
                "from",
                "will",
                "not",
                "happening",
                "called",
                "off",
                "change",
                "changed",
                "push",
                "pushed",
                "delay",
                "delayed",
                "please",
                "can",
                "about",
                "around",
                "this",
                "next",
                "that",
                "its",
                "it's",
                "has",
                "have",
                "been",
                "now",
                "there",
                "hobe",
                "kal",
            )
    }
}
