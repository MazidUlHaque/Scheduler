package com.mazidulhaque.kokhon.core.model

import java.time.Duration
import java.time.Instant

/**
 * One reminder that should be registered with the system alarm service.
 *
 * @property stepIndex position of the step in [ReminderChain.orderedSteps] (earliest first).
 * @property isLast true when no later step exists in the chain, so the alarm screen can say
 * "This was the last reminder".
 */
data class ScheduledReminder(
    val entryId: Long,
    val stepIndex: Int,
    val fireAt: Instant,
    val style: ReminderStyle,
    val offset: Duration,
    val isLast: Boolean,
) {
    /** Stable id used for the system alarm and the notification of this step. */
    val requestCode: Int get() = requestCodeFor(entryId, stepIndex)

    companion object {
        /** A chain may hold at most this many steps, so request codes never collide. */
        const val MAX_STEPS = 16

        fun requestCodeFor(
            entryId: Long,
            stepIndex: Int,
        ): Int {
            require(stepIndex in 0 until MAX_STEPS) { "Step index out of range: $stepIndex" }
            return (entryId * MAX_STEPS + stepIndex).toInt()
        }
    }
}

/**
 * The reminders that still need to fire for this entry at time [now].
 * Silent steps are left out because they never interrupt the user.
 */
fun Entry.scheduledReminders(now: Instant): List<ScheduledReminder> {
    if (id == 0L || !reminders.enabled) return emptyList()
    if (manualStatus == EntryStatus.COMPLETED || manualStatus == EntryStatus.CANCELLED) return emptyList()
    val ordered = reminders.orderedSteps.take(ScheduledReminder.MAX_STEPS)
    val lastActive = ordered.indexOfLast { it.enabled && it.style != ReminderStyle.SILENT }
    return ordered.mapIndexedNotNull { index, step ->
        val fireAt = step.fireTime(start)
        if (!step.enabled || step.style == ReminderStyle.SILENT || !fireAt.isAfter(now)) {
            null
        } else {
            ScheduledReminder(id, index, fireAt, step.style, step.offset, isLast = index == lastActive)
        }
    }
}

/**
 * Finds the step that a fired alarm refers to, or null when the alarm is stale
 * (the entry was moved, finished, or that reminder was turned off after scheduling).
 *
 * @param tolerance allowed difference between the requested and actual fire time.
 */
fun Entry.reminderForFiredAlarm(
    stepIndex: Int,
    expectedFireAt: Instant,
    tolerance: Duration = Duration.ofMinutes(2),
): ScheduledReminder? {
    if (!reminders.enabled) return null
    if (manualStatus == EntryStatus.COMPLETED || manualStatus == EntryStatus.CANCELLED) return null
    val ordered = reminders.orderedSteps
    val step = ordered.getOrNull(stepIndex) ?: return null
    if (!step.enabled) return null
    val fireAt = step.fireTime(start)
    if (Duration.between(fireAt, expectedFireAt).abs() > tolerance) return null
    val lastActive = ordered.indexOfLast { it.enabled && it.style != ReminderStyle.SILENT }
    return ScheduledReminder(id, stepIndex, fireAt, step.style, step.offset, isLast = stepIndex == lastActive)
}
