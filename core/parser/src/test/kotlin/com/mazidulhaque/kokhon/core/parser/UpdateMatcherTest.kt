package com.mazidulhaque.kokhon.core.parser

import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateMatcherTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 6, 18, 40, 0, 0, zone)
    private val parser = PlanParser()

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    private val teamMeeting =
        Entry(id = 1, title = "Team meeting", type = EntryType.MEETING, start = at("2026-11-07T21:30"), people = listOf("team"))
    private val clientMeeting = Entry(id = 2, title = "Client meeting", type = EntryType.MEETING, start = at("2026-11-08T11:00"))
    private val dinner =
        Entry(id = 3, title = "Dinner with Nabila", type = EntryType.DINNER, start = at("2026-11-07T20:00"), people = listOf("Nabila"))
    private val entries = listOf(teamMeeting, clientMeeting, dinner)

    private fun match(text: String) = UpdateMatcher.match(parser.parse(text, now).change!!, entries, now.toInstant(), zone)

    @Test
    fun `the meeting tomorrow is the best match and moves to 10 pm`() {
        val candidates = match("guys let's shift tomorrow's meeting to 10 pm instead")
        assertEquals(listOf(1L, 2L), candidates.map { it.entry.id })
        val best = candidates.first()
        assertEquals(at("2026-11-07T22:00"), best.newStart)
        assertTrue(MatchReason.SAME_TYPE in best.reasons)
        assertTrue(MatchReason.SAME_DAY in best.reasons)
    }

    @Test
    fun `a name in the message finds the plan with that person`() {
        val best = match("can we move dinner with Nabila to 9?").first()
        assertEquals(3L, best.entry.id)
        // "9" for a dinner at 8 PM means 9 PM.
        assertEquals(at("2026-11-07T21:00"), best.newStart)
    }

    @Test
    fun `cancelling proposes no new time`() {
        val best = match("the dinner is cancelled").first()
        assertEquals(3L, best.entry.id)
        assertTrue(best.cancel)
    }

    @Test
    fun `finished and cancelled plans are never matched`() {
        val done = listOf(teamMeeting.copy(manualStatus = EntryStatus.COMPLETED), clientMeeting.copy(manualStatus = EntryStatus.CANCELLED))
        val change = parser.parse("move the meeting to 10 pm", now).change!!
        assertTrue(UpdateMatcher.match(change, done, now.toInstant(), zone).isEmpty())
    }

    @Test
    fun `an ambiguous new time takes the reading closest to the old one`() {
        val change = parser.parse("make it 10 instead", now).change!!
        assertEquals(at("2026-11-07T22:00"), UpdateMatcher.proposeStart(change, teamMeeting, zone))
        val morning = teamMeeting.copy(start = at("2026-11-07T09:30"))
        assertEquals(at("2026-11-07T10:00"), UpdateMatcher.proposeStart(change, morning, zone))
    }

    @Test
    fun `moving to another day keeps the time`() {
        val change = parser.parse("move the meeting to monday", now).change!!
        assertEquals(at("2026-11-09T21:30"), UpdateMatcher.proposeStart(change, teamMeeting, zone))
    }
}
