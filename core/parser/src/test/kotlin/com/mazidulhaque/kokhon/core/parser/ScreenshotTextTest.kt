package com.mazidulhaque.kokhon.core.parser

import com.mazidulhaque.kokhon.core.model.EntryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Simulates what text recognition returns for typical chat screenshots (1080 x 2400 px). */
class ScreenshotTextTest {
    private val captured = LocalDateTime.of(2026, 11, 6, 18, 40)
    private val height = 2400

    /** A message line: 46 px tall text. A small line (timestamps, receipts): 30 px. */
    private fun msg(
        text: String,
        top: Int,
        left: Int = 80,
    ) = OcrLine(text, left, top, left + 700, top + 46)

    private fun small(
        text: String,
        top: Int,
        left: Int = 700,
    ) = OcrLine(text, left, top, left + 150, top + 30)

    private val whatsapp =
        listOf(
            small("6:40", 20, 40),
            small("85%", 20, 950),
            msg("Team Kokhon", 110, 200),
            small("online", 160, 200),
            small("Yesterday", 300, 460),
            msg("ok when can everyone join?", 400),
            small("9:12 PM", 452, 650),
            msg("let's try to sit for a meeting", 560, 300),
            msg("tomorrow around 9:30 pm", 612, 300),
            small("9:14 PM ✓✓", 664, 800),
            msg("Type a message", 2250, 120),
        )

    @Test
    fun `chat chrome is removed and wrapped lines are joined`() {
        val message = ScreenshotText.clean(whatsapp, height, captured)
        assertEquals("Team Kokhon\nok when can everyone join?\nlet's try to sit for a meeting tomorrow around 9:30 pm", message.text)
    }

    @Test
    fun `the date header and the last timestamp set the reference time`() {
        val message = ScreenshotText.clean(whatsapp, height, captured)
        assertEquals(LocalDateTime.of(2026, 11, 5, 21, 14), message.reference)
        assertTrue(message.referenceFromScreenshot)
    }

    @Test
    fun `tomorrow in yesterday's message means today`() {
        val message = ScreenshotText.clean(whatsapp, height, captured)
        val draft = PlanParser().parse(message.text, message.reference.atZone(ZoneId.of("Asia/Dhaka"))).drafts.single()
        assertEquals(EntryType.MEETING, draft.type)
        assertEquals(LocalDate.of(2026, 11, 6), draft.date)
        assertEquals(LocalTime.of(21, 30), draft.time)
        assertTrue(draft.approximate)
    }

    @Test
    fun `without a header the screenshot's own time is the reference`() {
        val lines = listOf(msg("dinner at 8?", 400), small("6:31 PM", 452))
        val message = ScreenshotText.clean(lines, height, captured)
        assertEquals("dinner at 8?", message.text)
        assertEquals(LocalDateTime.of(2026, 11, 6, 18, 31), message.reference)
        assertFalse(message.referenceFromScreenshot)
    }

    @Test
    fun `a message that is only a time is kept`() {
        val lines = listOf(msg("when?", 400), msg("9:30 pm", 520), small("6:31 PM", 572))
        assertEquals("when?\n9:30 pm", ScreenshotText.clean(lines, height, captured).text)
    }

    @Test
    fun `timestamps with ticks at the end of a line are removed`() {
        val lines = listOf(msg("see you at 9 10:32 PM ✓✓", 400))
        assertEquals("see you at 9", ScreenshotText.clean(lines, height, captured).text)
    }

    @Test
    fun `day headers in many forms`() {
        val today = LocalDate.of(2026, 11, 6) // a Friday
        assertEquals(today, ScreenshotText.headerDate("TODAY", today))
        assertEquals(LocalDate.of(2026, 11, 2), ScreenshotText.headerDate("Monday", today))
        assertEquals(LocalDate.of(2026, 11, 3), ScreenshotText.headerDate("3 November 2026", today))
        assertEquals(LocalDate.of(2026, 10, 30), ScreenshotText.headerDate("October 30, 2026", today))
        assertEquals(LocalDate.of(2026, 11, 4), ScreenshotText.headerDate("04/11/2026", today))
        assertEquals(null, ScreenshotText.headerDate("let's meet monday", today))
    }

