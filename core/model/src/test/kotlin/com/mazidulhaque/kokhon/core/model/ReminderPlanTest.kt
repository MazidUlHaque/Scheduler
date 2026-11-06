package com.mazidulhaque.kokhon.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ReminderPlanTest {
    private val start = Instant.parse("2026-11-07T15:30:00Z")
    private val entry =
        Entry(id = 7, title = "Team meeting", type = EntryType.MEETING, start = start)
            .withImportance(Importance.IMPORTANT)

    @Test
    fun `plans every future step with stable request codes`() {
        val plan = entry.scheduledReminders(start.minus(Duration.ofHours(1)))
        assertEquals(listOf(0, 1, 2), plan.map { it.stepIndex })
        assertEquals(listOf(7 * 16, 7 * 16 + 1, 7 * 16 + 2), plan.map { it.requestCode })
        assertTrue(plan.last().isLast)
        assertFalse(plan.first().isLast)
    }

    @Test
    fun `nothing is planned for unsaved, finished or muted entries`() {
        val now = start.minus(Duration.ofHours(1))
        assertTrue(entry.copy(id = 0).scheduledReminders(now).isEmpty())
        assertTrue(entry.copy(manualStatus = EntryStatus.COMPLETED).scheduledReminders(now).isEmpty())
        assertTrue(entry.copy(reminders = entry.reminders.copy(enabled = false)).scheduledReminders(now).isEmpty())
    }

    @Test
    fun `silent steps are not scheduled`() {
        val chain = ReminderChain(listOf(ReminderStep(Duration.ofMinutes(10), ReminderStyle.SILENT)))
        assertTrue(entry.copy(reminders = chain).scheduledReminders(start.minus(Duration.ofHours(1))).isEmpty())
    }

    @Test
    fun `a fired alarm is matched to its step`() {
        val fired = entry.reminderForFiredAlarm(2, start.minus(Duration.ofMinutes(5)))
        assertNotNull(fired)
        assertEquals(ReminderStyle.ALARM, fired!!.style)
        assertTrue(fired.isLast)
    }

    @Test
    fun `an alarm for a moved entry is recognised as stale`() {
        val moved = entry.rescheduledTo(start.plus(Duration.ofMinutes(30)))
        assertNull(moved.reminderForFiredAlarm(2, start.minus(Duration.ofMinutes(5))))
    }

    @Test
    fun `an alarm for a turned-off step is stale`() {
        val steps = entry.reminders.orderedSteps.toMutableList()
        steps[2] = steps[2].copy(enabled = false)
        val muted = entry.copy(reminders = entry.reminders.copy(steps = steps))
        assertNull(muted.reminderForFiredAlarm(2, start.minus(Duration.ofMinutes(5))))
    }
}
