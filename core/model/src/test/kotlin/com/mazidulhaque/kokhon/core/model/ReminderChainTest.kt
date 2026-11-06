package com.mazidulhaque.kokhon.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

class ReminderChainTest {
    private val start = Instant.parse("2026-11-07T15:30:00Z")

    @Test
    fun `important chain is 30, 15 and 5 minutes with the last one as an alarm`() {
        val steps = ReminderChain.defaultFor(Importance.IMPORTANT).orderedSteps
        assertEquals(listOf(30L, 15L, 5L), steps.map { it.offset.toMinutes() })
        assertEquals(ReminderStyle.ALARM, steps.last().style)
    }

    @Test
    fun `normal chain is a single notification 15 minutes before`() {
        val steps = ReminderChain.defaultFor(Importance.NORMAL).steps
        assertEquals(1, steps.size)
        assertEquals(Duration.ofMinutes(15), steps.single().offset)
        assertEquals(ReminderStyle.NOTIFICATION, steps.single().style)
    }

    @Test
    fun `pending steps skip reminders that already fired`() {
        val chain = ReminderChain.defaultFor(Importance.IMPORTANT)
        val now = start.minus(Duration.ofMinutes(20))
        assertEquals(listOf(15L, 5L), chain.pendingSteps(start, now).map { it.offset.toMinutes() })
    }

    @Test
    fun `turning the chain off leaves nothing pending but keeps the steps`() {
        val chain = ReminderChain.defaultFor(Importance.IMPORTANT).copy(enabled = false)
        assertTrue(chain.pendingSteps(start, start.minus(Duration.ofHours(2))).isEmpty())
        assertEquals(3, chain.steps.size)
    }

    @Test
    fun `a single disabled step is skipped`() {
        val chain =
            ReminderChain(
                listOf(
                    ReminderStep(Duration.ofMinutes(30), ReminderStyle.NOTIFICATION, enabled = false),
                    ReminderStep(Duration.ofMinutes(5), ReminderStyle.ALARM),
                ),
            )
        assertEquals(
            listOf(5L),
            chain.pendingSteps(start, start.minus(Duration.ofHours(1))).map { it.offset.toMinutes() },
        )
    }

    @Test
    fun `a dinner date gets get-ready and time-to-leave reminders`() {
        val steps = ReminderChain.defaultFor(Importance.IMPORTANT, EntryType.DINNER_DATE).orderedSteps
        assertEquals(listOf(120L, 45L, 30L, 15L, 5L), steps.map { it.offset.toMinutes() })
        assertEquals(ReminderPurpose.GET_READY, steps[0].purpose)
        assertEquals(ReminderPurpose.LEAVE_NOW, steps[1].purpose)
    }

    @Test
    fun `a profile step at the same time labels the existing step and keeps the louder style`() {
        val steps = ReminderChain.defaultFor(Importance.CRITICAL, EntryType.EXAM).orderedSteps
        val hourBefore = steps.single { it.offset == Duration.ofHours(1) }
        assertEquals(ReminderPurpose.LEAVE_NOW, hourBefore.purpose)
        assertEquals(ReminderStyle.HEADS_UP, hourBefore.style)
        assertEquals(ReminderPurpose.PREPARE, steps.single { it.offset == Duration.ofDays(1) }.purpose)
    }

    @Test
    fun `low importance stays a single quiet reminder`() {
        assertEquals(1, ReminderChain.defaultFor(Importance.LOW, EntryType.TOUR).steps.size)
    }

    @Test
    fun `negative offsets are rejected`() {
        assertThrows<IllegalArgumentException> { ReminderStep(Duration.ofMinutes(-1), ReminderStyle.ALARM) }
    }
}
