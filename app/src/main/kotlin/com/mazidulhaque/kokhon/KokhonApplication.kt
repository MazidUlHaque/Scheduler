package com.mazidulhaque.kokhon

import android.app.Application
import android.content.Context
import android.content.Intent
import com.mazidulhaque.kokhon.core.alarm.AlarmScheduler
import com.mazidulhaque.kokhon.core.alarm.DailySummary
import com.mazidulhaque.kokhon.core.alarm.ReminderCoordinator
import com.mazidulhaque.kokhon.core.alarm.ReminderHost
import com.mazidulhaque.kokhon.core.alarm.ReminderNotifier
import com.mazidulhaque.kokhon.core.data.repository.createEntryRepository
import com.mazidulhaque.kokhon.core.data.settings.SettingsRepository
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.ocr.ScreenshotReader
import com.mazidulhaque.kokhon.core.parser.ParserConfig
import com.mazidulhaque.kokhon.core.parser.PlanParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek

/**
 * Application entry point and the app's object graph. Dependencies are wired by hand:
 * the graph is small, and avoiding a DI framework keeps builds fast.
 */
class KokhonApplication :
    Application(),
    ReminderHost {
    override val clock: Clock = Clock.systemDefaultZone()

    override val entryRepository by lazy { createEntryRepository(this, clock) }
    override val settingsRepository by lazy { SettingsRepository(this) }
    override val alarmScheduler by lazy { AlarmScheduler(this, clock) }
    override val reminderCoordinator by lazy { ReminderCoordinator(this, entryRepository, alarmScheduler, clock) }

    /** Bangladesh (and a few others) have a Friday weekend, which changes what "this weekend" means. */
    val planParser by lazy {
        val fridayWeekend = setOf("Asia/Dhaka", "Asia/Riyadh", "Asia/Kuwait", "Asia/Qatar", "Asia/Bahrain", "Asia/Muscat")
        PlanParser(ParserConfig(weekendStart = if (clock.zone.id in fridayWeekend) DayOfWeek.FRIDAY else DayOfWeek.SATURDAY))
    }

    val screenshotReader by lazy { ScreenshotReader(this, clock) }

    /** A plan found in a message that the user wants to finish in the full editor. */
    val captureDraft = MutableStateFlow<Entry?>(null)

    /** The most recently deleted entry, offered for Undo on the home screen. */
    val pendingUndo = MutableStateFlow<Entry?>(null)

    val dailySummary by lazy { DailySummary(this, clock) }

    /** For work that outlives any screen, such as scheduling the daily summary at start-up. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.ensureChannels(this)
        appScope.launch {
            val settings = settingsRepository.current()
            dailySummary.scheduleNext(settings.morningSummary, settings.eveningSummary)
        }
    }

    override fun openEntryIntent(
        context: Context,
        entryId: Long,
    ): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_ENTRY)
            .putExtra(MainActivity.EXTRA_ENTRY_ID, entryId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
