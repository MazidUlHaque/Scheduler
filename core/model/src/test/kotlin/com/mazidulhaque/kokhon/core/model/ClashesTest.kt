package com.mazidulhaque.kokhon.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ClashesTest {
    private val nine = Instant.parse("2026-11-07T15:00:00Z")
    private val meeting = Entry(id = 1, title = "Team meeting", type = EntryType.MEETING, start = nine, duration = Duration.ofHours(1))

    @Test
    fun `overlapping plans clash`() {
        val dinner = Entry(id = 2, title = "Dinner", type = EntryType.DINNER, start = nine.plus(Duration.ofMinutes(30)))
        assertEquals(listOf(meeting), dinner.clashesIn(listOf(meeting, dinner)))
    }

    @Test
    fun `back to back plans do not clash`() {
        val next = Entry(id = 2, title = "Call", type = EntryType.MEETING, start = nine.plus(Duration.ofHours(1)))
        assertTrue(next.clashesIn(listOf(meeting)).isEmpty())
    }

    @Test
    fun `tasks, cancelled and finished plans never clash`() {
        val task = Entry(id = 3, title = "Pay bill", type = EntryType.TASK, start = nine)
        val cancelled = meeting.copy(id = 4, manualStatus = EntryStatus.CANCELLED)
        val other = Entry(id = 5, title = "Lunch", type = EntryType.EVENT, start = nine)
        assertTrue(task.clashesIn(listOf(meeting)).isEmpty())
        assertTrue(other.clashesIn(listOf(cancelled, task)).isEmpty())
    }

    @Test
    fun `the same message captured twice is a duplicate`() {
        val source = EntrySource(EntrySource.Kind.PASTED_TEXT, "meeting tomorrow at 9", null)
        val first = meeting.copy(source = source)
        val again = Entry(title = "Meeting", type = EntryType.MEETING, start = nine, source = source)
        assertEquals(first, again.duplicateIn(listOf(first)))
        assertNull(again.copy(start = nine.plusSeconds(3600)).duplicateIn(listOf(first)))
    }

    @Test
    fun `the same name at the same time is a duplicate`() {
        val again = Entry(title = "team meeting ", type = EntryType.MEETING, start = nine)
        assertEquals(meeting, again.duplicateIn(listOf(meeting)))
    }
}
