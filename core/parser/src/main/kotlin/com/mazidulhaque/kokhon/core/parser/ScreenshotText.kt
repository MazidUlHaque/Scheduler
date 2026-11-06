package com.mazidulhaque.kokhon.core.parser

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** One word of a line, with its height in pixels (chat apps print message times smaller than messages). */
data class OcrWord(
    val text: String,
    val height: Int,
)

/** One line of text found in an image, with its position in pixels and, when known, its words. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val words: List<OcrWord> = emptyList(),
) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/**
 * The readable message in a chat screenshot, plus the moment it was written when the screenshot shows it.
 *
 * @property reference the date and time the plan in the message is relative to: "tomorrow" in a message
 * from yesterday means today.
 */
data class ScreenshotMessage(
    val text: String,
    val reference: LocalDateTime,
    val referenceFromScreenshot: Boolean,
)

/**
 * Cleans OCR output of a chat screenshot: drops the status bar, read receipts, timestamps, date headers and
 * the typing box, joins wrapped lines back into messages, and reads the date header to know when the message was written.
 */
object ScreenshotText {
    private val chrome =
        Regex(
            "^(seen|seen by .*|delivered|read|sent|sending\\.*|typing\\.*|online|offline|last seen.*|active now|active \\d+.* ago|edited|forwarded|" +
                "type a message|message|aa|write a message|reply|reply\\.*|send a message|\\+|[✓✔√]+|\\d{1,3}%|lte|4g|5g|volte|wi-?fi|" +
                "you replied|today at.*|end-to-end encrypted.*|messages and calls are end-to-end encrypted.*)$",
            RegexOption.IGNORE_CASE,
        )
    private val timestamp = Regex("^(\\d{1,2})[:.](\\d{2})\\s*([ap]\\.?m\\.?)?\\s*[✓✔√]*$", RegexOption.IGNORE_CASE)
    private val trailingStamp = Regex("\\s+\\d{1,2}[:.]\\d{2}\\s*(?:[ap]\\.?m\\.?)?\\s*[✓✔√]+$", RegexOption.IGNORE_CASE)
    private val weekdays = DayOfWeek.entries.associateBy { it.name.lowercase(Locale.ENGLISH) }
    private val months =
        Month.entries
            .flatMap { m ->
                val full = m.name.lowercase(Locale.ENGLISH)
                listOf(full to m, full.take(3) to m)
            }.toMap()

