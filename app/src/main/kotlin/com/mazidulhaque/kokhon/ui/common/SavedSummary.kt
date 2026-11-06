package com.mazidulhaque.kokhon.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.component.DoneBadge
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonIcons
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonPrimaryButton
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonSecondaryButton
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.scheduledReminders
import java.time.Instant
import java.time.ZoneId

/**
 * Confirms a save in plain words: what was saved, when it is, and when the reminders will come.
 * OK goes back to the timeline; the two smaller buttons open the editor or the reminders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSummarySheet(
    entries: List<Entry>,
    updated: Boolean,
    now: Instant,
    onOk: () -> Unit,
    onChangeDetails: (Entry) -> Unit,
    onChangeReminders: (Entry) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onOk, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoneBadge(size = 28.dp, contentDescription = "")
                Text(
                    stringResource(if (updated) R.string.saved_updated else R.string.saved_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            entries.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleLarge)
                    val date = TimeFormat.longDate(entry.start.atZone(ZoneId.systemDefault()).toLocalDate())
                    val time = TimeFormat.time(context, entry.start)
                    Text(
                        stringResource(if (entry.isApproximate) R.string.saved_when_approx else R.string.saved_when, date, time),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(KokhonIcons.Bell, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(reminderLine(entry, now), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            KokhonPrimaryButton(text = stringResource(R.string.ok), onClick = onOk)
            entries.singleOrNull()?.let { only ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KokhonSecondaryButton(
                        stringResource(R.string.change_details),
                        onClick = { onChangeDetails(only) },
                        modifier = Modifier.weight(1f),
                    )
                    KokhonSecondaryButton(stringResource(R.string.change_reminders_short), onClick = {
                        onChangeReminders(only)
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun reminderLine(
    entry: Entry,
    now: Instant,
): String {
    val context = LocalContext.current
    if (entry.manualStatus == EntryStatus.CANCELLED) return stringResource(R.string.status_cancelled)
    if (!entry.reminders.enabled) return stringResource(R.string.reminder_sentence_off)
    val times = entry.scheduledReminders(now).map { TimeFormat.time(context, it.fireAt) }
    return when {
        times.isEmpty() && entry.start.isBefore(now) -> {
            stringResource(R.string.saved_time_passed)
        }

        times.isEmpty() -> {
            stringResource(R.string.reminder_sentence_none)
        }

        times.size == 1 -> {
            stringResource(R.string.saved_remind_at, times.single())
        }

        else -> {
            stringResource(
                R.string.saved_remind_at,
                stringResource(R.string.list_and, times.dropLast(1).joinToString(", "), times.last()),
            )
        }
    }
}
