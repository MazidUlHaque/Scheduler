package com.mazidulhaque.kokhon.core.alarm

import com.mazidulhaque.kokhon.core.model.AlarmBehavior
import com.mazidulhaque.kokhon.core.model.Importance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Everything the alarm screen needs to explain what is ringing and what each button will do. */
data class RingingAlarm(
    val entryId: Long,
    val stepIndex: Int,
    val title: String,
    val entryStart: Instant,
    val isApproximate: Boolean,
    val importance: Importance,
    /** 1-based position among the chain's active reminders. */
    val stepNumber: Int,
    val stepCount: Int,
    val nextReminderAt: Instant?,
    val snoozeCount: Int,
    val behavior: AlarmBehavior,
    val quietAt: Instant,
    val withSound: Boolean,
    val silenced: Boolean = false,
) {
    val snoozesLeft: Int get() = behavior.snoozesLeft(snoozeCount)
    val canSnooze: Boolean get() = behavior.canSnooze(snoozeCount)
}

/** The alarm that is ringing right now, if any. Observed by [com.mazidulhaque.kokhon.core.alarm.ui.AlarmActivity]. */
object AlarmRingState {
    private val state = MutableStateFlow<RingingAlarm?>(null)
    val current: StateFlow<RingingAlarm?> = state.asStateFlow()

    internal fun set(alarm: RingingAlarm?) {
        state.value = alarm
    }

    internal fun markSilenced() = state.update { it?.copy(silenced = true) }
}
