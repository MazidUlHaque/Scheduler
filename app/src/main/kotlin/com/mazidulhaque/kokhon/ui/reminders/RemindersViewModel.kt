package com.mazidulhaque.kokhon.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazidulhaque.kokhon.core.alarm.ReminderCoordinator
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.data.settings.SettingsRepository
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderChain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemindersState(
    val entry: Entry,
    val importance: Importance,
    val chain: ReminderChain,
)

class RemindersViewModel(
    id: Long,
    private val repository: EntryRepository,
    private val coordinator: ReminderCoordinator,
    settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<RemindersState?>(null)
    val state: StateFlow<RemindersState?> = mutableState.asStateFlow()
    val settings = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            repository.get(id)?.let { mutableState.value = RemindersState(it, it.importance, it.reminders) }
        }
    }

    fun setImportance(level: Importance) =
        mutableState.update {
            it?.copy(importance = level, chain = ReminderChain.defaultFor(level))
        }

    fun setChain(chain: ReminderChain) = mutableState.update { it?.copy(chain = chain) }

    fun setAllEnabled(enabled: Boolean) = mutableState.update { it?.copy(chain = it.chain.copy(enabled = enabled)) }

    fun save(onSaved: () -> Unit) {
        val s = state.value ?: return
        viewModelScope.launch {
            coordinator.save(s.entry.copy(importance = s.importance, reminders = s.chain))
            onSaved()
        }
    }
}
