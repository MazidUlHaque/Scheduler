package com.mazidulhaque.kokhon.core.model

import java.time.Duration
import java.time.Instant

/** How a single reminder presents itself. Ordered from quietest to loudest. */
enum class ReminderStyle {
    /** Only visible in the timeline and widgets. */
    SILENT,

    /** A normal notification with sound. */
    NOTIFICATION,

    /** A notification that pops up on screen. */
    HEADS_UP,

    /** A full-screen alarm that rings until stopped, snoozed or the ring limit ends. */
    ALARM,
}

/**
 * One step in a reminder chain, e.g. "15 minutes before, as a notification".
 *
 * @property offset how long before the entry starts this reminder fires. Must not be negative.
 * @property enabled lets the user turn off a single step without deleting it.
 */
data class ReminderStep(
    val offset: Duration,
    val style: ReminderStyle,
    val enabled: Boolean = true,
) {
    init {
        require(!offset.isNegative) { "A reminder offset can't be negative: $offset" }
    }

    /** The moment this step fires for an entry starting at [start]. */
    fun fireTime(start: Instant): Instant = start.minus(offset)
}

/**
 * An ordered list of reminders for one entry, earliest first.
 *
 * @property enabled the master switch. When false, no step fires, but the steps are kept
 * so the user can turn them back on.
 */
data class ReminderChain(
    val steps: List<ReminderStep>,
    val enabled: Boolean = true,
) {
    /** Steps sorted earliest first (largest offset first). */
    val orderedSteps: List<ReminderStep>
        get() = steps.sortedByDescending { it.offset }

    /**
     * The steps that should still fire for an entry starting at [start], given the current time [now].
     * Returns an empty list when the chain is turned off.
     */
    fun pendingSteps(
        start: Instant,
        now: Instant,
    ): List<ReminderStep> {
        if (!enabled) return emptyList()
        return orderedSteps.filter { it.enabled && it.fireTime(start).isAfter(now) }
    }

    fun withStep(step: ReminderStep): ReminderChain = copy(steps = steps + step)

    fun withoutStep(step: ReminderStep): ReminderChain = copy(steps = steps - step)

    companion object {
        val NONE = ReminderChain(emptyList())

        private fun minutes(value: Long) = Duration.ofMinutes(value)

        /** The default chain for each importance level. Users can change these in Settings. */
        fun defaultFor(importance: Importance): ReminderChain =
            when (importance) {
                Importance.LOW -> {
                    ReminderChain(listOf(ReminderStep(minutes(10), ReminderStyle.NOTIFICATION)))
                }

                Importance.NORMAL -> {
                    ReminderChain(listOf(ReminderStep(minutes(15), ReminderStyle.NOTIFICATION)))
                }

                Importance.IMPORTANT -> {
                    ReminderChain(
                        listOf(
                            ReminderStep(minutes(30), ReminderStyle.NOTIFICATION),
                            ReminderStep(minutes(15), ReminderStyle.NOTIFICATION),
                            ReminderStep(minutes(5), ReminderStyle.ALARM),
                        ),
                    )
                }

                Importance.CRITICAL -> {
                    ReminderChain(
                        listOf(
                            ReminderStep(Duration.ofDays(1), ReminderStyle.NOTIFICATION),
                            ReminderStep(Duration.ofHours(1), ReminderStyle.HEADS_UP),
                            ReminderStep(minutes(30), ReminderStyle.ALARM),
                            ReminderStep(minutes(5), ReminderStyle.ALARM),
                        ),
                    )
                }
            }
    }
}
