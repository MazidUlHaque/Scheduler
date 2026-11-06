package com.mazidulhaque.kokhon.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mazidulhaque.kokhon.core.data.db.reminderChainFromJson
import com.mazidulhaque.kokhon.core.data.db.toJson
import com.mazidulhaque.kokhon.core.model.AlarmBehavior
import com.mazidulhaque.kokhon.core.model.ReminderPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/** Light or dark look: follow the phone, or always one of them. */
enum class Appearance { SYSTEM, LIGHT, DARK }

/** User settings that affect how reminders ring and how the app looks. */
data class ReminderSettings(
    val alarm: AlarmBehavior = AlarmBehavior(),
    val appearance: Appearance = Appearance.SYSTEM,
    /** Discreet mode turns every alarm into a vibrate-only notification until this moment. */
    val discreetUntil: Instant? = null,
    /** When true, Critical entries may ring even when the phone is on silent or Do Not Disturb. */
    val criticalBypassesSilent: Boolean = false,
    /** A short summary of today's plans each morning. */
    val morningSummary: Boolean = true,
    /** A short summary of tomorrow's plans each evening. */
    val eveningSummary: Boolean = false,
    /** Offer to make a plan when a new screenshot contains one. Off unless the user turns it on. */
    val watchScreenshots: Boolean = false,
    val presets: List<ReminderPreset> = emptyList(),
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
        val appearance = stringPreferencesKey("appearance")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val maxSnoozes = intPreferencesKey("max_snoozes")
        val volumeSilences = booleanPreferencesKey("volume_button_silences")
        val gentleStart = booleanPreferencesKey("gentle_start")
        val discreetUntil = longPreferencesKey("discreet_until_millis")
        val criticalBypass = booleanPreferencesKey("critical_bypasses_silent")
        val morningSummary = booleanPreferencesKey("morning_summary")
        val eveningSummary = booleanPreferencesKey("evening_summary")
        val watchScreenshots = booleanPreferencesKey("watch_screenshots")
        val presets = stringPreferencesKey("reminder_presets")
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
                appearance = Appearance.entries.firstOrNull { it.name == p[Keys.appearance] } ?: Appearance.SYSTEM,
                discreetUntil = p[Keys.discreetUntil]?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                criticalBypassesSilent = p[Keys.criticalBypass] ?: false,
                morningSummary = p[Keys.morningSummary] ?: true,
                eveningSummary = p[Keys.eveningSummary] ?: false,
                watchScreenshots = p[Keys.watchScreenshots] ?: false,
                presets = p[Keys.presets]?.let(::decodePresets).orEmpty(),
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

    suspend fun setSummaries(
        morning: Boolean,
        evening: Boolean,
    ) {
        store.edit {
            it[Keys.morningSummary] = morning
            it[Keys.eveningSummary] = evening
        }
    }

    suspend fun setAppearance(appearance: Appearance) {
        store.edit { it[Keys.appearance] = appearance.name }
    }

    suspend fun setWatchScreenshots(enabled: Boolean) {
        store.edit { it[Keys.watchScreenshots] = enabled }
    }

    /** Saves a preset; a preset with the same name is replaced. */
    suspend fun savePreset(preset: ReminderPreset) {
        store.edit { p ->
            val current = p[Keys.presets]?.let(::decodePresets).orEmpty().filterNot { it.name.equals(preset.name, ignoreCase = true) }
            p[Keys.presets] = encodePresets(current + preset)
        }
    }

    suspend fun deletePreset(name: String) {
        store.edit { p ->
            p[Keys.presets] = encodePresets(p[Keys.presets]?.let(::decodePresets).orEmpty().filterNot { it.name == name })
        }
    }

    private fun encodePresets(presets: List<ReminderPreset>): String =
        presetJson.encodeToString(ListSerializer(PresetJson.serializer()), presets.map { PresetJson(it.name, it.chain.toJson()) })

    private fun decodePresets(value: String): List<ReminderPreset> =
        runCatching {
            presetJson
                .decodeFromString(
                    ListSerializer(PresetJson.serializer()),
                    value,
                ).map { ReminderPreset(it.name, reminderChainFromJson(it.chain)) }
        }.getOrDefault(emptyList())
}

@Serializable
private data class PresetJson(
    val name: String,
    val chain: String,
)

private val presetJson = Json { ignoreUnknownKeys = true }
