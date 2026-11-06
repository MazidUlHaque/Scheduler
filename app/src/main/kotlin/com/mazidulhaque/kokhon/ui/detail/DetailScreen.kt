package com.mazidulhaque.kokhon.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.component.DoneBadge
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonIcons
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.core.designsystem.theme.accentColor
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.ui.common.KokhonTopBar
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import com.mazidulhaque.kokhon.ui.common.labelRes
import com.mazidulhaque.kokhon.ui.common.reminderSentence
import java.time.ZoneId

@Composable
fun DetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onEditReminders: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val app = kokhonApp()
    val context = LocalContext.current
    val vm: DetailViewModel =
        viewModel(key = "detail-$id") {
            DetailViewModel(id, app.entryRepository, app.reminderCoordinator, app.clock, app.pendingUndo)
        }
    val entry by vm.entry.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    val current = entry ?: return
    val status = current.statusAt(vm.clock.instant())
    val statusText =
        stringResource(
            when (status) {
                EntryStatus.UPCOMING -> R.string.status_upcoming
                EntryStatus.ONGOING -> R.string.status_ongoing
                EntryStatus.COMPLETED -> R.string.status_completed
                EntryStatus.CANCELLED -> R.string.status_cancelled
                EntryStatus.MISSED -> R.string.status_missed
            },
        )
    val finished = status == EntryStatus.COMPLETED || status == EntryStatus.CANCELLED

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        KokhonTopBar(title = "", onBack = onBack) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == EntryStatus.COMPLETED) DoneBadge(size = 20.dp, contentDescription = statusText)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Direct manipulation: tap the name itself to rename.
                Text(
                    current.title,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier =
                        Modifier
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.rename)) {
                                renaming =
                                    true
                            }.semantics { heading() },
                )
                Text(
                    stringResource(R.string.tap_to_rename),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val dateText = TimeFormat.longDate(current.start.atZone(ZoneId.systemDefault()).toLocalDate())
                val time = TimeFormat.time(context, current.start)
                Text(
                    stringResource(
                        if (current.isApproximate) R.string.detail_sentence_approx else R.string.detail_sentence,
                        dateText,
                        time,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(
                                10.dp,
                            ).background(current.type.accentColor(), androidx.compose.foundation.shape.CircleShape),
                    )
                    Text(
                        stringResource(
                            R.string.detail_type_importance,
                            stringResource(current.type.labelRes()),
                            stringResource(current.importance.labelRes()).lowercase(),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                current.location?.let {
                    Text(
                        stringResource(R.string.where, it),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (current.reminders.enabled) R.string.reminders_on else R.string.reminders_off_title,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        reminderSentence(current.reminders),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = current.reminders.enabled, onCheckedChange = vm::setRemindersEnabled)
            }

            Column {
                ActionRow(stringResource(R.string.reschedule), onClick = onEdit)
                ActionRow(stringResource(R.string.change_reminders), onClick = onEditReminders)
                val copyTemplate = stringResource(R.string.copy_suffix, "%s")
                ActionRow(
                    stringResource(R.string.duplicate),
                    onClick = { vm.duplicate({ copyTemplate.format(it) }, onOpenEntry) },
                )
                if (status == EntryStatus.CANCELLED) {
                    ActionRow(stringResource(R.string.restore_plan), onClick = { vm.setStatus(null) })
                } else {
                    ActionRow(stringResource(R.string.cancel_plan), onClick = { vm.setStatus(EntryStatus.CANCELLED) })
                }
                ActionRow(
                    stringResource(R.string.delete),
                    trailing = stringResource(R.string.you_can_undo),
                    color = KokhonColors.StopText,
                    divider = false,
                    onClick = { vm.delete(onBack) },
                )
            }

            current.source.originalText?.let { text ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.original_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("“$text”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            }
            current.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Button(
            onClick = {
                vm.setStatus(
                    if (finished &&
                        status == EntryStatus.COMPLETED
                    ) {
                        null
                    } else {
                        EntryStatus.COMPLETED
                    },
                )
            },
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp).fillMaxWidth().heightIn(min = 60.dp),
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KokhonColors.DoneSoft,
                    contentColor = KokhonColors.DoneText,
                ),
        ) {
            Icon(KokhonIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(if (status == EntryStatus.COMPLETED) R.string.mark_not_done else R.string.mark_as_done),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(current.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    vm.rename(text)
                    renaming = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A labelled, full-width action. Words instead of icon puzzles. */
@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
    color: Color = Color.Unspecified,
    divider: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(KokhonIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
