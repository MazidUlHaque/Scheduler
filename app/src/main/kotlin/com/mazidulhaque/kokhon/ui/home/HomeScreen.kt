package com.mazidulhaque.kokhon.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonTheme
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The home timeline. In this first version it shows the day header, the "Now" line and
 * the empty state; entries are added in Phase 1.
 */
@Composable
fun HomeScreen(
    clock: Clock,
    modifier: Modifier = Modifier,
    onAddPlan: () -> Unit = {},
) {
    val now = LocalDateTime.now(clock)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            DayHeader(now)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                NowLine(now)
                EmptyState()
            }
            BottomBar(onAddPlan = onAddPlan)
        }
    }
}

@Composable
private fun DayHeader(now: LocalDateTime) {
    val greeting =
        when (now.hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    Column(
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(greeting),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.status_no_plans),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The yellow "Now" marker. Past plans appear above it, upcoming plans below. */
@Composable
private fun NowLine(now: LocalDateTime) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = stringResource(R.string.now),
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
private fun EmptyState() {
    Column(
        modifier =
            Modifier
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
private fun BottomBar(onAddPlan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = {}, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.calendar), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            Text(
                "+  " + stringResource(R.string.add_a_plan),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(onClick = {}, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.settings), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    KokhonTheme { HomeScreen(clock = Clock.systemDefaultZone()) }
}
