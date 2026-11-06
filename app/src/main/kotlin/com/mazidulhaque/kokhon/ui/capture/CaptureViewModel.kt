package com.mazidulhaque.kokhon.ui.capture

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazidulhaque.kokhon.core.alarm.ReminderCoordinator
import com.mazidulhaque.kokhon.core.data.repository.EntryRepository
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntrySource
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderChain
import com.mazidulhaque.kokhon.core.model.clashesIn
import com.mazidulhaque.kokhon.core.model.duplicateIn
import com.mazidulhaque.kokhon.core.ocr.ScreenshotReadException
import com.mazidulhaque.kokhon.core.ocr.ScreenshotReader
import com.mazidulhaque.kokhon.core.parser.ChangeRequest
import com.mazidulhaque.kokhon.core.parser.MatchCandidate
import com.mazidulhaque.kokhon.core.parser.PlanDraft
import com.mazidulhaque.kokhon.core.parser.PlanParser
import com.mazidulhaque.kokhon.core.parser.Span
import com.mazidulhaque.kokhon.core.parser.UpdateMatcher
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
import java.time.ZonedDateTime

data class ImageError(
    val reason: ScreenshotReadException.Reason,
    val details: String,
)

/** A plan found in the message, as the user is editing it. */
data class DraftUi(
    val parsed: PlanDraft,
    val title: String,
    val type: EntryType,
    val date: LocalDate,
    val time: LocalTime,
    val approximate: Boolean,
    val importance: Importance,
    val reminders: ReminderChain,
    val include: Boolean = true,
) {
    /** Both readings of an ambiguous time ("9:30" = AM or PM), morning first, while the user is choosing between them. */
    val amPmChoices: List<LocalTime>?
        get() {
            val other = parsed.alternativeTime ?: return null
            return if (time == parsed.time || time == other) listOf(parsed.time, other).sorted() else null
        }
}

data class CandidateUi(
    val candidate: MatchCandidate,
    val selected: Boolean,
)

enum class CaptureMode { INPUT, READING, CREATE, UPDATE, NOTHING_TO_CHANGE, NOTHING_FOUND }

data class CaptureState(
    val text: String = "",
    val mode: CaptureMode = CaptureMode.INPUT,
    val spans: List<Span> = emptyList(),
    val drafts: List<DraftUi> = emptyList(),
    val current: Int = 0,
    val candidates: List<CandidateUi> = emptyList(),
    val target: Entry? = null,
    /** The plans just saved or updated, shown in the confirmation. */
    val saved: List<Entry>? = null,
    val savedWasUpdate: Boolean = false,
    /** Preview of the screenshot the text came from. */
    val thumbnail: Bitmap? = null,
    /** When the message was written, if the screenshot showed it; dates are read relative to it. */
    val writtenAt: ZonedDateTime? = null,
    /** Why the last screenshot could not be read, with details the user can copy. */
    val imageError: ImageError? = null,
)

