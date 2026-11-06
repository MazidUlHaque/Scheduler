package com.mazidulhaque.kokhon.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonIcons
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.core.model.ReminderChain
import com.mazidulhaque.kokhon.core.model.ReminderPreset
import com.mazidulhaque.kokhon.core.model.ReminderStep
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import com.mazidulhaque.kokhon.core.model.ScheduledReminder
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Edits a reminder chain. A ruler at the top maps "time before the plan" to distance, so the
 * user sees at a glance how the reminders are spread; the list below makes each one editable.
 */
@Composable
fun ReminderChainEditor(
    chain: ReminderChain,
    entryStart: Instant,
    onChange: (ReminderChain) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val steps = chain.orderedSteps
    var adding by remember { mutableStateOf(false) }
    var savingPreset by remember { mutableStateOf(false) }
    val app = kokhonApp()
    val scope = rememberCoroutineScope()
    val presetsFlow = remember(app) { app.settingsRepository.settings.map { it.presets } }
    val presets by presetsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.before_start, TimeFormat.time(context, entryStart)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReminderRuler(steps = steps)

        if (presets.isNotEmpty()) {
            Text(stringResource(R.string.my_presets), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = preset.chain.orderedSteps == steps,
                        onClick = { onChange(preset.chain.copy(enabled = chain.enabled)) },
                        label = { Text(preset.name) },
                    )
                }
            }
        }

        steps.forEachIndexed { index, step ->
            val label =
                if (step.offset.isZero) {
                    stringResource(
                        R.string.at_start,
                    )
                } else {
                    stringResource(R.string.offset_before, offsetLabel(step.offset))
                }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    step.purpose?.let {
                        Text(
                            stringResource(it.labelRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { onChange(chain.replace(steps, index, step.copy(style = step.style.next()))) },
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(0.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Text(
                            stringResource(step.style.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (step.style ==
                                    ReminderStyle.ALARM
                                ) {
                                    KokhonColors.StopText
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
                Switch(
                    checked = step.enabled,
                    onCheckedChange = { onChange(chain.replace(steps, index, step.copy(enabled = it))) },
                )
                IconButton(onClick = { onChange(chain.copy(steps = steps.filterIndexed { i, _ -> i != index })) }) {
                    Icon(KokhonIcons.Close, contentDescription = stringResource(R.string.remove_reminder_cd, label))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Text(
            stringResource(R.string.style_change_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { adding = true },
            enabled = steps.size < ScheduledReminder.MAX_STEPS,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(stringResource(R.string.add_reminder), color = MaterialTheme.colorScheme.onSurface)
        }
        if (steps.isNotEmpty()) {
            TextButton(onClick = { savingPreset = true }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(
                    stringResource(R.string.save_as_preset),
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (savingPreset) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingPreset = false },
            title = { Text(stringResource(R.string.preset_name)) },
            text = {
                OutlinedTextField(value = name, onValueChange = {
                    name = it
                }, singleLine = true, placeholder = { Text(stringResource(R.string.preset_name_hint)) })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val preset = ReminderPreset(name.trim(), chain.copy(enabled = true))
                    scope.launch { app.settingsRepository.savePreset(preset) }
                    savingPreset = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { savingPreset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (adding) {
        AddReminderDialog(
            onDismiss = { adding = false },
            onAdd = { step ->
                adding = false
                onChange(chain.withStep(step))
            },
        )
    }
}

private fun ReminderChain.replace(
    ordered: List<ReminderStep>,
    index: Int,
    step: ReminderStep,
): ReminderChain = copy(steps = ordered.toMutableList().also { it[index] = step })

/** A horizontal ruler: the plan's start is on the right, each reminder sits at its distance before it. */
@Composable
private fun ReminderRuler(steps: List<ReminderStep>) {
    val maxMinutes = (steps.maxOfOrNull { it.offset.toMinutes() } ?: 60L).coerceAtLeast(60L) * 1.1f
    val line = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clearAndSetSemantics { },
    ) {
        val width = maxWidth
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val y = size.height / 2
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                ink,
                Offset(size.width - 2.dp.toPx(), y - 12.dp.toPx()),
                Offset(
                    size.width - 2.dp.toPx(),
                    y + 12.dp.toPx(),
                ),
                strokeWidth = 3.dp.toPx(),
            )
        }
        steps.filter { it.enabled }.forEach { step ->
            val fraction = 1f - step.offset.toMinutes() / maxMinutes
            val isAlarm = step.style == ReminderStyle.ALARM
            Surface(
                shape = CircleShape,
                color = if (isAlarm) KokhonColors.Stop else MaterialTheme.colorScheme.surface,
                border = if (isAlarm) null else BorderStroke(3.dp, ink),
                modifier =
                    Modifier
                        .size(22.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = (width - 22.dp) * fraction.coerceIn(0f, 1f)),
            ) {}
        }
    }
}

@Composable
private fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAdd: (ReminderStep) -> Unit,
) {
    val options = listOf(0L, 5L, 10L, 15L, 30L, 60L, 120L, 24 * 60L)
    var minutes by remember { mutableLongStateOf(10L) }
    var style by remember { mutableStateOf(ReminderStyle.NOTIFICATION) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onAdd(ReminderStep(Duration.ofMinutes(minutes), style)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.choose_when)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = { minutes = option },
                            label = {
                                Text(
                                    if (option ==
                                        0L
                                    ) {
                                        stringResource(R.string.at_start)
                                    } else {
                                        offsetLabel(Duration.ofMinutes(option))
                                    },
                                )
                            },
                        )
                    }
                }
                Text(stringResource(R.string.choose_how), style = MaterialTheme.typography.titleSmall)
                ReminderStyle.entries.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .selectable(selected = style == option, role = Role.RadioButton, onClick = {
                                    style =
                                        option
                                }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = style == option, onClick = null)
                        Box(Modifier.width(8.dp))
                        Text(stringResource(option.labelRes()))
                    }
                }
            }
        },
    )
}
