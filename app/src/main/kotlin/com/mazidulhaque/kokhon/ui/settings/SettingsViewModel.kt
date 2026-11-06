package com.mazidulhaque.kokhon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazidulhaque.kokhon.core.data.settings.ReminderSettings
import com.mazidulhaque.kokhon.core.data.settings.SettingsRepository
import com.mazidulhaque.kokhon.core.model.AlarmBehavior
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class SettingsViewModel(
    private val repository: SettingsRepository,
    val clock: Clock,
) : ViewModel() {
    val settings: StateFlow<ReminderSettings?> =
        repository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    fun updateAlarm(change: (AlarmBehavior) -> AlarmBehavior) {
        val current = settings.value ?: return
        viewModelScope.launch { repository.setAlarmBehavior(change(current.alarm)) }
    }

    fun setCriticalBypass(enabled: Boolean) {
        viewModelScope.launch { repository.setCriticalBypassesSilent(enabled) }
    }

    enum class DiscreetChoice { OFF, ONE_HOUR, TWO_HOURS, UNTIL_MORNING }

    fun setDiscreet(choice: DiscreetChoice) {
        val now = clock.instant()
        val until: Instant? =
            when (choice) {
                DiscreetChoice.OFF -> {
                    null
                }

                DiscreetChoice.ONE_HOUR -> {
                    now.plus(Duration.ofHours(1))
                }

                DiscreetChoice.TWO_HOURS -> {
                    now.plus(Duration.ofHours(2))
                }

                DiscreetChoice.UNTIL_MORNING -> {
                    val today = LocalDate.now(clock)
                    val morning = if (LocalTime.now(clock).isBefore(LocalTime.of(7, 0))) today else today.plusDays(1)
                    morning.atTime(7, 0).atZone(clock.zone).toInstant()
                }
            }
        viewModelScope.launch { repository.setDiscreetUntil(until) }
    }
}