    @Test
    fun `unrelated chat lines do not leak into the plan title`() {
        val text = "Rafi\nhaha that was fun\nfootball tomorrow at 4pm"
        val draft = PlanParser().parse(text, captured.atZone(ZoneId.of("Asia/Dhaka"))).drafts.single()
        assertEquals("Match", draft.title)
        assertEquals(LocalTime.of(16, 0), draft.time)
        val coffee =
            PlanParser()
                .parse(
                    "Rafi\nhaha that was fun\ncoffee at 5pm tomorrow",
                    captured.atZone(ZoneId.of("Asia/Dhaka")),
                ).drafts
                .single()
        assertEquals("Coffee", coffee.title)
    }

    /**
     * A cropped WhatsApp screenshot reported by a user (no date header, times printed at the end of lines):
     * Rahman asks for "a short meeting tomorrow at your preferred time?", the reply is "let's try around 10 pm".
     */
    private fun line(
        top: Int,
        left: Int,
        vararg words: Pair<String, Int>,
    ): OcrLine {
        val text = words.joinToString(" ") { it.first }
        val height = words.maxOf { it.second }
        return OcrLine(text, left, top, left + 900, top + height, words.map { OcrWord(it.first, it.second) })
    }

    private fun msg(vararg text: String) = text.map { it to 42 }.toTypedArray()

    private val rahmanChat =
        listOf(
            line(80, 165, "~" to 36, "A." to 36, "Rahman" to 36, "+880" to 30, "1700-000000" to 30),
            line(140, 165, *msg("Assalamualaikum", "Sir,", "we", "have", "finalised")),
            line(197, 165, *msg("our", "game's", "main", "flow.", "We", "also", "have", "some")),
            line(254, 165, *msg("questions", "regarding", "the", "data", "we", "will", "be")),
            line(311, 165, *msg("using.", "Will", "it", "be", "possible", "for", "you", "to", "join")),
            line(368, 165, *msg("us", "for", "a", "short", "meeting", "tomorrow", "at", "your")),
            line(425, 165, *msg("preferred", "time?"), "8:18" to 30, "PM" to 30),
            line(550, 595, *msg("walaikumussalam"), "8:19" to 30, "PM" to 30, "✓✓" to 30),
            line(655, 520, *msg("let's", "try", "around", "10", "pm"), "8:19" to 30, "PM" to 30, "✓✓" to 30),
            line(780, 165, "~" to 36, "A." to 36, "Rahman" to 36, "+880" to 30, "1700-000000" to 30),
            line(840, 165, *msg("Ok", "sir"), "8:19" to 30, "PM" to 30),
            line(990, 170, "Message" to 42),
        )

    @Test
    fun `message times at the end of a line are removed by their smaller size`() {
        val text = ScreenshotText.clean(rahmanChat, 1238, captured).text
        assertFalse(text.contains("8:18"), text)
        assertFalse(text.contains("8:19"), text)
        assertTrue(text.contains("let's try around 10 pm"), text)
    }

    @Test
    fun `a time written in the message itself is kept`() {
        val lines = listOf(line(400, 80, *msg("meet", "at", "10:30", "pm")))
        assertEquals("meet at 10:30 pm", ScreenshotText.clean(lines, height, captured).text)
    }

    @Test
    fun `the question and the reply make one meeting tomorrow at about 10 pm`() {
        // Screenshot taken the same evening, after the last message.
        val message = ScreenshotText.clean(rahmanChat, 1238, LocalDateTime.of(2026, 11, 6, 20, 30))
        val draft = PlanParser().parse(message.text, message.reference.atZone(ZoneId.of("Asia/Dhaka"))).drafts.single()
        assertEquals(EntryType.MEETING, draft.type)
        assertEquals(LocalDate.of(2026, 11, 7), draft.date)
        assertEquals(LocalTime.of(22, 0), draft.time)
        assertTrue(draft.approximate)
    }

    @Test
    fun `read the next morning, tomorrow still means the day after the chat`() {
        // Screenshot taken at 7:09 AM: the 8:19 PM messages are from the evening before.
        val message = ScreenshotText.clean(rahmanChat, 1238, LocalDateTime.of(2026, 11, 7, 7, 9))
        assertEquals(LocalDateTime.of(2026, 11, 6, 20, 19), message.reference)
        val draft = PlanParser().parse(message.text, message.reference.atZone(ZoneId.of("Asia/Dhaka"))).drafts.single()
        assertEquals(LocalDate.of(2026, 11, 7), draft.date)
    }
}
