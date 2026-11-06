package com.mazidulhaque.kokhon.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * System alarms are lost on reboot and can drift when the clock or time zone changes,
 * so every such event re-registers all pending reminders.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED) return
        runAsync {
            val host = context.reminderHost
            host.reminderCoordinator.rescheduleAll()
            val settings = host.settingsRepository.current()
            DailySummary(context, host.clock).scheduleNext(settings.morningSummary, settings.eveningSummary)
        }
    }

    private companion object {
        val HANDLED =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )
    }
}
