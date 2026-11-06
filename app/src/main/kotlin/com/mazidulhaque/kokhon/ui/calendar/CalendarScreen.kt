package com.mazidulhaque.kokhon.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonIcons
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.core.designsystem.theme.accentColor
import com.mazidulhaque.kokhon.ui.common.EntryRow
import com.mazidulhaque.kokhon.ui.common.KokhonTopBar
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** A month at a glance: dots show which days have plans; the chosen day's plans are listed below. */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val app = kokhonApp()
    val zone = app.clock.zone
    val today = LocalDate.now(app.clock)
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var selected by rememberSaveable { mutableStateOf(today) }
    // Everything from a year back; the calendar filters by month.
    val since = remember { today.minusYears(1).atStartOfDay(zone).toInstant() }
    val entriesFlow = remember { app.entryRepository.observeFrom(since) }
    val entries by entriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val byDay = remember(entries) { entries.groupBy { it.start.atZone(zone).toLocalDate() } }
    val locale = LocalLocale.current.platformLocale
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val now = app.clock.instant()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        KokhonTopBar(title = stringResource(R.string.calendar), onBack = onBack)
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(KokhonIcons.Back, contentDescription = stringResource(R.string.previous_month))
            }
            Text(
                DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(KokhonIcons.ChevronRight, contentDescription = stringResource(R.string.next_month))
            }
        }
        // Weekday names
        val days = (0 until 7).map { firstDay.plus(it.toLong()) }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            days.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        MonthGrid(
            month,
            firstDay,
            today,
            selected,
            byDay.mapValues { (_, list) ->
                list.filter {
                    it.manualStatus !=
                        com.mazidulhaque.kokhon.core.model.EntryStatus.CANCELLED
                }
            },
        ) {
            selected = it
            if (YearMonth.from(it) != month) month = YearMonth.from(it)
        }
        val dayEntries = byDay[selected].orEmpty().sortedBy { it.start }
        Text(
            TimeFormat.longDate(selected),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
        if (dayEntries.isEmpty()) {
            Text(
                stringResource(R.string.no_plans_this_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 22.dp)) {
            items(dayEntries, key = { it.id }) { entry ->
                EntryRow(entry, now, showDate = false) { onOpenEntry(entry.id) }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    firstDay: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<com.mazidulhaque.kokhon.core.model.Entry>>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - firstDay.value) + 7) % 7
    val start = first.minusDays(lead.toLong())
    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        for (week in 0 until 6) {
            Row {
                for (d in 0 until 7) {
                    val date = start.plusDays((week * 7 + d).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selected,
                        plans = byDay[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    plans: List<com.mazidulhaque.kokhon.core.model.Entry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = plans.size
    val description =
        TimeFormat.longDate(date) + if (count > 0) ", " + pluralStringResource(R.plurals.n_plans, count, count) else ""
    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .heightIn(min = 44.dp)
                .padding(2.dp)
                .background(if (isSelected) KokhonColors.Ink else Color.Transparent, CircleShape)
                .then(if (isToday && !isSelected) Modifier.border(2.dp, KokhonColors.NowLine, CircleShape) else Modifier)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    this.selected = isSelected
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
            color =
                when {
                    isSelected -> KokhonColors.Paper
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            plans.take(3).forEach { plan ->
                Box(Modifier.size(5.dp).background(if (isSelected) KokhonColors.Paper else plan.type.accentColor(), CircleShape))
            }
        }
    }
}
