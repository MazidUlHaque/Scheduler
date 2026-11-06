package com.mazidulhaque.kokhon.core.model

import java.time.Duration
import java.time.Instant

/** Where an entry is in its life. */
enum class EntryStatus {
    UPCOMING,
    ONGOING,
    COMPLETED,
    CANCELLED,

    /** A task whose due time passed without being ticked off. */
    MISSED,
}

/** Where the entry came from, so it is always traceable to the original message. */
data class EntrySource(
    val kind: Kind,
    val originalText: String? = null,
    val capturedAt: Instant? = null,
) {
    enum class Kind { MANUAL, PASTED_TEXT, SHARED_TEXT, SCREENSHOT }

    companion object {
        val MANUAL = EntrySource(Kind.MANUAL)
    }
}

/**
 * A single plan: a meeting, task, dinner, match, tour and so on.
 *
 * @property isApproximate true when the time came from words like "around" or "maybe".
 * @property manualStatus a status the user set explicitly (done, cancelled), overriding the time-based one.
 */
data class Entry(
    val id: Long = 0,
    val title: String,
    val type: EntryType,
    val start: Instant,
    val duration: Duration? = null,
    val allDay: Boolean = false,
    val isApproximate: Boolean = false,
    val location: String? = null,
    val notes: String? = null,
    val people: List<String> = emptyList(),
    val importance: Importance = type.defaultImportance,
    val reminders: ReminderChain = ReminderChain.defaultFor(importance),
    val manualStatus: EntryStatus? = null,
    val source: EntrySource = EntrySource.MANUAL,
) {
    val end: Instant? get() = duration?.let { start.plus(it) }

    /**
     * The status shown to the user at time [now]. Past events become COMPLETED automatically,
     * past tasks become MISSED unless the user ticked them off.
     */
    fun statusAt(now: Instant): EntryStatus {
        manualStatus?.let { return it }
        val finish = end ?: start
        return when {
            now.isBefore(start) -> EntryStatus.UPCOMING
            now.isBefore(finish) -> EntryStatus.ONGOING
            type == EntryType.TASK -> EntryStatus.MISSED
            else -> EntryStatus.COMPLETED
        }
    }

    /** True when the entry should be shown faded with a "done" style. */
    fun isFinishedAt(now: Instant): Boolean =
        when (statusAt(now)) {
            EntryStatus.COMPLETED, EntryStatus.CANCELLED, EntryStatus.MISSED -> true
            EntryStatus.UPCOMING, EntryStatus.ONGOING -> false
        }

    /** Moves the entry to a new start time. The reminder chain moves with it automatically. */
    fun rescheduledTo(newStart: Instant): Entry = copy(start = newStart, manualStatus = null)

    /** Changes importance and replaces the reminders with that level's default chain. */
    fun withImportance(level: Importance): Entry = copy(importance = level, reminders = ReminderChain.defaultFor(level))
}
