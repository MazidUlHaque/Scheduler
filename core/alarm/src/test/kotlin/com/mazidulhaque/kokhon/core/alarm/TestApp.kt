package com.mazidulhaque.kokhon.core.alarm

import android.app.Application
import android.content.Context
import android.content.Intent
import com.mazidulhaque.kokhon.core.data.repository.createEntryRepository
import com.mazidulhaque.kokhon.core.data.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Test application: the same object graph as the real app, with a fixed clock. */
class TestApp :
    Application(),
    ReminderHost {
    override val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    override val entryRepository by lazy { createEntryRepository(this, clock) }
    override val settingsRepository by lazy { SettingsRepository(this) }
    override val alarmScheduler by lazy { AlarmScheduler(this, clock) }
    override val reminderCoordinator by lazy { ReminderCoordinator(this, entryRepository, alarmScheduler, clock) }

    override fun openEntryIntent(
        context: Context,
        entryId: Long,
    ): Intent = Intent(Intent.ACTION_VIEW).setPackage(context.packageName).putExtra("entry_id", entryId)

    companion object {
        val NOW: Instant = Instant.parse("2026-11-07T14:00:00Z")
    }
}
