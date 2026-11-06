package com.mazidulhaque.kokhon.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import com.mazidulhaque.kokhon.ui.common.labelRes
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onOpenEntry: (Long) -> Unit,
    onAddPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onCapture: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    val clipboard = rememberClipboardOffer()
    val app = kokhonApp()
    val vm: HomeViewModel =
        viewModel {
            HomeViewModel(app.entryRepository, app.reminderCoordinator, app.settingsRepository, app.clock, app.pendingUndo)
        }
    val state by vm.state.collectAsStateWithLifecycle()
    val undo by vm.pendingUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val deletedMessage = undo?.let { stringResource(R.string.deleted_entry, it.title) }
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(undo) {
        val entry = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedMessage.orEmpty(), actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete(entry) else vm.dismissUndo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0),
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
        ) {
            val current = state
            if (current == null) {
                Box(Modifier.weight(1f))
            } else {
                Header(current)
                current.discreetUntil?.let { until ->
                    DiscreetBanner(until = until, onTurnOff = { vm.toggleDiscreet(currentlyOn = true) })
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(current.rows, key = { row -> rowKey(row) }) { row ->
                        when (row) {
                            is TimelineRow.NowLine -> {
                                NowLine(row)
                            }

                            is TimelineRow.DayHeader -> {
                                DayHeaderRow(row.date, current.today)
                            }

                            is TimelineRow.Item -> {
                                TimelineItem(
                                    row,
                                    onOpen = { onOpenEntry(row.entry.id) },
                                    onToggleDone = { vm.setDone(row.entry, it) },
                                )
                            }
                        }
                    }
                    if (current.isEmpty) {
                        item { EmptyState() }
                    }
                }
                if (clipboard.available) {
                    ClipboardBanner(
                        onUse = { onCapture(clipboard.read(context)) },
                        onDismiss = clipboard::dismiss,
                    )
                }
            }
            BottomBar(
                discreetOn = state?.discreetUntil != null,
                onToggleDiscreet = { vm.toggleDiscreet(currentlyOn = state?.discreetUntil != null) },
                onAddPlan = { choosing = true },
                onOpenSettings = onOpenSettings,
            )
        }
    }

    if (choosing) {
        AddPlanSheet(
            onDismiss = { choosing = false },
            onPaste = {
                choosing = false
                onCapture(clipboard.read(context))
            },
            onType = {
                choosing = false
                onAddPlan()
            },
        )
    }
}

private fun rowKey(row: TimelineRow): String =
    when (row) {
        is TimelineRow.NowLine -> "now"
        is TimelineRow.DayHeader -> "day-${row.date}"
        is TimelineRow.Item -> "entry-${row.entry.id}"
    }

