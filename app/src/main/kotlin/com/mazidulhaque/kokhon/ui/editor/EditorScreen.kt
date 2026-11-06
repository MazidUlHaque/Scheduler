package com.mazidulhaque.kokhon.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonPrimaryButton
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.ui.common.ClashWarning
import com.mazidulhaque.kokhon.ui.common.ImportanceSelector
import com.mazidulhaque.kokhon.ui.common.KokhonTopBar
import com.mazidulhaque.kokhon.ui.common.ReminderChainEditor
import com.mazidulhaque.kokhon.ui.common.isCustomFor
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import com.mazidulhaque.kokhon.ui.common.labelRes
import com.mazidulhaque.kokhon.ui.common.reminderSentence
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    id: Long,
    fromCapture: Boolean = false,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val app = kokhonApp()
    val context = LocalContext.current
    val vm: EditorViewModel =
        viewModel(key = "editor-$id-$fromCapture") {
            EditorViewModel(id, if (fromCapture) app.captureDraft.value else null, app.entryRepository, app.reminderCoordinator, app.clock)
        }
    val state by vm.state.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var editingReminders by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }

    // Ask for notification permission at the moment it matters: the first time a plan is saved.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.save() }
    val onSave = {
        val needsPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission &&
            state.title.isNotBlank()
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.save()
        }
    }

    if (!state.loaded) return
    val start = vm.startInstant(state)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        KokhonTopBar(
            title = stringResource(if (state.isNew) R.string.new_plan else R.string.edit_plan),
            onBack = onBack,
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::setTitle,
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(R.string.name_hint)) },
                isError = state.showTitleError,
                supportingText =
                    if (state.showTitleError) {
                        (
                            {
                                Text(
                                    stringResource(R.string.title_required),
                                )
                            }
                        )
                    } else {
                        null
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.type), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { vm.setType(type) },
                            label = { Text(stringResource(type.labelRes())) },
                        )
                    }
                }
            }

            // When: date as a tappable sentence, time as a big number with -15 / +15 nudges.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    TimeFormat.longDate(state.date),
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = TextDecoration.Underline,
                    modifier =
                        Modifier.clickable { pickingDate = true }.semantics {
                            contentDescription =
                                "Date: " + TimeFormat.longDate(state.date)
                        },
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    NudgeButton(
                        stringResource(R.string.minus_15),
                        stringResource(R.string.earlier_15),
                    ) { vm.nudge(-15) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { pickingTime = true },
                    ) {
                        if (state.approximate) {
                            Text(
                                stringResource(R.string.about),
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    androidx.compose.ui.graphics
                                        .Color(0xFF8A6200),
                            )
                        }
                        Text(TimeFormat.time(context, start), style = MaterialTheme.typography.displayMedium)
                    }
                    NudgeButton(stringResource(R.string.plus_15), stringResource(R.string.later_15)) { vm.nudge(15) }
                }
                ClashWarning(vm.clashes(state, others))
                if (vm.isInPast()) {
                    Text(
                        stringResource(R.string.in_the_past_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = KokhonColors.StopText,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.approximate_switch), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.approximate_switch_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.approximate, onCheckedChange = vm::setApproximate)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.duration), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, 30L, 60L, 90L, 120L, 180L).forEach { minutes ->
                        FilterChip(
                            selected = state.durationMinutes == minutes,
                            onClick = { vm.setDuration(minutes) },
                            label = {
                                Text(
                                    when (minutes) {
                                        null -> {
                                            stringResource(R.string.duration_none)
                                        }

                                        else -> {
                                            com.mazidulhaque.kokhon.ui.common.offsetLabel(
                                                java.time.Duration.ofMinutes(minutes),
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.location,
                onValueChange = vm::setLocation,
                label = { Text(stringResource(R.string.location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ImportanceSelector(
                selected = state.importance,
                isCustom = state.reminders.isCustomFor(state.importance, state.type),
                onSelect = vm::setImportance,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reminderSentence(state.reminders),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editingReminders = true }) {
                    Text(
                        stringResource(R.string.customise_reminders),
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::setNotes,
                label = { Text(stringResource(R.string.notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KokhonPrimaryButton(
            text = stringResource(if (state.isNew) R.string.save_plan else R.string.save_changes),
            onClick = onSave,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }

    if (pickingDate) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.date
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.setDate(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }

    if (pickingTime) {
        val pickerState =
            rememberTimePickerState(
                initialHour = state.time.hour,
                initialMinute = state.time.minute,
                is24Hour =
                    android.text.format.DateFormat
                        .is24HourFormat(context),
            )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTime(LocalTime.of(pickerState.hour, pickerState.minute))
                    pickingTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = pickerState) },
        )
    }

    if (editingReminders) {
        ModalBottomSheet(onDismissRequest = {
            editingReminders = false
        }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(
                            rememberScrollState(),
                        ).padding(horizontal = 22.dp)
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleLarge)
                ReminderChainEditor(chain = state.reminders, entryStart = start, onChange = vm::setReminders)
                KokhonPrimaryButton(text = stringResource(R.string.done), onClick = { editingReminders = false })
            }
        }
    }
}

@Composable
private fun NudgeButton(
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
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
