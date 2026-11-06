package com.mazidulhaque.kokhon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazidulhaque.kokhon.core.alarm.ReminderCoordinator
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.data.settings.SettingsRepository
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One row of the timeline. */
sealed interface TimelineRow {
    data class DayHeader(
        val date: LocalDate,
    ) : TimelineRow

    data class Item(
        val entry: Entry,
        val status: EntryStatus,
    ) : TimelineRow {
        val finished: Boolean get() =
            status == EntryStatus.COMPLETED || status == EntryStatus.CANCELLED ||
                status == EntryStatus.MISSED
    }

    data class NowLine(
        val now: Instant,
    ) : TimelineRow
}

data class HomeState(
    val now: Instant,
    val today: LocalDate,
    val rows: List<TimelineRow>,
    val leftToday: Int,
    val next: Entry?,
    val ongoing: Entry?,
    val isEmpty: Boolean,
    val discreetUntil: Instant?,
)

class HomeViewModel(
    repository: EntryRepository,
    private val coordinator: ReminderCoordinator,
    private val settings: SettingsRepository,
    private val clock: Clock,
    val pendingUndo: MutableStateFlow<Entry?>,
) : ViewModel() {
    private val zone: ZoneId get() = clock.zone

    private val ticker =
        flow {
            while (true) {
                emit(clock.instant())
                delay(30_000)
            }
        }

    private val startOfToday = LocalDate.now(clock).atStartOfDay(zone).toInstant()

    val state: StateFlow<HomeState?> =
        combine(repository.observeFrom(startOfToday), ticker, settings.settings) { entries, now, prefs ->
            build(entries, now, prefs.discreetUntil?.takeIf { prefs.isDiscreetAt(now) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(
        entries: List<Entry>,
        now: Instant,
        discreetUntil: Instant?,
    ): HomeState {
        val today = now.atZone(zone).toLocalDate()
        val byDay = entries.groupBy { it.start.atZone(zone).toLocalDate() }.toSortedMap()
        val rows = mutableListOf<TimelineRow>()
        val todayItems = byDay[today].orEmpty().map { TimelineRow.Item(it, it.statusAt(now)) }
        // Position communicates time: finished plans above the Now line, everything else below.
        rows += todayItems.filter { it.finished }
        rows += TimelineRow.NowLine(now)
        rows += todayItems.filterNot { it.finished }
        byDay.filterKeys { it.isAfter(today) }.forEach { (date, dayEntries) ->
            rows += TimelineRow.DayHeader(date)
            rows += dayEntries.map { TimelineRow.Item(it, it.statusAt(now)) }
        }
        val unfinished = entries.filter { !it.isFinishedAt(now) }
        val ongoing = unfinished.firstOrNull { it.statusAt(now) == EntryStatus.ONGOING }
        return HomeState(
            now = now,
            today = today,
            rows = rows,
            leftToday = todayItems.count { !it.finished },
            next = unfinished.firstOrNull { it.start.isAfter(now) },
            ongoing = ongoing,
            isEmpty = entries.isEmpty(),
            discreetUntil = discreetUntil,
        )
    }

    fun setDone(
        entry: Entry,
        done: Boolean,
    ) {
        viewModelScope.launch { coordinator.setStatus(entry.id, if (done) EntryStatus.COMPLETED else null) }
    }

    fun toggleDiscreet(currentlyOn: Boolean) {
        viewModelScope.launch {
            settings.setDiscreetUntil(if (currentlyOn) null else clock.instant().plus(Duration.ofHours(1)))
        }
    }

    fun undoDelete(entry: Entry) {
        pendingUndo.value = null
        viewModelScope.launch { coordinator.restore(entry) }
    }

    fun dismissUndo() {
        pendingUndo.value = null
    }
}