@Composable
private fun Header(state: HomeState) {
    val context = LocalContext.current
    val hour = state.now.atZone(ZoneId.systemDefault()).hour
    val greeting =
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    val status =
        when {
            state.isEmpty -> {
                stringResource(R.string.status_no_plans)
            }

            state.ongoing != null && state.next == null -> {
                stringResource(R.string.status_happening_now, state.ongoing.title)
            }

            state.next == null -> {
                stringResource(R.string.status_all_done)
            }

            state.leftToday > 0 -> {
                pluralStringResource(
                    R.plurals.status_left_today,
                    state.leftToday,
                    state.leftToday,
                    state.next.title,
                    TimeFormat.duration(Duration.between(state.now, state.next.start)),
                )
            }

            else -> {
                val nextDate =
                    state.next.start
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                val whenText =
                    if (nextDate == state.today.plusDays(1)) {
                        "tomorrow at " + TimeFormat.time(context, state.next.start)
                    } else {
                        TimeFormat.shortDate(state.next.start) + " at " + TimeFormat.time(context, state.next.start)
                    }
                stringResource(R.string.status_nothing_left_today, state.next.title, whenText)
            }
        }
    Column(
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            TimeFormat.longDate(state.today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(greeting), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(status, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DiscreetBanner(
    until: java.time.Instant,
    onTurnOff: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier =
            Modifier
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KokhonIcons.Moon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.discreet_on_until, TimeFormat.time(context, until)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        TextButton(onClick = onTurnOff) { Text(stringResource(R.string.turn_off), color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun NowLine(row: TimelineRow.NowLine) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(TimeFormat.time(context, row.now), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
        Text(
            stringResource(R.string.now),
            style = MaterialTheme.typography.labelMedium,
            color = KokhonColors.Ink,
            modifier =
                Modifier
                    .background(KokhonColors.Highlighter, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(KokhonColors.NowLine, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun DayHeaderRow(
    date: LocalDate,
    today: LocalDate,
) {
    val locale = LocalLocale.current.platformLocale
    val weekday = DateTimeFormatter.ofPattern("EEEE", locale).format(date)
    val text = if (date == today.plusDays(1)) stringResource(R.string.tomorrow_day, weekday) else TimeFormat.longDate(date)
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp).padding(start = 86.dp).semantics { heading() },
    )
}

@Composable
private fun TimelineItem(
    row: TimelineRow.Item,
    onOpen: () -> Unit,
    onToggleDone: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val entry = row.entry
    val time = TimeFormat.time(context, entry.start)
    val timeText = if (entry.isApproximate) stringResource(R.string.approx_prefix, time) else time
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .alpha(if (row.finished) 0.62f else 1f)
                .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            timeText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (row.finished) FontWeight.Normal else FontWeight.Bold),
            color = if (row.finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(76.dp),
        )
        if (row.finished && row.status == EntryStatus.COMPLETED) {
            DoneBadge(contentDescription = stringResource(R.string.status_completed))
        } else {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(entry.type.accentColor(), RoundedCornerShape(2.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (row.finished) FontWeight.Normal else FontWeight.Bold),
                textDecoration = if (row.status == EntryStatus.CANCELLED) TextDecoration.LineThrough else null,
            )
            Text(subtitle(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.type == EntryType.TASK) {
            val done = row.status == EntryStatus.COMPLETED
            val description =
                stringResource(if (done) R.string.mark_not_done_cd else R.string.mark_done_cd, entry.title)
            IconButton(onClick = { onToggleDone(!done) }) {
                Surface(
                    shape = CircleShape,
                    color = if (done) KokhonColors.Done else MaterialTheme.colorScheme.surface,
                    border = if (done) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(28.dp),
                ) {
                    if (done) {
                        Icon(
                            KokhonIcons.Check,
                            contentDescription = description,
                            tint = KokhonColors.Paper,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun subtitle(row: TimelineRow.Item): String {
    val entry = row.entry
    val parts = mutableListOf(stringResource(entry.type.labelRes()))
    when (row.status) {
        EntryStatus.COMPLETED -> {
            parts += stringResource(R.string.status_completed)
        }

        EntryStatus.CANCELLED -> {
            parts += stringResource(R.string.status_cancelled)
        }

        EntryStatus.MISSED -> {
            parts += stringResource(R.string.status_missed)
        }

        EntryStatus.ONGOING -> {
            parts += stringResource(R.string.status_ongoing)
        }

        EntryStatus.UPCOMING -> {
            if (entry.importance != Importance.NORMAL) parts += stringResource(entry.importance.labelRes())
            if (!entry.reminders.enabled) parts += stringResource(R.string.reminders_off)
            if (entry.isApproximate) parts += stringResource(R.string.time_is_approximate)
        }
    }
    entry.location?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}

@Composable
private fun EmptyState() {
    Column(
        modifier =
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.app_name) + " - " + stringResource(R.string.tagline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** All actions live in the bottom thumb zone. */
@Composable
private fun BottomBar(
    discreetOn: Boolean,
    onToggleDiscreet: () -> Unit,
    onAddPlan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarButton(KokhonIcons.Moon, stringResource(R.string.discreet), selected = discreetOn, onClick = onToggleDiscreet)
        Button(
            onClick = onAddPlan,
            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(KokhonIcons.Plus, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.add_a_plan),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        BottomBarButton(KokhonIcons.Settings, stringResource(R.string.settings), selected = false, onClick = onOpenSettings)
    }
}

@Composable
private fun BottomBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(72.dp)
                .heightIn(min = 56.dp)
                .background(
                    if (selected) KokhonColors.Highlighter else androidx.compose.ui.graphics.Color.Transparent,
                    MaterialTheme.shapes.small,
                ).clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button)
                .semantics { this.selected = selected }
                .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) KokhonColors.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) KokhonColors.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