    fun clean(
        lines: List<OcrLine>,
        imageHeight: Int,
        captured: LocalDateTime,
    ): ScreenshotMessage {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.top }, { it.left }))
        // Message text is the larger text on screen; the upper quartile of line heights is a robust measure of it.
        val typicalHeight = sorted.map { it.height }.sorted().let { if (it.isEmpty()) 1 else it[it.size * 3 / 4] }
        var headerDate: LocalDate? = null
        var lastStamp: LocalTime? = null
        val kept = mutableListOf<OcrLine>()

        for (line in sorted) {
            val text = line.text.trim()
            val lower = text.lowercase(Locale.ENGLISH)
            val inStatusBar = line.bottom < imageHeight * 0.06
            val inInputBar = line.top > imageHeight * 0.9

            // Timestamps are printed smaller than messages; a message that is just "9:30 pm" stays.
            val stamp = timestamp.matchEntire(text)
            if (stamp != null && line.height < typicalHeight * 0.85) {
                val time = stampTime(stamp)
                if (time != null) lastStamp = time
                continue
            }
            if (chrome.matches(lower)) continue
            if (inStatusBar && (lower.length <= 12 || Regex("\\d").containsMatchIn(lower))) continue
            if (inInputBar && lower.length <= 24 && !Regex("\\d").containsMatchIn(lower)) continue
            headerDate(lower, captured.toLocalDate())?.let {
                headerDate = it
                lastStamp = null
                continue
            }
            val (withoutStamp, stampAtEnd) = stripSmallTrailingTime(line, typicalHeight)
            stampAtEnd?.let { lastStamp = it }
            kept += line.copy(text = withoutStamp.replace(trailingStamp, "").trim())
        }

        // Join wrapped lines of one bubble; separate bubbles with a new line.
        val text =
            buildString {
                var previous: OcrLine? = null
                for (line in kept) {
                    val prev = previous
                    if (prev != null) {
                        val gap = line.top - prev.bottom
                        val sameBubble = gap < prev.height * 0.8 && line.left < prev.right && prev.left < line.right
                        append(if (sameBubble) " " else "\n")
                    }
                    append(line.text)
                    previous = line
                }
            }

        val date = headerDate
        val stampTime = lastStamp
        val reference =
            when {
                date != null -> date.atTime(stampTime ?: if (date == captured.toLocalDate()) captured.toLocalTime() else LocalTime.NOON)

                stampTime != null && !stampTime.isAfter(captured.toLocalTime()) -> captured.toLocalDate().atTime(stampTime)

                // A message time later in the day than the screenshot means the message was sent the day before.
                stampTime != null -> captured.toLocalDate().minusDays(1).atTime(stampTime)

                else -> captured
            }
        return ScreenshotMessage(text.trim(), reference, referenceFromScreenshot = date != null)
    }

    /**
     * WhatsApp and others print the message time at the end of the last line, in smaller text:
     * "preferred time? 8:18 PM". Words that form such a time and are clearly smaller than the rest are removed.
     * A time written in the message itself ("meet at 10:30 pm") is the same size as the text around it and stays.
     */
    private fun stripSmallTrailingTime(
        line: OcrLine,
        typicalHeight: Int,
    ): Pair<String, LocalTime?> {
        val words = line.words
        if (words.size < 2) return line.text.trim() to null
        for (take in 3 downTo 1) {
            if (words.size <= take) continue
            val tail = words.takeLast(take)
            val tailText = tail.joinToString(" ") { it.text }
            val match = timestamp.matchEntire(tailText) ?: continue
            val bodyHeight = words.dropLast(take).maxOf { it.height }
            val reference = maxOf(bodyHeight, (typicalHeight * 0.9).toInt())
            if (tail.all { it.height < reference * 0.85 }) {
                return words.dropLast(take).joinToString(" ") { it.text } to stampTime(match)
            }
        }
        return line.text.trim() to null
    }

    private fun stampTime(m: MatchResult): LocalTime? {
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val meridiem = m.groupValues[3].lowercase(Locale.ENGLISH)
        if (minute > 59 || hour > 23) return null
        if (meridiem.startsWith("p") && hour < 12) hour += 12
        if (meridiem.startsWith("a") && hour == 12) hour = 0
        return LocalTime.of(hour, minute)
    }

    /** Chat apps separate days with headers like "Today", "Yesterday", "Monday" or "6 November 2026". */
    internal fun headerDate(
        line: String,
        today: LocalDate,
    ): LocalDate? {
        val s = line.trim().trim('-', '—', ' ').lowercase(Locale.ENGLISH)
        if (s == "today") return today
        if (s == "yesterday") return today.minusDays(1)
        weekdays[s]?.let { return today.with(TemporalAdjusters.previous(it)) }
        Regex("^(?:\\w+,\\s+)?(\\d{1,2})\\s+([a-z]+)(?:,?\\s+(\\d{4}))?$").matchEntire(s)?.let { m ->
            val month = months[m.groupValues[2]] ?: return null
            return runCatching { LocalDate.of(m.groupValues[3].toIntOrNull() ?: today.year, month, m.groupValues[1].toInt()) }.getOrNull()
        }
        Regex("^(?:\\w+,\\s+)?([a-z]+)\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?$").matchEntire(s)?.let { m ->
            val month = months[m.groupValues[1]] ?: return null
            return runCatching { LocalDate.of(m.groupValues[3].toIntOrNull() ?: today.year, month, m.groupValues[2].toInt()) }.getOrNull()
        }
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2,4})$").matchEntire(s)?.let { m ->
            val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate.of(year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }
}
