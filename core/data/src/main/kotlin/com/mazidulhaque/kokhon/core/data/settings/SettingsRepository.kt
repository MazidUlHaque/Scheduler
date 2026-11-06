package com.mazidulhaque.kokhon.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mazidulhaque.kokhon.core.model.AlarmBehavior
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

/** User settings that affect how reminders ring. */
data class ReminderSettings(
    val alarm: AlarmBehavior = AlarmBehavior(),
    /** Discreet mode turns every alarm into a vibrate-only notification until this moment. */
    val discreetUntil: Instant? = null,
    /** When true, Critical entries may ring even when the phone is on silent or Do Not Disturb. */
    val criticalBypassesSilent: Boolean = false,
) {
    fun isDiscreetAt(now: Instant): Boolean = discreetUntil?.isAfter(now) == true
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(
    context: Context,
) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val ringLimitSeconds = intPreferencesKey("ring_limit_seconds")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val maxSnoozes = intPreferencesKey("max_snoozes")
        val volumeSilences = booleanPreferencesKey("volume_button_silences")
        val gentleStart = booleanPreferencesKey("gentle_start")
        val discreetUntil = longPreferencesKey("discreet_until_millis")
        val criticalBypass = booleanPreferencesKey("critical_bypasses_silent")
    }

    val settings: Flow<ReminderSettings> =
        store.data.map { p ->
            val defaults = AlarmBehavior()
            ReminderSettings(
                alarm =
                    AlarmBehavior(
                        ringLimit =
                            Duration.ofSeconds(
                                (p[Keys.ringLimitSeconds] ?: defaults.ringLimit.seconds.toInt()).toLong(),
                            ),
                        snoozeLength =
                            Duration.ofMinutes(
                                (
                                    p[Keys.snoozeMinutes]
                                        ?: defaults.snoozeLength.toMinutes().toInt()
                                ).toLong(),
                            ),
                        maxSnoozes = p[Keys.maxSnoozes] ?: defaults.maxSnoozes,
                        volumeButtonSilences = p[Keys.volumeSilences] ?: defaults.volumeButtonSilences,
                        gentleStart = p[Keys.gentleStart] ?: defaults.gentleStart,
                    ),
                discreetUntil = p[Keys.discreetUntil]?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                criticalBypassesSilent = p[Keys.criticalBypass] ?: false,
            )
        }

    suspend fun current(): ReminderSettings = settings.first()

    suspend fun setAlarmBehavior(behavior: AlarmBehavior) {
        store.edit { p ->
            p[Keys.ringLimitSeconds] = behavior.ringLimit.seconds.toInt()
            p[Keys.snoozeMinutes] = behavior.snoozeLength.toMinutes().toInt()
            p[Keys.maxSnoozes] = behavior.maxSnoozes
            p[Keys.volumeSilences] = behavior.volumeButtonSilences
            p[Keys.gentleStart] = behavior.gentleStart
        }
    }

    /** Pass null to turn discreet mode off. */
    suspend fun setDiscreetUntil(until: Instant?) {
        store.edit { it[Keys.discreetUntil] = until?.toEpochMilli() ?: 0L }
    }

    suspend fun setCriticalBypassesSilent(enabled: Boolean) {
        store.edit { it[Keys.criticalBypass] = enabled }
    }
}
