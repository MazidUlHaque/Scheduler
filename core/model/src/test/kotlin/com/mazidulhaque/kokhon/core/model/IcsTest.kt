package com.mazidulhaque.kokhon.core.model

import com.mazidulhaque.kokhon.core.model.ics.Ics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IcsTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now = Instant.parse("2026-11-06T12:40:00Z")

    @Test
    fun `a plan survives export and import`() {
        val plan =
            Entry(
                id = 7,
                title = "Dinner with Nabila, Rafi; and team",
                type = EntryType.DINNER_DATE,
                start = Instant.parse("2026-11-07T14:00:00Z"),
                duration = Duration.ofMinutes(90),
                isApproximate = true,
                location = "Gulshan 2",
                notes = "Book a table\nnear the window",
            )
        val text = Ics.write(listOf(plan), now, zone)
        assertTrue(text.lines().all { it.toByteArray().size <= 76 }, "lines are folded")
        val back = Ics.read(text, zone).single()
        assertEquals(plan.title, back.title)
        assertEquals(plan.start, back.start)
        assertEquals(plan.duration, back.duration)
        assertEquals(plan.type, back.type)
        assertEquals(plan.importance, back.importance)
        assertEquals(true, back.isApproximate)
        assertEquals("Gulshan 2", back.location)
        assertEquals("Book a table\nnear the window", back.notes)
        assertEquals(plan.reminders.orderedSteps.map { it.offset }, back.reminders.orderedSteps.map { it.offset })
    }

    @Test
    fun `an event from Google Calendar with a time zone`() {
        val text =
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;TZID=Asia/Dhaka:20261120T100000
            DTEND;TZID=Asia/Dhaka:20261120T113000
            SUMMARY:Project review
            LOCATION:Office\, 3rd floor
            DESCRIPTION:Bring the
              slides
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT30M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        val e = Ics.read(text, ZoneId.of("UTC")).single()
        assertEquals(
            LocalDate
                .of(2026, 11, 20)
                .atTime(10, 0)
                .atZone(zone)
                .toInstant(),
            e.start,
        )
        assertEquals(Duration.ofMinutes(90), e.duration)
        assertEquals("Office, 3rd floor", e.location)
        assertEquals("Bring the slides", e.notes)
        assertEquals(listOf(Duration.ofMinutes(30)), e.reminders.orderedSteps.map { it.offset })
    }

    @Test
    fun `all-day events and durations`() {
        val text = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART;VALUE=DATE:20261225\nSUMMARY:Holiday\nEND:VEVENT\nEND:VCALENDAR"
        val e = Ics.read(text, zone).single()
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 12, 25).atStartOfDay(zone).toInstant(), e.start)
        assertEquals(Duration.ofDays(1).plusHours(2), Ics.parseDuration("P1DT2H"))
        assertEquals("-P1DT2H30M", Ics.trigger(Duration.ofDays(1).plusHours(2).plusMinutes(30)))
    }

    @Test
    fun `broken events are skipped`() {
        val text =
            listOf(
                "BEGIN:VCALENDAR",
                "BEGIN:VEVENT",
                "SUMMARY:No date",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "DTSTART:20261107T090000Z",
                "SUMMARY:Ok",
                "END:VEVENT",
                "END:VCALENDAR",
            ).joinToString("\n")
        assertEquals(listOf("Ok"), Ics.read(text, zone).map { it.title })
    }
}
