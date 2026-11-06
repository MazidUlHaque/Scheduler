package com.mazidulhaque.kokhon.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class EntryTest {
    private val start = Instant.parse("2026-11-07T15:30:00Z")
    private val meeting =
        Entry(title = "Team meeting", type = EntryType.MEETING, start = start, duration = Duration.ofHours(1))

    @Test
    fun `status follows the clock`() {
        assertEquals(EntryStatus.UPCOMING, meeting.statusAt(start.minusSeconds(60)))
        assertEquals(EntryStatus.ONGOING, meeting.statusAt(start.plusSeconds(60)))
        assertEquals(EntryStatus.COMPLETED, meeting.statusAt(start.plus(Duration.ofHours(2))))
    }

    @Test
    fun `past tasks are missed unless ticked off`() {
        val task = Entry(title = "Send budget", type = EntryType.TASK, start = start)
        val later = start.plusSeconds(60)
        assertEquals(EntryStatus.MISSED, task.statusAt(later))
        assertEquals(EntryStatus.COMPLETED, task.copy(manualStatus = EntryStatus.COMPLETED).statusAt(later))
    }

    @Test
    fun `finished entries are the ones shown faded`() {
        assertFalse(meeting.isFinishedAt(start.minusSeconds(1)))
        assertTrue(meeting.isFinishedAt(start.plus(Duration.ofHours(2))))
        assertTrue(meeting.copy(manualStatus = EntryStatus.CANCELLED).isFinishedAt(start.minusSeconds(1)))
    }

    @Test
    fun `rescheduling moves the reminders with the entry`() {
        val important = meeting.withImportance(Importance.IMPORTANT)
        val moved = important.rescheduledTo(start.plus(Duration.ofMinutes(30)))
        val fireTimes = moved.reminders.orderedSteps.map { it.fireTime(moved.start) }
        assertEquals(
            listOf(start, start.plus(Duration.ofMinutes(15)), start.plus(Duration.ofMinutes(25))),
            fireTimes,
        )
    }

    @Test
    fun `entry types pick a sensible default importance`() {
        assertEquals(Importance.NORMAL, Entry(title = "x", type = EntryType.MEETING, start = start).importance)
        assertEquals(Importance.CRITICAL, Entry(title = "x", type = EntryType.FLIGHT, start = start).importance)
    }
}
