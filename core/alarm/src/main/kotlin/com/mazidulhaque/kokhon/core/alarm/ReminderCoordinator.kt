package com.mazidulhaque.kokhon.core.alarm

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.data.repository.HistoryItem
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ScheduledReminder
import java.time.Clock
import java.time.Duration

/**
 * Every change to an entry goes through here, so the database and the system alarms
 * can never disagree.
 */
class ReminderCoordinator(
    private val context: Context,
    private val repository: EntryRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
    /** Called after any change, e.g. to refresh home-screen widgets. */
    private val onChanged: suspend () -> Unit = {},
) {
    /**
     * Saves the entry, re-registers its alarms and records what changed in its history.
     *
     * @param sourceText the chat message that caused this change, if any.
     */
    suspend fun save(
        entry: Entry,
        sourceText: String? = null,
        recordHistory: Boolean = true,
    ): Entry {
        val before = if (entry.id != 0L) repository.get(entry.id) else null
        val saved = repository.save(entry)
        scheduler.schedule(saved)
        if (recordHistory) record(before, saved, sourceText)
        onChanged()
        return saved
    }

    private suspend fun record(
        before: Entry?,
        after: Entry,
        sourceText: String?,
    ) {
        if (before == null) {
            val kind = if (sourceText != null) HistoryItem.Kind.CREATED_FROM_MESSAGE else HistoryItem.Kind.CREATED
            val summary = context.getString(if (sourceText != null) R.string.history_created_from_message else R.string.history_created)
            repository.addHistory(after.id, kind, summary, sourceText)
            return
        }
        val lines = mutableListOf<String>()
        if (before.start != after.start) lines += context.getString(R.string.history_moved, describe(before), describe(after))
        if (before.title != after.title) lines += context.getString(R.string.history_renamed, after.title)
        if (before.manualStatus != after.manualStatus) {
            lines +=
                context.getString(
                    when (after.manualStatus) {
                        EntryStatus.COMPLETED -> R.string.history_completed
                        EntryStatus.CANCELLED -> R.string.history_cancelled
                        else -> R.string.history_reopened
                    },
                )
        }
        if (before.importance != after.importance) {
            lines += context.getString(R.string.history_importance, context.getString(importanceRes(after.importance)).lowercase())
        }
        if (before.reminders.enabled != after.reminders.enabled) {
            lines += context.getString(if (after.reminders.enabled) R.string.history_reminders_on else R.string.history_reminders_off)
        } else if (before.reminders != after.reminders && before.importance == after.importance) {
            lines += context.getString(R.string.history_reminders_changed)
        }
        if (lines.isEmpty() && before.copy(source = after.source) != after) lines += context.getString(R.string.history_details)
        if (lines.isEmpty()) return
        val onlyStatus = lines.size == 1 && before.manualStatus != after.manualStatus
        val kind =
            when {
                sourceText != null -> HistoryItem.Kind.UPDATED_FROM_MESSAGE
                onlyStatus -> HistoryItem.Kind.STATUS
                else -> HistoryItem.Kind.EDITED
            }
        repository.addHistory(after.id, kind, lines.joinToString("; "), sourceText)
    }

    private fun describe(entry: Entry): String = TimeFormat.shortDate(entry.start) + ", " + TimeFormat.time(context, entry.start)

    private fun importanceRes(level: Importance) =
        when (level) {
            Importance.LOW -> R.string.importance_low
            Importance.NORMAL -> R.string.importance_normal
            Importance.IMPORTANT -> R.string.importance_important
            Importance.CRITICAL -> R.string.importance_critical
        }

    suspend fun delete(entry: Entry) {
        scheduler.cancel(entry.id)
        clearNotifications(entry.id)
        repository.delete(entry.id)
        onChanged()
    }

    /** Restores a deleted entry (Undo). Keeps its id so links still work. */
    suspend fun restore(entry: Entry): Entry = save(entry, recordHistory = false)

    suspend fun setStatus(
        id: Long,
        status: EntryStatus?,
    ): Entry? {
        val entry = repository.get(id) ?: return null
        if (status == EntryStatus.COMPLETED || status == EntryStatus.CANCELLED) clearNotifications(id)
        return save(entry.copy(manualStatus = status))
    }

    suspend fun setRemindersEnabled(
        id: Long,
        enabled: Boolean,
    ): Entry? {
        val entry = repository.get(id) ?: return null
        if (!enabled) clearNotifications(id)
        return save(entry.copy(reminders = entry.reminders.copy(enabled = enabled)))
    }

    /** Re-registers alarms for everything that may still ring. Called after boot, time changes and updates. */
    suspend fun rescheduleAll() {
        val from = clock.instant().minus(Duration.ofDays(2))
        repository.startingFrom(from).forEach { scheduler.schedule(it) }
    }

    private fun clearNotifications(entryId: Long) {
        val manager = NotificationManagerCompat.from(context)
        for (index in 0 until ScheduledReminder.MAX_STEPS) {
            manager.cancel(ScheduledReminder.requestCodeFor(entryId, index))
        }
        manager.cancel(ReminderNotifier.quietNotificationId(entryId))
    }
}
