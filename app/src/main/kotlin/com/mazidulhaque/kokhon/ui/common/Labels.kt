package com.mazidulhaque.kokhon.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderChain
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import java.time.Duration

@StringRes
fun EntryType.labelRes(): Int =
    when (this) {
        EntryType.MEETING -> R.string.type_meeting
        EntryType.EVENT -> R.string.type_event
        EntryType.TASK -> R.string.type_task
        EntryType.DINNER -> R.string.type_dinner
        EntryType.DINNER_DATE -> R.string.type_dinner_date
        EntryType.TOUR -> R.string.type_tour
        EntryType.MATCH -> R.string.type_match
        EntryType.BIRTHDAY -> R.string.type_birthday
        EntryType.APPOINTMENT -> R.string.type_appointment
        EntryType.FLIGHT -> R.string.type_flight
        EntryType.EXAM -> R.string.type_exam
        EntryType.OTHER -> R.string.type_other
    }

@StringRes
fun Importance.labelRes(): Int =
    when (this) {
        Importance.LOW -> R.string.importance_low
        Importance.NORMAL -> R.string.importance_normal
        Importance.IMPORTANT -> R.string.importance_important
        Importance.CRITICAL -> R.string.importance_critical
    }

@StringRes
fun Importance.descriptionRes(): Int =
    when (this) {
        Importance.LOW -> R.string.importance_low_desc
        Importance.NORMAL -> R.string.importance_normal_desc
        Importance.IMPORTANT -> R.string.importance_important_desc
        Importance.CRITICAL -> R.string.importance_critical_desc
    }

@StringRes
fun ReminderStyle.labelRes(): Int =
    when (this) {
        ReminderStyle.SILENT -> R.string.style_silent
        ReminderStyle.NOTIFICATION -> R.string.style_notification
        ReminderStyle.HEADS_UP -> R.string.style_heads_up
        ReminderStyle.ALARM -> R.string.style_alarm
    }

/** The next style when the user taps a reminder's style: quiet to loud, then back to silent. */
fun ReminderStyle.next(): ReminderStyle = ReminderStyle.entries[(ordinal + 1) % ReminderStyle.entries.size]

/** "5 min", "1 h", "1 h 30 min", "1 day". */
@Composable
fun offsetLabel(offset: Duration): String {
    val totalMinutes = offset.toMinutes()
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts += pluralStringResource(R.plurals.days_short, days.toInt(), days.toInt())
    if (hours > 0) parts += stringResource(R.string.hours_short, hours.toInt())
    if (minutes > 0 || parts.isEmpty()) parts += stringResource(R.string.minutes_short, minutes.toInt())
    return parts.joinToString(" ")
}

/** "I'll remind you 30 min, 15 min and 5 min before." */
@Composable
fun reminderSentence(chain: ReminderChain): String {
    if (!chain.enabled) return stringResource(R.string.reminder_sentence_off)
    val steps = chain.orderedSteps.filter { it.enabled && it.style != ReminderStyle.SILENT }
    if (steps.isEmpty()) return stringResource(R.string.reminder_sentence_none)
    val labels = steps.map { if (it.offset.isZero) stringResource(R.string.at_start) else offsetLabel(it.offset) }
    val joined =
        if (labels.size == 1) {
            labels.single()
        } else {
            stringResource(R.string.list_and, labels.dropLast(1).joinToString(", "), labels.last())
        }
    val before = if (steps.all { it.offset.isZero }) joined else stringResource(R.string.offset_before, joined)
    return stringResource(R.string.reminder_sentence, before)
}

/** True when [chain] differs from the default chain of [importance]. */
fun ReminderChain.isCustomFor(
    importance: Importance,
    type: com.mazidulhaque.kokhon.core.model.EntryType,
): Boolean = orderedSteps != ReminderChain.defaultFor(importance, type).orderedSteps

@StringRes
fun com.mazidulhaque.kokhon.core.model.ReminderPurpose.labelRes(): Int =
    when (this) {
        com.mazidulhaque.kokhon.core.model.ReminderPurpose.GET_READY -> R.string.purpose_get_ready
        com.mazidulhaque.kokhon.core.model.ReminderPurpose.LEAVE_NOW -> R.string.purpose_leave_now
        com.mazidulhaque.kokhon.core.model.ReminderPurpose.PACK -> R.string.purpose_pack
        com.mazidulhaque.kokhon.core.model.ReminderPurpose.CHECK_IN -> R.string.purpose_check_in
        com.mazidulhaque.kokhon.core.model.ReminderPurpose.PREPARE -> R.string.purpose_prepare
        com.mazidulhaque.kokhon.core.model.ReminderPurpose.BUY_GIFT -> R.string.purpose_buy_gift
    }

/** "Past" when the time went by, "Done" when the user ticked it off. */
@StringRes
fun com.mazidulhaque.kokhon.core.model.Entry.statusWordRes(now: java.time.Instant): Int =
    when (statusAt(now)) {
        com.mazidulhaque.kokhon.core.model.EntryStatus.UPCOMING -> {
            R.string.status_upcoming
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.ONGOING -> {
            R.string.status_ongoing
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.COMPLETED -> {
            if (manualStatus == com.mazidulhaque.kokhon.core.model.EntryStatus.COMPLETED) R.string.status_done else R.string.status_past
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.CANCELLED -> {
            R.string.status_cancelled
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.MISSED -> {
            R.string.status_missed
        }
    }

/** A full sentence: "Coming up, in 2 h 30 min", "Past: this was 3 h ago", "Done ✓, marked by you". */
@Composable
fun statusSentence(
    entry: com.mazidulhaque.kokhon.core.model.Entry,
    now: java.time.Instant,
): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val distance =
        com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
            .duration(context, Duration.between(now, entry.start))
    return when (entry.statusAt(now)) {
        com.mazidulhaque.kokhon.core.model.EntryStatus.UPCOMING -> {
            stringResource(R.string.status_line_upcoming, distance)
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.ONGOING -> {
            stringResource(R.string.status_ongoing)
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.COMPLETED -> {
            if (entry.manualStatus == com.mazidulhaque.kokhon.core.model.EntryStatus.COMPLETED) {
                stringResource(R.string.status_line_done)
            } else {
                stringResource(R.string.status_line_past, distance)
            }
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.CANCELLED -> {
            stringResource(R.string.status_cancelled)
        }

        com.mazidulhaque.kokhon.core.model.EntryStatus.MISSED -> {
            stringResource(R.string.status_line_missed)
        }
    }
}