class CaptureViewModel(
    initialText: String,
    initialImage: Uri?,
    private var sourceKind: EntrySource.Kind,
    private val targetId: Long,
    private val parser: PlanParser,
    private val repository: EntryRepository,
    private val coordinator: ReminderCoordinator,
    private val clock: Clock,
    private val draftHolder: MutableStateFlow<Entry?>,
    private val reader: ScreenshotReader,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureState(text = initialText))
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()

    /** Nearby plans, to warn about clashes and duplicates. */
    val others: StateFlow<List<Entry>> =
        repository
            .observeFrom(clock.instant().minus(Duration.ofDays(1)))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clashes(
        draft: DraftUi,
        all: List<Entry>,
    ): List<Entry> = draft.toEntry().clashesIn(all)

    fun duplicate(
        draft: DraftUi,
        all: List<Entry>,
    ): Entry? = draft.toEntry().duplicateIn(all)

    init {
        viewModelScope.launch {
            val target = if (targetId != 0L) repository.get(targetId) else null
            mutableState.update { it.copy(target = target) }
            when {
                initialImage != null -> readImage(initialImage)
                initialText.isNotBlank() -> analyse(initialText)
            }
        }
    }

    /** Reads the text in a screenshot or photo, on the device, then looks for plans in it. */
    fun readImage(uri: Uri) {
        sourceKind = EntrySource.Kind.SCREENSHOT
        mutableState.update { it.copy(mode = CaptureMode.READING, imageError = null) }
        viewModelScope.launch {
            val thumbnail = reader.thumbnail(uri)
            mutableState.update { it.copy(thumbnail = thumbnail) }
            val message =
                try {
                    reader.read(uri)
                } catch (e: ScreenshotReadException) {
                    mutableState.update { it.copy(mode = CaptureMode.INPUT, imageError = ImageError(e.reason, details(e))) }
                    return@launch
                } catch (e: Exception) {
                    val error = ImageError(ScreenshotReadException.Reason.RECOGNITION_FAILED, details(e))
                    mutableState.update { it.copy(mode = CaptureMode.INPUT, imageError = error) }
                    return@launch
                }
            val writtenAt = message.reference.atZone(clock.zone)
            mutableState.update { it.copy(text = message.text, writtenAt = writtenAt.takeIf { message.referenceFromScreenshot }) }
            analyse(message.text)
        }
    }

    /** What went wrong, in a form the user can paste into a message to the developer. */
    private fun details(e: Throwable): String =
        buildString {
            append("Kokhon screenshot error\n")
            append(
                "Android ",
            ).append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append("), ")
            append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n')
            var cause: Throwable? = e
            while (cause != null) {
                append(cause.javaClass.name).append(": ").append(cause.message).append('\n')
                cause.suppressed.forEach {
                    append("  also tried: ")
                        .append(it.javaClass.name)
                        .append(": ")
                        .append(it.message)
                        .append('\n')
                }
                cause = cause.cause.takeIf { it !== cause }
            }
        }

    /** The moment relative dates are read from: when the message was written, or now. */
    private fun referenceNow(): ZonedDateTime = state.value.writtenAt ?: clock.instant().atZone(clock.zone)

    fun setText(text: String) = mutableState.update { it.copy(text = text) }

    fun editMessage() = mutableState.update { it.copy(mode = CaptureMode.INPUT) }

    fun analyse(text: String = state.value.text) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val now = referenceNow()
            val target = state.value.target
            val result = parser.parse(text, now)
            when {
                target != null -> {
                    // The user picked the plan first: compare only against it.
                    val request = result.change ?: result.drafts.firstOrNull()?.toChangeRequest()
                    val newStart = request?.let { UpdateMatcher.proposeStart(it, target, clock.zone) }
                    val cancel = request?.kind == ChangeRequest.Kind.CANCEL
                    val candidate = MatchCandidate(target, 0, emptyList(), newStart, cancel)
                    mutableState.update {
                        it.copy(
                            text = text,
                            spans = result.change?.spans ?: result.drafts.flatMap { d -> d.spans },
                            candidates = listOf(CandidateUi(candidate, selected = true)),
                            mode = if (candidate.changesSomething) CaptureMode.UPDATE else CaptureMode.NOTHING_TO_CHANGE,
                        )
                    }
                }

                result.change != null -> {
                    val upcoming = repository.startingFrom(clock.instant().minus(Duration.ofHours(12)))
                    val matches = UpdateMatcher.match(result.change!!, upcoming, clock.instant(), clock.zone)
                    if (matches.isEmpty()) {
                        // Nothing to change: treat the message as a new plan.
                        showDrafts(text, parser.parse(text, now, allowChange = false).drafts)
                    } else {
                        mutableState.update {
                            it.copy(
                                text = text,
                                spans = result.change!!.spans,
                                candidates = matches.mapIndexed { i, c -> CandidateUi(c, selected = i == 0) },
                                mode = if (matches.first().changesSomething) CaptureMode.UPDATE else CaptureMode.NOTHING_TO_CHANGE,
                            )
                        }
                    }
                }

                else -> {
                    showDrafts(text, result.drafts)
                }
            }
        }
    }

    private fun showDrafts(
        text: String,
        drafts: List<PlanDraft>,
    ) {
        mutableState.update {
            it.copy(
                text = text,
                spans = drafts.flatMap { d -> d.spans },
                drafts =
                    drafts.map { d ->
                        DraftUi(
                            d,
                            d.title,
                            d.type,
                            d.date,
                            d.time,
                            d.approximate,
                            d.importance,
                            ReminderChain.defaultFor(d.importance, d.type),
                        )
                    },
                current = 0,
                mode = if (drafts.isEmpty()) CaptureMode.NOTHING_FOUND else CaptureMode.CREATE,
            )
        }
    }

    /** Switches from "update an existing plan" to "make a new plan" for the same message. */
    fun treatAsNewPlan() {
        val now = referenceNow()
        mutableState.update { it.copy(target = null) }
        showDrafts(state.value.text, parser.parse(state.value.text, now, allowChange = false).drafts)
    }

    // ---- editing a draft

    fun select(index: Int) = mutableState.update { it.copy(current = index) }

    private fun updateDraft(change: (DraftUi) -> DraftUi) =
        mutableState.update { s ->
            s.copy(drafts = s.drafts.mapIndexed { i, d -> if (i == s.current) change(d) else d })
        }

    fun setTitle(value: String) = updateDraft { it.copy(title = value) }

    fun setType(type: EntryType) =
        updateDraft {
            val importance = maxOf(type.defaultImportance, it.parsed.importance, compareBy { level -> level.ordinal })
            it.copy(type = type, importance = importance, reminders = ReminderChain.defaultFor(importance, type))
        }

    fun setDate(date: LocalDate) = updateDraft { it.copy(date = date) }

    fun setTime(time: LocalTime) = updateDraft { it.copy(time = time) }

    fun nudge(minutes: Long) =
        updateDraft {
            val moved = LocalDateTime.of(it.date, it.time).plusMinutes(minutes)
            it.copy(date = moved.toLocalDate(), time = moved.toLocalTime())
        }

    fun setApproximate(value: Boolean) = updateDraft { it.copy(approximate = value) }

    fun setImportance(level: Importance) = updateDraft { it.copy(importance = level, reminders = ReminderChain.defaultFor(level, it.type)) }

    fun setReminders(chain: ReminderChain) = updateDraft { it.copy(reminders = chain) }

    fun setIncluded(
        index: Int,
        include: Boolean,
    ) = mutableState.update { s -> s.copy(drafts = s.drafts.mapIndexed { i, d -> if (i == index) d.copy(include = include) else d }) }

    fun isPast(draft: DraftUi): Boolean = startOf(draft).isBefore(clock.instant())

    fun startOf(draft: DraftUi): Instant = LocalDateTime.of(draft.date, draft.time).atZone(clock.zone).toInstant()

    private fun DraftUi.toEntry(): Entry =
        Entry(
            title = title.trim().ifBlank { parsed.title },
            type = type,
            start = startOf(this),
            duration = parsed.endTime?.let { end -> Duration.between(parsed.time, end).takeIf { !it.isNegative && !it.isZero } },
            isApproximate = approximate,
            location = parsed.location,
            people = parsed.people,
            importance = importance,
            reminders = reminders,
            source = EntrySource(sourceKind, state.value.text, clock.instant()),
        )

    fun saveDrafts() {
        val chosen = state.value.drafts.filter { it.include }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val saved = chosen.map { coordinator.save(it.toEntry(), sourceText = state.value.text) }
            mutableState.update { it.copy(saved = saved, savedWasUpdate = false) }
        }
    }

    /** Hands the current draft to the full editor. */
    fun editAllDetails(): Boolean {
        val draft = state.value.drafts.getOrNull(state.value.current) ?: return false
        draftHolder.value = draft.toEntry()
        return true
    }

    /** For "Make a plan anyway" when nothing was found: the message becomes the notes. */
    fun startBlankDraft() {
        val text = state.value.text.trim()
        draftHolder.value =
            Entry(
                title = text.lineSequence().first().take(48),
                type = EntryType.OTHER,
                start = clock.instant().plus(Duration.ofHours(1)),
                notes = text,
                source = EntrySource(sourceKind, text, clock.instant()),
            )
    }

    // ---- updating existing plans

    fun toggleCandidate(index: Int) =
        mutableState.update { s ->
            s.copy(candidates = s.candidates.mapIndexed { i, c -> if (i == index) c.copy(selected = !c.selected) else c })
        }

    fun applyUpdates() {
        val chosen = state.value.candidates.filter { it.selected && it.candidate.changesSomething }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            chosen.forEach { (candidate, _) ->
                val entry = candidate.entry
                val updated =
                    if (candidate.cancel) {
                        entry.copy(manualStatus = EntryStatus.CANCELLED)
                    } else {
                        entry.rescheduledTo(candidate.newStart!!)
                    }
                coordinator.save(updated, sourceText = state.value.text)
            }
            val updated = chosen.mapNotNull { repository.get(it.candidate.entry.id) }
            mutableState.update { it.copy(saved = updated, savedWasUpdate = true) }
        }
    }

    private fun PlanDraft.toChangeRequest() =
        ChangeRequest(
            kind = ChangeRequest.Kind.MOVE,
            newDate = date.takeIf { !dateGuessed },
            newTime = time.takeIf { !timeGuessed || alternativeTime != null },
            newTimeAmbiguous = alternativeTime != null,
        )
}
