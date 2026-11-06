package com.mazidulhaque.kokhon.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazidulhaque.kokhon.core.alarm.ReminderCoordinator
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderChain
import com.mazidulhaque.kokhon.core.model.clashesIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class EditorState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val title: String = "",
    val type: EntryType = EntryType.MEETING,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(9, 0),
    val approximate: Boolean = false,
    val durationMinutes: Long? = null,
    val location: String = "",
    val notes: String = "",
    val importance: Importance = EntryType.MEETING.defaultImportance,
    val reminders: ReminderChain = ReminderChain.defaultFor(EntryType.MEETING.defaultImportance, EntryType.MEETING),
    /** Once the user picks an importance, changing the type no longer overrides it. */
    val importanceTouched: Boolean = false,
    val showTitleError: Boolean = false,
    val savedId: Long? = null,
    val savedEntry: Entry? = null,
)

class EditorViewModel(
    private val id: Long,
    /** A plan found in a message, to finish here. Saved as a new entry that keeps its source message. */
    private val prefill: Entry?,
    private val repository: EntryRepository,
    private val coordinator: ReminderCoordinator,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = mutableState.asStateFlow()
    private var original: Entry? = null

    /** Nearby plans, to warn about clashes. */
    val others: StateFlow<List<Entry>> =
        repository
            .observeFrom(clock.instant().minus(Duration.ofDays(1)))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Other plans at the same time as the one being edited. */
    fun clashes(
        s: EditorState,
        all: List<Entry>,
    ): List<Entry> =
        Entry(id = id, title = s.title, type = s.type, start = startInstant(s), duration = s.durationMinutes?.let(Duration::ofMinutes))
            .clashesIn(all.filter { it.id != id })

    init {
        viewModelScope.launch {
            val existing = if (id != 0L) repository.get(id) else null
            original = existing
            val base = existing ?: prefill
            mutableState.value =
                if (base != null) {
                    val existing = base
                    val local = existing.start.atZone(clock.zone)
                    EditorState(
                        loaded = true,
                        isNew = original == null,
                        title = existing.title,
                        type = existing.type,
                        date = local.toLocalDate(),
                        time = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                        approximate = existing.isApproximate,
                        durationMinutes = existing.duration?.toMinutes(),
                        location = existing.location.orEmpty(),
                        notes = existing.notes.orEmpty(),
                        importance = existing.importance,
                        reminders = existing.reminders,
                        importanceTouched = true,
                    )
                } else {
                    // Default to the next full hour, so a new plan is never in the past.
                    val next = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS).plusHours(1)
                    EditorState(loaded = true, date = next.toLocalDate(), time = next.toLocalTime())
                }
        }
    }

    fun startInstant(s: EditorState = state.value): Instant = LocalDateTime.of(s.date, s.time).atZone(clock.zone).toInstant()

    fun isInPast(): Boolean = startInstant().isBefore(clock.instant())

    fun setTitle(value: String) = mutableState.update { it.copy(title = value, showTitleError = false) }

    fun setType(type: EntryType) =
        mutableState.update {
            if (it.importanceTouched) {
                it.copy(type = type)
            } else {
                it.copy(
                    type = type,
                    importance = type.defaultImportance,
                    reminders = ReminderChain.defaultFor(type.defaultImportance, type),
                )
            }
        }

    fun setDate(date: LocalDate) = mutableState.update { it.copy(date = date) }

    fun setTime(time: LocalTime) = mutableState.update { it.copy(time = time) }

    fun nudge(minutes: Long) =
        mutableState.update {
            val moved = LocalDateTime.of(it.date, it.time).plusMinutes(minutes)
            it.copy(date = moved.toLocalDate(), time = moved.toLocalTime())
        }

    fun setApproximate(value: Boolean) = mutableState.update { it.copy(approximate = value) }

    fun setDuration(minutes: Long?) = mutableState.update { it.copy(durationMinutes = minutes) }

    fun setLocation(value: String) = mutableState.update { it.copy(location = value) }

    fun setNotes(value: String) = mutableState.update { it.copy(notes = value) }

    fun setImportance(level: Importance) =
        mutableState.update {
            it.copy(importance = level, reminders = ReminderChain.defaultFor(level, it.type), importanceTouched = true)
        }

    fun setReminders(chain: ReminderChain) = mutableState.update { it.copy(reminders = chain, importanceTouched = true) }

    fun save() {
        val s = state.value
        if (s.title.isBlank()) {
            mutableState.update { it.copy(showTitleError = true) }
            return
        }
        val start = startInstant(s)
        val base = original
        val entry =
            (base ?: prefill ?: Entry(title = s.title, type = s.type, start = start)).copy(
                title = s.title.trim(),
                type = s.type,
                start = start,
                duration = s.durationMinutes?.let(Duration::ofMinutes),
                isApproximate = s.approximate,
                location = s.location.trim().ifBlank { null },
                notes = s.notes.trim().ifBlank { null },
                importance = s.importance,
                reminders = s.reminders,
                // Moving a finished plan to a new time makes it active again.
                manualStatus = if (base != null && base.start != start) null else base?.manualStatus,
            )
        viewModelScope.launch {
            val saved = coordinator.save(entry, sourceText = if (base == null) prefill?.source?.originalText else null)
            mutableState.update { it.copy(savedId = saved.id, savedEntry = saved) }
        }
    }
}
