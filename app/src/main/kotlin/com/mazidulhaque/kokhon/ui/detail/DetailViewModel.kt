package com.mazidulhaque.kokhon.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazidulhaque.kokhon.core.alarm.ReminderCoordinator
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

class DetailViewModel(
    id: Long,
    repository: EntryRepository,
    private val coordinator: ReminderCoordinator,
    val clock: Clock,
    private val pendingUndo: MutableStateFlow<Entry?>,
) : ViewModel() {
    val entry: StateFlow<Entry?> =
        repository
            .observe(
                id,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun update(change: (Entry) -> Entry) {
        val current = entry.value ?: return
        viewModelScope.launch { coordinator.save(change(current)) }
    }

    fun rename(title: String) {
        if (title.isNotBlank()) update { it.copy(title = title.trim()) }
    }

    fun setRemindersEnabled(enabled: Boolean) = update { it.copy(reminders = it.reminders.copy(enabled = enabled)) }

    fun setStatus(status: EntryStatus?) {
        val current = entry.value ?: return
        viewModelScope.launch { coordinator.setStatus(current.id, status) }
    }

    fun duplicate(
        copyTitle: (String) -> String,
        onCreated: (Long) -> Unit,
    ) {
        val current = entry.value ?: return
        viewModelScope.launch {
            val copy = coordinator.save(current.copy(id = 0, title = copyTitle(current.title), manualStatus = null))
            onCreated(copy.id)
        }
    }

    /** Deletes and offers Undo on the home screen. */
    fun delete(onDeleted: () -> Unit) {
        val current = entry.value ?: return
        viewModelScope.launch {
            coordinator.delete(current)
            pendingUndo.value = current
            onDeleted()
        }
    }
}
