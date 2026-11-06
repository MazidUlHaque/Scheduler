package com.mazidulhaque.kokhon.core.alarm

import android.content.Context
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.data.settings.SettingsRepository
import java.time.Clock

/**
 * Implemented by the Application so that receivers and services, which Android creates
 * on its own, can reach the app's single instances.
 */
interface ReminderHost {
    val clock: Clock
    val entryRepository: EntryRepository
    val settingsRepository: SettingsRepository
    val alarmScheduler: AlarmScheduler
    val reminderCoordinator: ReminderCoordinator

    /** The intent that opens the app on a given entry, used by notifications. */
    fun openEntryIntent(
        context: Context,
        entryId: Long,
    ): android.content.Intent
}

internal val Context.reminderHost: ReminderHost
    get() = applicationContext as ReminderHost
