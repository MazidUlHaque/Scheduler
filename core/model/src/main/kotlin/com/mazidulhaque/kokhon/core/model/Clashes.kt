package com.mazidulhaque.kokhon.core.model

import java.time.Duration
import java.time.Instant

/** A plan with no end time is treated as lasting this long when looking for clashes. */
private val ASSUMED_LENGTH: Duration = Duration.ofMinutes(30)

private fun Entry.isActive() = manualStatus != EntryStatus.CANCELLED && manualStatus != EntryStatus.COMPLETED

private fun Entry.endForClash(): Instant = start.plus(duration?.takeIf { !it.isZero && !it.isNegative } ?: ASSUMED_LENGTH)

/** Other active plans whose time overlaps this one. All-day plans and tasks never clash. */
fun Entry.clashesIn(others: Iterable<Entry>): List<Entry> {
    if (allDay || type == EntryType.TASK || !isActive()) return emptyList()
    return others
        .filter { it.id != id || id == 0L }
        .filter { it.isActive() && !it.allDay && it.type != EntryType.TASK }
        .filter { start.isBefore(it.endForClash()) && it.start.isBefore(endForClash()) }
        .sortedBy { it.start }
}

/**
 * A saved plan that looks like the same one: the same message was captured before,
 * or the same name at the same time.
 */
fun Entry.duplicateIn(others: Iterable<Entry>): Entry? =
    others.firstOrNull { other ->
        other.id != id &&
            other.isActive() &&
            (
                (source.originalText != null && source.originalText == other.source.originalText && start == other.start) ||
                    (title.trim().equals(other.title.trim(), ignoreCase = true) && start == other.start)
            )
    }
