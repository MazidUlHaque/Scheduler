package com.mazidulhaque.kokhon.core.alarm

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
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
) {
    suspend fun save(entry: Entry): Entry {
        val saved = repository.save(entry)
        scheduler.schedule(saved)
        return saved
    }

    suspend fun delete(entry: Entry) {
        scheduler.cancel(entry.id)
        clearNotifications(entry.id)
        repository.delete(entry.id)
    }

    /** Restores a deleted entry (Undo). Keeps its id so links still work. */
    suspend fun restore(entry: Entry): Entry = save(entry)

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
