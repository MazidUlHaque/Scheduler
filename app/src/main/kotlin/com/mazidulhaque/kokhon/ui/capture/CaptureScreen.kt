package com.mazidulhaque.kokhon.ui.capture

import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonIcons
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonPrimaryButton
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonSecondaryButton
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonTextButton
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.core.designsystem.theme.accentColor
import com.mazidulhaque.kokhon.core.model.EntrySource
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import com.mazidulhaque.kokhon.core.parser.MatchReason
import com.mazidulhaque.kokhon.core.parser.Span
import com.mazidulhaque.kokhon.core.parser.SpanKind
import com.mazidulhaque.kokhon.ui.common.ClashWarning
import com.mazidulhaque.kokhon.ui.common.DuplicateWarning
import com.mazidulhaque.kokhon.ui.common.ImportanceSelector
import com.mazidulhaque.kokhon.ui.common.ReminderChainEditor
import com.mazidulhaque.kokhon.ui.common.isCustomFor
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import com.mazidulhaque.kokhon.ui.common.labelRes
import com.mazidulhaque.kokhon.ui.common.reminderSentence
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Turns a pasted or shared message into a plan, or updates an existing plan from it.
 * The message stays on screen with the understood words highlighted, so the user keeps context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    initialText: String,
    initialImage: String = "",
    source: EntrySource.Kind,
    targetId: Long,
    onClose: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onGoHome: () -> Unit,
    onEditDraft: () -> Unit,
    onEditEntry: (Long) -> Unit,
) {
    val app = kokhonApp()
    val vm: CaptureViewModel =
        viewModel(key = "capture-$targetId-${initialText.hashCode()}-$initialImage") {
            CaptureViewModel(
                initialText = initialText,
                initialImage = initialImage.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse),
                sourceKind = source,
                targetId = targetId,
                parser = app.planParser,
                repository = app.entryRepository,
                coordinator = app.reminderCoordinator,
                clock = app.clock,
                draftHolder = app.captureDraft,
                reader = app.screenshotReader,
            )
        }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finishedWith) {
        state.finishedWith?.let { if (it == 0L) onGoHome() else onOpenEntry(it) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onClose) {
                Icon(KokhonIcons.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                stringResource(if (targetId != 0L) R.string.update_title else R.string.capture_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (state.mode) {
            CaptureMode.INPUT -> InputContent(state, vm)
            CaptureMode.READING -> ReadingContent(state)
            CaptureMode.CREATE -> CreateContent(state, vm, onEditDraft, onOpenEntry)
            CaptureMode.UPDATE -> UpdateContent(state, vm, onClose)
            CaptureMode.NOTHING_TO_CHANGE -> NothingToChange(state, vm, onEditEntry)
            CaptureMode.NOTHING_FOUND -> NothingFound(vm, onEditDraft)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.InputContent(
    state: CaptureState,
    vm: CaptureViewModel,
) {
    val context = LocalContext.current
    val images = rememberImageSources(vm::readImage)
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.imageUnreadable) {
            Text(stringResource(R.string.image_unreadable), style = MaterialTheme.typography.bodyMedium, color = KokhonColors.StopText)
        }
        OutlinedTextField(
            value = state.text,
            onValueChange = vm::setText,
            placeholder = { Text(stringResource(R.string.message_hint)) },
            minLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        KokhonSecondaryButton(
            text = stringResource(R.string.paste),
            onClick = {
                val clip = context.getSystemService<ClipboardManager>()?.primaryClip
                val pasted =
                    clip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                if (!pasted.isNullOrBlank()) vm.setText(pasted)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.or), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KokhonSecondaryButton(
                text = stringResource(R.string.pick_screenshot),
                onClick = images.pickScreenshot,
                modifier = Modifier.weight(1f),
            )
            KokhonSecondaryButton(text = stringResource(R.string.take_photo), onClick = images.takePhoto, modifier = Modifier.weight(1f))
        }
    }
    KokhonPrimaryButton(
        text = stringResource(R.string.find_the_plan),
        onClick = { vm.analyse() },
        enabled = state.text.isNotBlank(),
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReadingContent(state: CaptureState) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        state.thumbnail?.let { Thumbnail(it, maxHeight = 320) }
        androidx.compose.material3.CircularProgressIndicator()
        Text(stringResource(R.string.reading_screenshot), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.reading_screenshot_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Thumbnail(
    bitmap: android.graphics.Bitmap,
    maxHeight: Int,
) {
    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.screenshot_preview_cd),
        modifier =
            Modifier.heightIn(max = maxHeight.dp).clip(
                androidx.compose.foundation.shape
                    .RoundedCornerShape(12.dp),
            ),
    )
}

/** Where the text came from: the screenshot preview and, when known, when the message was sent. */
@Composable
private fun SourceInfo(state: CaptureState) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        state.thumbnail?.let { Thumbnail(it, maxHeight = 96) }
        state.writtenAt?.let { at ->
            Text(
                stringResource(R.string.written_at, TimeFormat.shortDate(at.toInstant()) + ", " + TimeFormat.time(context, at.toInstant())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The message with understood words marked like a highlighter pen; guesses are underlined in amber. */
@Composable
private fun HighlightedMessage(
    text: String,
    spans: List<Span>,
    onTap: (Span) -> Unit,
) {
    val guessColor = Color(0xFF8A6200)
    val annotated =
        buildAnnotatedString {
            append(text)
            spans.distinctBy { it.start to it.end }.forEach { span ->
                val style =
                    if (span.guessed) {
                        SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, color = guessColor)
                    } else {
                        SpanStyle(fontWeight = FontWeight.Bold, background = KokhonColors.Highlighter, color = KokhonColors.Ink)
                    }
                addStyle(style, span.start, span.end)
                addLink(LinkAnnotation.Clickable("span-${span.start}") { onTap(span) }, span.start, span.end)
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(annotated, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
        Text(
            stringResource(R.string.capture_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CreateContent(
    state: CaptureState,
    vm: CaptureViewModel,
    onEditDraft: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val context = LocalContext.current
    val draft = state.drafts[state.current]
    val others by vm.others.collectAsStateWithLifecycle()
    val duplicate = vm.duplicate(draft, others)
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingType by remember { mutableStateOf(false) }
    var editingReminders by remember { mutableStateOf(false) }
    val start = vm.startOf(draft)

    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (state.drafts.size == 1) {
                stringResource(R.string.found_one_plan)
            } else {
                pluralStringResource(R.plurals.found_plans, state.drafts.size, state.drafts.size)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.thumbnail != null || state.writtenAt != null) SourceInfo(state)
        HighlightedMessage(state.text, draft.parsed.spans) { span ->
            when (span.kind) {
                SpanKind.DATE -> pickingDate = true
                SpanKind.TIME -> pickingTime = true
                SpanKind.TYPE -> pickingType = true
                SpanKind.APPROXIMATE -> vm.setApproximate(!draft.approximate)
                else -> Unit
            }
        }

        if (state.drafts.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.drafts.forEachIndexed { index, d ->
                    val includeDescription = stringResource(R.string.include_plan_cd, d.title)
                    val label = d.title + " · " + TimeFormat.shortDate(vm.startOf(d)) + ", " + TimeFormat.time(context, vm.startOf(d))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = d.include,
                            onCheckedChange = { vm.setIncluded(index, it) },
                            modifier = Modifier.semantics { contentDescription = includeDescription },
                        )
                        FilterChip(selected = index == state.current, onClick = { vm.select(index) }, label = { Text(label, maxLines = 1) })
                    }
                }
            }
        }

        duplicate?.let { DuplicateWarning(it) { onOpenEntry(it.id) } }
        ClashWarning(vm.clashes(draft, others).filter { it.id != duplicate?.id })

        draft.amPmChoices?.let { choices ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF4D1), MaterialTheme.shapes.medium)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val labels =
                    choices.map {
                        TimeFormat.time(
                            context,
                            draft.date
                                .atTime(it)
                                .atZone(ZoneId.systemDefault())
                                .toInstant(),
                        )
                    }
                Text(
                    stringResource(R.string.am_or_pm, labels[0], labels[1]),
                    style = MaterialTheme.typography.titleSmall,
                    color = KokhonColors.Ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    choices.forEachIndexed { i, option ->
                        FilterChip(
                            selected = draft.time == option,
                            onClick = { vm.setTime(option) },
                            label = { Text(labels[i], style = MaterialTheme.typography.titleSmall) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }

        // The plan itself.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = vm::setTitle,
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(10.dp).background(draft.type.accentColor(), androidx.compose.foundation.shape.CircleShape),
                )
                Text(
                    stringResource(draft.type.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(role = Role.Button) { pickingType = true },
                )
                Text("·", style = MaterialTheme.typography.bodyLarge)
                Text(
                    TimeFormat.longDate(draft.date),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(role = Role.Button) { pickingDate = true },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Nudge(stringResource(R.string.minus_15), stringResource(R.string.earlier_15)) { vm.nudge(-15) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) { pickingTime = true },
                ) {
                    if (draft.approximate) {
                        Text(
                            stringResource(R.string.about),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF8A6200),
                        )
                    }
                    Text(TimeFormat.time(context, start), style = MaterialTheme.typography.displayMedium)
                }
                Nudge(stringResource(R.string.plus_15), stringResource(R.string.later_15)) { vm.nudge(15) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.approximate_switch),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = draft.approximate, onCheckedChange = vm::setApproximate)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.importance_line, stringResource(draft.importance.labelRes())),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(reminderSentence(draft.reminders), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { editingReminders = true }) {
                Text(
                    stringResource(R.string.change),
                    textDecoration = TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    val count = state.drafts.count { it.include }
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KokhonPrimaryButton(
            text = pluralStringResource(R.plurals.save_n_plans, count, count),
            onClick = vm::saveDrafts,
            enabled = count > 0,
        )
        if (state.drafts.size == 1) {
            KokhonTextButton(text = stringResource(R.string.edit_all_details), onClick = { if (vm.editAllDetails()) onEditDraft() })
        }
    }

    if (pickingDate) {
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    draft.date
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickingDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = picker) }
    }
    if (pickingTime) {
        val picker =
            rememberTimePickerState(
                draft.time.hour,
                draft.time.minute,
                android.text.format.DateFormat
                    .is24HourFormat(context),
            )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTime(LocalTime.of(picker.hour, picker.minute))
                    pickingTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = picker) },
        )
    }
    if (pickingType) {
        AlertDialog(
            onDismissRequest = { pickingType = false },
            confirmButton = { TextButton(onClick = { pickingType = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.type)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = {
                                vm.setType(type)
                                pickingType = false
                            },
                            label = { Text(stringResource(type.labelRes())) },
                        )
                    }
                }
            },
        )
    }
    if (editingReminders) {
        ModalBottomSheet(onDismissRequest = {
            editingReminders = false
        }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ImportanceSelector(
                    selected = draft.importance,
                    isCustom = draft.reminders.isCustomFor(draft.importance, draft.type),
                    onSelect = vm::setImportance,
                )
                ReminderChainEditor(chain = draft.reminders, entryStart = start, onChange = vm::setReminders)
                KokhonPrimaryButton(text = stringResource(R.string.done), onClick = { editingReminders = false })
            }
        }
    }
}

@Composable
private fun Nudge(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.width(64.dp).heightIn(min = 48.dp).semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(0.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.UpdateContent(
    state: CaptureState,
    vm: CaptureViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val best = state.candidates.first().candidate
    val entry = best.entry

    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (state.target !=
                null
            ) {
                stringResource(R.string.update_selected_from, entry.title)
            } else {
                stringResource(R.string.changes_existing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.thumbnail != null || state.writtenAt != null) SourceInfo(state)
        HighlightedMessage(state.text, state.spans) {}

        val newStart = best.newStart
        val question =
            if (best.cancel) {
                stringResource(R.string.question_cancel, entry.title)
            } else {
                stringResource(R.string.question_move, entry.title, describeChange(context, entry.start, newStart!!))
            }
        Text(question, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

        if (!best.cancel && newStart != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.was),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        TimeFormat.time(context, entry.start),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        TimeFormat.shortDate(entry.start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(KokhonIcons.ChevronRight, contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.will_be),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(TimeFormat.time(context, newStart), style = MaterialTheme.typography.headlineSmall)
                    Text(TimeFormat.shortDate(newStart), style = MaterialTheme.typography.bodySmall)
                }
            }
            val moved = entry.rescheduledTo(newStart)
            val times =
                moved.reminders.orderedSteps
                    .filter { it.enabled && it.style != ReminderStyle.SILENT }
                    .map { TimeFormat.time(context, it.fireTime(newStart)) }
            if (moved.reminders.enabled && times.isNotEmpty()) {
                Text(stringResource(R.string.reminders_move_too, times.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.candidates.size > 1) {
            Text(
                stringResource(R.string.also_could_be),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.candidates.drop(1).forEachIndexed { offset, ui ->
                val c = ui.candidate
                val change =
                    when {
                        c.cancel -> stringResource(R.string.status_cancelled)
                        c.newStart != null -> describeChange(context, c.entry.start, c.newStart!!)
                        else -> ""
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = ui.selected, role = Role.Checkbox, onValueChange = { vm.toggleCandidate(offset + 1) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = ui.selected, onCheckedChange = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(c.entry.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            TimeFormat.shortDate(c.entry.start) + ", " + TimeFormat.time(context, c.entry.start) + " → " + change,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            reasons(c.reasons),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val selected = state.candidates.count { it.selected }
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KokhonPrimaryButton(
            text =
                when {
                    selected > 1 -> pluralStringResource(R.plurals.update_n_plans, selected, selected)
                    best.cancel -> stringResource(R.string.yes_cancel_it)
                    else -> stringResource(R.string.yes_move_it)
                },
            onClick = vm::applyUpdates,
            enabled = selected > 0,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KokhonSecondaryButton(
                text = stringResource(R.string.save_as_new_plan),
                onClick = vm::treatAsNewPlan,
                modifier = Modifier.weight(1f),
            )
            KokhonSecondaryButton(text = stringResource(R.string.cancel), onClick = onClose, modifier = Modifier.weight(1f))
        }
    }
}

/** "10:00 PM" when only the time changes, "Mon, 9 Nov, 9:30 PM" when the day changes too. */
private fun describeChange(
    context: android.content.Context,
    old: Instant,
    new: Instant,
): String {
    val zone = ZoneId.systemDefault()
    val sameDay = old.atZone(zone).toLocalDate() == new.atZone(zone).toLocalDate()
    return if (sameDay) TimeFormat.time(context, new) else TimeFormat.shortDate(new) + ", " + TimeFormat.time(context, new)
}

@Composable
private fun reasons(list: List<MatchReason>): String =
    list
        .map {
            stringResource(
                when (it) {
                    MatchReason.SAME_TYPE -> R.string.reason_same_type
                    MatchReason.SAME_DAY -> R.string.reason_same_day
                    MatchReason.SAME_WORDS -> R.string.reason_same_words
                    MatchReason.SAME_PEOPLE -> R.string.reason_same_people
                    MatchReason.COMING_UP -> R.string.reason_coming_up
                },
            )
        }.joinToString(", ")
        .replaceFirstChar { it.uppercase() }

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NothingToChange(
    state: CaptureState,
    vm: CaptureViewModel,
    onEditEntry: (Long) -> Unit,
) {
    val entry =
        state.candidates
            .first()
            .candidate.entry
    Column(modifier = Modifier.weight(1f).padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HighlightedMessage(state.text, state.spans) {}
        Text(stringResource(R.string.nothing_to_change, entry.title), style = MaterialTheme.typography.titleMedium)
    }
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KokhonPrimaryButton(text = stringResource(R.string.edit_this_plan), onClick = { onEditEntry(entry.id) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KokhonSecondaryButton(text = stringResource(R.string.edit_message), onClick = vm::editMessage, modifier = Modifier.weight(1f))
            if (state.target == null) {
                KokhonSecondaryButton(
                    text = stringResource(R.string.save_as_new_plan),
                    onClick = vm::treatAsNewPlan,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NothingFound(
    vm: CaptureViewModel,
    onEditDraft: () -> Unit,
) {
    Column(
        modifier = Modifier.weight(1f).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.nothing_found), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.nothing_found_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KokhonPrimaryButton(text = stringResource(R.string.make_plan_anyway), onClick = {
            vm.startBlankDraft()
            onEditDraft()
        })
        KokhonSecondaryButton(text = stringResource(R.string.edit_message), onClick = vm::editMessage, modifier = Modifier.fillMaxWidth())
    }
}
