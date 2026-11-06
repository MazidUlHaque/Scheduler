package com.mazidulhaque.kokhon.core.alarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazidulhaque.kokhon.core.alarm.R
import com.mazidulhaque.kokhon.core.alarm.RingingAlarm
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.core.model.Importance
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * Designed so the two main buttons can't be confused: Stop is the largest, red, at the bottom
 * where the thumb rests; Snooze is smaller, outlined, higher up. Each says what happens next.
 */
@Composable
fun AlarmScreen(
    alarm: RingingAlarm,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onAware: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Instant.now()
        }
    }
    val untilStart = Duration.between(now, alarm.entryStart)
    val startTime = TimeFormat.time(context, alarm.entryStart)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(KokhonColors.NightBackground)
                .safeDrawingPadding()
                .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.alarm_reminder_of, alarm.stepNumber, alarm.stepCount),
                color = KokhonColors.NightInkSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(importanceLabel(alarm.importance)),
                color = KokhonColors.NowLine,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Column(modifier = Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (untilStart.toMinutes() >= 1) {
                    stringResource(R.string.starts_in, TimeFormat.duration(context, untilStart))
                } else {
                    stringResource(R.string.starts_now)
                },
                color = KokhonColors.NightInkSecondary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            )
            Text(
                alarm.title,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (alarm.isApproximate) R.string.about_time else R.string.at_time, startTime),
                color = Color.White,
                fontSize = 22.sp,
            )
        }

        Text(
            text = stringResource(if (alarm.silenced) R.string.alarm_silenced else R.string.alarm_silence_hint),
            color = KokhonColors.NightInk,
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .padding(top = 28.dp)
                    .fillMaxWidth()
                    .background(KokhonColors.NightSurface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
        )

        Spacer(Modifier.weight(1f))

        if (alarm.canSnooze) {
            val snoozeUntil = TimeFormat.time(context, now.plus(alarm.behavior.snoozeLength))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(2.dp, Color(0xFF6B7179)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(
                            R.string.action_snooze_minutes,
                            alarm.behavior.snoozeLength
                                .toMinutes()
                                .toInt(),
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        pluralStringResource(R.plurals.alarm_snooze_detail, alarm.snoozesLeft, alarm.snoozesLeft, snoozeUntil),
                        color = KokhonColors.NightInkSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.alarm_no_snoozes_left),
                color = KokhonColors.NightInkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TextButton(onClick = onAware, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp)) {
            Text(
                stringResource(R.string.alarm_aware),
                color = KokhonColors.Highlighter,
                textDecoration = TextDecoration.Underline,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = onStop,
            modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(min = 104.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KokhonColors.Stop, contentColor = Color.White),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(
                        Modifier
                            .size(22.dp)
                            .background(Color.White, RoundedCornerShape(4.dp)),
                    )
                    Text(stringResource(R.string.action_stop), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    alarm.nextReminderAt?.let { stringResource(R.string.alarm_stop_next, TimeFormat.time(context, it)) }
                        ?: stringResource(R.string.alarm_stop_last),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFE3E8),
                )
            }
        }

        val secondsLeft = Duration.between(now, alarm.quietAt).seconds.coerceAtLeast(0)
        if (!alarm.silenced && alarm.withSound) {
            Text(
                stringResource(R.string.alarm_quiet_in, secondsLeft.toInt()),
                color = Color(0xFF9AA0A8),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
    }
}

internal fun importanceLabel(importance: Importance): Int =
    when (importance) {
        Importance.LOW -> R.string.importance_low
        Importance.NORMAL -> R.string.importance_normal
        Importance.IMPORTANT -> R.string.importance_important
        Importance.CRITICAL -> R.string.importance_critical
    }
