package com.mazidulhaque.kokhon.core.model

import java.time.Duration

/**
 * How an alarm-style reminder behaves while ringing. Designed so that an alarm
 * can never ring forever or embarrass the user in public.
 *
 * @property ringLimit after this long the alarm goes quiet and becomes a silent notification.
 * @property snoozeLength how long a single snooze lasts.
 * @property maxSnoozes after this many snoozes, Snooze is no longer offered.
 * @property volumeButtonSilences pressing a volume or power button silences the sound
 * without dismissing, so the user can choose Stop or Snooze calmly.
 * @property gentleStart the volume starts low and rises over a few seconds.
 */
data class AlarmBehavior(
    val ringLimit: Duration = Duration.ofSeconds(60),
    val snoozeLength: Duration = Duration.ofMinutes(5),
    val maxSnoozes: Int = 3,
    val volumeButtonSilences: Boolean = true,
    val gentleStart: Boolean = true,
) {
    init {
        require(!ringLimit.isNegative && !ringLimit.isZero) { "Ring limit must be positive" }
        require(!snoozeLength.isNegative && !snoozeLength.isZero) { "Snooze length must be positive" }
        require(maxSnoozes >= 0) { "Max snoozes can't be negative" }
    }

    fun canSnooze(snoozesUsed: Int): Boolean = snoozesUsed < maxSnoozes

    fun snoozesLeft(snoozesUsed: Int): Int = (maxSnoozes - snoozesUsed).coerceAtLeast(0)
}
