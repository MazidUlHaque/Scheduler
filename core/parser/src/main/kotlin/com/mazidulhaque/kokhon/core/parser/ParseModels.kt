package com.mazidulhaque.kokhon.core.parser

import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import java.time.LocalDate
import java.time.LocalTime

/** What a highlighted part of the message was understood as. */
enum class SpanKind { DATE, TIME, TYPE, APPROXIMATE, PEOPLE, LOCATION, IMPORTANCE, CHANGE }

/**
 * A highlighted part of the original message.
 *
 * @property start index of the first character in the original text.
 * @property end index after the last character.
 * @property guessed true when the app is not sure and the user should check it (shown dashed).
 */
data class Span(
    val start: Int,
    val end: Int,
    val kind: SpanKind,
    val guessed: Boolean = false,
)

/**
 * One plan found in a message. [date] and [time] are never null in a draft;
 * [dateGuessed] / [timeGuessed] say whether they came from the text or were filled in.
 *
 * @property alternativeTime the other reading of an ambiguous time ("9:30" could be AM or PM).
 */
data class PlanDraft(
    val title: String,
    val type: EntryType,
    val date: LocalDate,
    val time: LocalTime,
    val endTime: LocalTime? = null,
    val dateGuessed: Boolean = false,
    val timeGuessed: Boolean = false,
    val alternativeTime: LocalTime? = null,
    val approximate: Boolean = false,
    val location: String? = null,
    val people: List<String> = emptyList(),
    val importance: Importance = type.defaultImportance,
    val spans: List<Span> = emptyList(),
    /** 0..1, how sure the parser is overall. */
    val confidence: Float = 0.5f,
)

/** The message talks about changing an existing plan rather than making a new one. */
data class ChangeRequest(
    val kind: Kind,
    /** The new date, when the message gives one ("move it to Friday"). */
    val newDate: LocalDate? = null,
    /** The new time, when the message gives one ("make it 10 pm"). */
    val newTime: LocalTime? = null,
    /** True when [newTime] had no am/pm, so the matcher picks the reading closest to the old time. */
    val newTimeAmbiguous: Boolean = false,
    /** A date that identifies which plan is meant ("tomorrow's meeting"). */
    val identifyingDate: LocalDate? = null,
    val type: EntryType? = null,
    val people: List<String> = emptyList(),
    /** Meaningful words of the message, used to match plan titles. */
    val words: Set<String> = emptySet(),
    val spans: List<Span> = emptyList(),
) {
    enum class Kind { MOVE, CANCEL }
}

data class ParseResult(
    val text: String,
    val drafts: List<PlanDraft>,
    val change: ChangeRequest?,
) {
    val foundAnything: Boolean get() = drafts.isNotEmpty() || change != null
}
