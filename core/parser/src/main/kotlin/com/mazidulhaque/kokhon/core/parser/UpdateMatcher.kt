package com.mazidulhaque.kokhon.core.parser

import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Why a plan was considered a match, shown to the user ("same type, same day"). */
enum class MatchReason { SAME_TYPE, SAME_DAY, SAME_WORDS, SAME_PEOPLE, COMING_UP }

/**
 * A plan that a change message may refer to, with what would change.
 *
 * @property newStart the proposed new start, or null when only cancelling or when the message gives no new time.
 */
data class MatchCandidate(
    val entry: Entry,
    val score: Int,
    val reasons: List<MatchReason>,
    val newStart: Instant?,
    val cancel: Boolean,
) {
    val changesSomething: Boolean get() = cancel || (newStart != null && newStart != entry.start)
}

/** Finds which existing plans a change message is about and works out the proposed change. */
object UpdateMatcher {
    /** Candidates, best first. Only upcoming (or very recent) plans that are still active are considered. */
    fun match(
        request: ChangeRequest,
        entries: List<Entry>,
        now: Instant,
        zone: ZoneId,
    ): List<MatchCandidate> =
        entries
            .asSequence()
            .filter { it.statusAt(now) != EntryStatus.CANCELLED && it.manualStatus != EntryStatus.COMPLETED }
            .filter { it.start.isAfter(now.minus(Duration.ofHours(12))) && it.start.isBefore(now.plus(Duration.ofDays(60))) }
            .mapNotNull { entry ->
                val reasons = mutableListOf<MatchReason>()
                var score = 0
                val entryDate = entry.start.atZone(zone).toLocalDate()
                if (request.type != null && request.type == entry.type) {
                    score += 3
                    reasons += MatchReason.SAME_TYPE
                }
                if (request.identifyingDate != null && request.identifyingDate == entryDate) {
                    score += 3
                    reasons += MatchReason.SAME_DAY
                }
                val titleWords = Regex("[\\p{L}]{3,}").findAll(entry.title.lowercase()).map { it.value }.toSet()
                val shared = titleWords.intersect(request.words) - PlanParser.MATCH_STOP_WORDS
                if (shared.isNotEmpty()) {
                    score += 2 * shared.size.coerceAtMost(3)
                    if (MatchReason.SAME_TYPE !in reasons || shared.size > 1) reasons += MatchReason.SAME_WORDS
                }
                val people = request.people.map { it.lowercase() }.toSet()
                if (people.isNotEmpty() && entry.people.any { it.lowercase() in people }) {
                    score += 2
                    reasons += MatchReason.SAME_PEOPLE
                }
                if (score > 0 && Duration.between(now, entry.start) < Duration.ofDays(2)) {
                    score += 1
                    reasons += MatchReason.COMING_UP
                }
                // A plan of another type only matches when a word or a person connects it to the message.
                val otherType = request.type != null && request.type != entry.type
                val connected = MatchReason.SAME_WORDS in reasons || MatchReason.SAME_PEOPLE in reasons
                if (score < 3 || (otherType && !connected)) {
                    null
                } else {
                    MatchCandidate(entry, score, reasons, proposeStart(request, entry, zone), request.kind == ChangeRequest.Kind.CANCEL)
                }
            }.sortedWith(compareByDescending<MatchCandidate> { it.score }.thenBy { it.entry.start })
            .toList()

    /**
     * Works out the change for one specific plan (also used when the user picked the plan first).
     * A new time without am/pm takes the reading closest to the old time: "make it 10" for a 9:30 PM plan means 10 PM.
     */
    fun proposeStart(
        request: ChangeRequest,
        entry: Entry,
        zone: ZoneId,
    ): Instant? {
        if (request.kind == ChangeRequest.Kind.CANCEL) return null
        if (request.newDate == null && request.newTime == null) return null
        val old = entry.start.atZone(zone)
        val date = request.newDate ?: old.toLocalDate()
        var time = request.newTime ?: old.toLocalTime()
        if (request.newTime != null && request.newTimeAmbiguous) {
            val other = request.newTime.plusHours(12)
            time = listOf(request.newTime, other).minBy { distance(it, old.toLocalTime()) }
        }
        return date.atTime(time).atZone(zone).toInstant()
    }

    private fun distance(
        a: LocalTime,
        b: LocalTime,
    ): Long {
        val diff = Duration.between(a, b).abs().toMinutes()
        return minOf(diff, 24 * 60 - diff)
    }
}
