package com.mazidulhaque.kokhon.core.parser

import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.EntryType.BIRTHDAY
import com.mazidulhaque.kokhon.core.model.EntryType.DINNER
import com.mazidulhaque.kokhon.core.model.EntryType.EVENT
import com.mazidulhaque.kokhon.core.model.EntryType.EXAM
import com.mazidulhaque.kokhon.core.model.EntryType.FLIGHT
import com.mazidulhaque.kokhon.core.model.EntryType.MATCH
import com.mazidulhaque.kokhon.core.model.EntryType.MEETING
import com.mazidulhaque.kokhon.core.model.EntryType.OTHER
import com.mazidulhaque.kokhon.core.model.EntryType.TASK
import com.mazidulhaque.kokhon.core.model.EntryType.TOUR
import com.mazidulhaque.kokhon.core.model.Importance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** All cases are parsed as if sent on Friday 6 November 2026 at 6:40 PM in Dhaka. */
class PlanParserTest {
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 6, 18, 40, 0, 0, ZoneId.of("Asia/Dhaka"))
    private val parser = PlanParser()

    private val today = LocalDate.of(2026, 11, 6)
    private val tomorrow = today.plusDays(1)

    private data class Case(
        val text: String,
        val type: EntryType,
        val date: LocalDate,
        val time: String,
        val title: String? = null,
        val approximate: Boolean? = null,
        val alternative: String? = null,
    )

    private fun t(value: String) = LocalTime.parse(value)

    private val cases =
        listOf(
            // The original example
            Case("let's try to sit for a meeting tomorrow around 9:30 pm", MEETING, tomorrow, "21:30", "Meeting", approximate = true),
            Case("Meeting tomorrow at 10", MEETING, tomorrow, "10:00", alternative = "22:00"),
            Case("meeting at 10am tomorrow", MEETING, tomorrow, "10:00", approximate = false),
            Case("dinner at 8", DINNER, today, "20:00", "Dinner"),
            Case("Dinner with Nabila tomorrow at 8 at Gulshan 2", DINNER, tomorrow, "20:00", "Dinner with Nabila"),
            Case("call at 5", MEETING, tomorrow, "17:00", alternative = "05:00"),
            Case("Bangladesh vs Sri Lanka match on Sunday at 2pm", MATCH, LocalDate.of(2026, 11, 8), "14:00", "Bangladesh vs Sri Lanka"),
            Case("remind me to send the budget sheet tomorrow at 11am", TASK, tomorrow, "11:00", "Send the budget sheet"),
            Case("Rafi's birthday party on 15th November at 7pm", BIRTHDAY, LocalDate.of(2026, 11, 15), "19:00", "Rafi's birthday"),
            Case("exam on 20/11 at 10am", EXAM, LocalDate.of(2026, 11, 20), "10:00", "Exam"),
            Case("flight to Dubai on Dec 3 at 6:45 am", FLIGHT, LocalDate.of(2026, 12, 3), "06:45", "Flight"),
            Case("doctor appointment next monday morning", EntryType.APPOINTMENT, LocalDate.of(2026, 11, 9), "09:00"),
            Case("tea break in 2 hours", OTHER, today, "20:40", "Tea break"),
            Case("party at 9 tonight", EVENT, today, "21:00", "Party"),
            Case("standup at 10:15", MEETING, tomorrow, "10:15", alternative = "22:15"),
            Case("Movie 7-9 pm on saturday", OTHER, tomorrow, "19:00", "Movie"),
            Case("wedding on 25 dec", EVENT, LocalDate.of(2026, 12, 25), "09:00", "Wedding"),
            // 3:30 PM has already passed at 6:40 PM, so it means tomorrow.
            Case("meet at 3:30", MEETING, tomorrow, "15:30"),
            Case("half past 7 dinner", DINNER, today, "19:30"),
            Case("quarter to 9 tomorrow morning", OTHER, tomorrow, "08:45"),
            Case("picnic this weekend", TOUR, tomorrow, "09:00", "Trip"),
            Case("Meeting at noon tomorrow", MEETING, tomorrow, "12:00"),
            Case("lunch on monday at 1", EVENT, LocalDate.of(2026, 11, 9), "13:00", "Lunch"),
            Case("class at 8:00", EVENT, today, "20:00", "Class", alternative = "08:00"),
            Case("game at 9ish", MATCH, today, "21:00", approximate = true),
            Case("football maybe at 4 tomorrow", MATCH, tomorrow, "16:00", approximate = true),
            Case("Wedding reception 22.11.2026 at 7.30 pm", EVENT, LocalDate.of(2026, 11, 22), "19:30"),
            Case("meeting on 2026-11-30 at 14:00", MEETING, LocalDate.of(2026, 11, 30), "14:00"),
            Case("team sync next friday 11am", MEETING, LocalDate.of(2026, 11, 13), "11:00"),
            Case("trip in 3 days", TOUR, LocalDate.of(2026, 11, 9), "09:00"),
            Case("Submit the report by 5pm", TASK, tomorrow, "17:00", "Submit the report"),
            Case("Interview on the 12th at 11", MEETING, LocalDate.of(2026, 11, 12), "11:00"),
            Case("dinner date at 8:30 with Tania", EntryType.DINNER_DATE, today, "20:30", "Dinner date with Tania"),
            Case("Meeting at 9:30 tomorrow evening", MEETING, tomorrow, "21:30"),
            Case("coffee at 23:15", OTHER, today, "23:15", "Coffee"),
            // Banglish
            Case("kal rat 9.30 e meeting", MEETING, tomorrow, "21:30", "Meeting"),
            Case("aj bikale 5tay cricket", MATCH, today, "17:00"),
            Case("porshu sokal 10 tay class", EVENT, LocalDate.of(2026, 11, 8), "10:00"),
            Case("kal sondhay 7 tay dinner", DINNER, tomorrow, "19:00"),
        )

    @TestFactory
    fun messages() =
        cases.map { case ->
            DynamicTest.dynamicTest(case.text) {
                val result = parser.parse(case.text, now)
                assertNull(result.change, "should not be a change request")
                assertEquals(1, result.drafts.size, "drafts: ${result.drafts}")
                val draft = result.drafts.single()
                assertEquals(case.type, draft.type, "type")
                assertEquals(case.date, draft.date, "date")
                assertEquals(t(case.time), draft.time, "time")
                case.title?.let { assertEquals(it, draft.title, "title") }
                case.approximate?.let { assertEquals(it, draft.approximate, "approximate") }
                case.alternative?.let { assertEquals(t(it), draft.alternativeTime, "alternative") }
            }
        }

    @Test
    fun `the original example highlights every understood word`() {
        val text = "let's try to sit for a meeting tomorrow around 9:30 pm"
        val draft = parser.parse(text, now).drafts.single()
        val highlighted = draft.spans.associate { text.substring(it.start, it.end) to it.kind }
        assertEquals(SpanKind.DATE, highlighted["tomorrow"])
        assertEquals(SpanKind.TIME, highlighted["9:30 pm"])
        assertEquals(SpanKind.APPROXIMATE, highlighted["around"])
        assertEquals(SpanKind.TYPE, highlighted["meeting"])
    }

    @Test
    fun `two plans in one message`() {
        val drafts = parser.parse("meeting at 10am tomorrow and dinner at 8pm", now).drafts
        assertEquals(listOf(MEETING, DINNER), drafts.map { it.type })
        assertEquals(listOf(tomorrow, tomorrow), drafts.map { it.date })
        assertEquals(listOf(t("10:00"), t("20:00")), drafts.map { it.time })
    }

    @Test
    fun `plans on separate lines`() {
        val drafts = parser.parse("Standup tomorrow 10am\nFootball on Sunday at 4pm", now).drafts
        assertEquals(2, drafts.size)
        assertEquals(LocalDate.of(2026, 11, 8), drafts[1].date)
    }

    @Test
    fun `a date in one sentence and the time in the next make one plan`() {
        val drafts = parser.parse("Tomorrow. Meeting at 9am.", now).drafts
        assertEquals(1, drafts.size)
        assertEquals(tomorrow, drafts.single().date)
        assertEquals(t("09:00"), drafts.single().time)
    }

    @Test
    fun `stated importance raises the level`() {
        assertEquals(
            Importance.IMPORTANT,
            parser
                .parse("important meeting tomorrow at 10", now)
                .drafts
                .single()
                .importance,
        )
        assertEquals(
            Importance.CRITICAL,
            parser
                .parse("very important call at 3pm", now)
                .drafts
                .single()
                .importance,
        )
        assertEquals(
            Importance.CRITICAL,
            parser
                .parse("flight at 6am tomorrow", now)
                .drafts
                .single()
                .importance,
        )
    }

    @Test
    fun `places and people are picked up`() {
        val draft = parser.parse("Dinner with Nabila tomorrow at 8 at Gulshan 2", now).drafts.single()
        assertEquals(listOf("Nabila"), draft.people)
        assertEquals("Gulshan 2", draft.location)
        assertEquals(
            "the office",
            parser
                .parse("meeting at 10am at the office", now)
                .drafts
                .single()
                .location
                .let { "the $it" },
        )
    }

    @Test
    fun `missing time is filled in and marked as a guess`() {
        val draft = parser.parse("dinner tomorrow", now).drafts.single()
        assertEquals(t("20:00"), draft.time)
        assertTrue(draft.timeGuessed)
    }

    @Test
    fun `parts of the day set the default time`() {
        assertEquals(
            t("13:00"),
            parser
                .parse("lunch tomorrow", now)
                .drafts
                .single()
                .time,
        )
        assertEquals(
            t("19:00"),
            parser
                .parse("match tomorrow evening", now)
                .drafts
                .single()
                .time,
        )
        assertEquals(
            t("09:00"),
            parser
                .parse("trip tomorrow morning", now)
                .drafts
                .single()
                .time,
        )
    }

    @Test
    fun `a time range keeps its end`() {
        val draft = parser.parse("workshop from 2 to 5 tomorrow", now).drafts.single()
        assertEquals(t("14:00"), draft.time)
        assertEquals(t("17:00"), draft.endTime)
    }

    @Test
    fun `the weekend follows the local week`() {
        val friday = PlanParser(ParserConfig(weekendStart = DayOfWeek.FRIDAY))
        assertEquals(
            today,
            friday
                .parse("picnic this weekend", now)
                .drafts
                .single()
                .date,
        )
    }

    @Test
    fun `messages without a plan produce nothing`() {
        listOf(
            "how are you?",
            "the price is 9.30 tk",
            "we were 5 people at the party",
            "I changed my mind about pizza",
            "see you",
            "",
        ).forEach { text ->
            val result = parser.parse(text, now)
            assertTrue(result.drafts.isEmpty(), "\"$text\" gave ${result.drafts}")
            assertNull(result.change, "\"$text\" gave ${result.change}")
        }
    }

    @Test
    fun `quantities are not times`() {
        assertTrue(parser.parse("we need 3 days for this", now).drafts.isEmpty())
        assertTrue(parser.parse("buy 2 kg rice", now).drafts.isEmpty())
    }

    // ------------------------------------------------------------- changes

    @Test
    fun `moving a plan to a new time`() {
        val change = parser.parse("guys let's shift tomorrow's meeting to 10 pm instead", now).change
        assertNotNull(change)
        assertEquals(ChangeRequest.Kind.MOVE, change!!.kind)
        assertEquals(tomorrow, change.identifyingDate)
        assertEquals(t("22:00"), change.newTime)
        assertNull(change.newDate)
        assertEquals(MEETING, change.type)
    }

    @Test
    fun `moving a plan to another day`() {
        val change = parser.parse("move the meeting to monday", now).change!!
        assertEquals(LocalDate.of(2026, 11, 9), change.newDate)
        assertNull(change.newTime)
    }

    @Test
    fun `a time without am or pm in a change is left for the matcher to decide`() {
        val change = parser.parse("make it 10 instead", now).change!!
        assertEquals(t("10:00").hour % 12, change.newTime!!.hour % 12)
        assertTrue(change.newTimeAmbiguous)
    }

    @Test
    fun `cancelling a plan`() {
        listOf("the dinner is cancelled", "tomorrow's meeting is off", "meeting hobe na").forEach { text ->
            val change = parser.parse(text, now).change
            assertEquals(ChangeRequest.Kind.CANCEL, change?.kind, text)
        }
    }
}
