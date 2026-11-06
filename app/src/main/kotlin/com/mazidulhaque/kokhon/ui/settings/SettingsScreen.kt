package com.mazidulhaque.kokhon.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.alarm.ReminderNotifier
import com.mazidulhaque.kokhon.core.designsystem.component.DoneBadge
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors
import com.mazidulhaque.kokhon.ui.common.KokhonTopBar
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import java.time.Duration

@Composable
fun durationText(value: Duration): String =
    when {
        value.seconds < 60 -> pluralStringResource(R.plurals.seconds_value, value.seconds.toInt(), value.seconds.toInt())
        else -> pluralStringResource(R.plurals.minutes_value, value.toMinutes().toInt(), value.toMinutes().toInt())
    }

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = kokhonApp()
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app.settingsRepository, app.clock) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val prefs = settings ?: return

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        KokhonTopBar(title = stringResource(R.string.settings), onBack = onBack)
        Column(
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Reliability first: if reminders can't arrive, nothing else matters.
            Section(stringResource(R.string.reliability)) {
                Text(
                    stringResource(R.string.reliability_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.runtime.key(refresh) {
                    CheckRow(
                        stringResource(R.string.check_notifications),
                        ok = ReminderNotifier.canPostNotifications(context),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.openSettings(
                                Intent(
                                    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    }
                    CheckRow(
                        stringResource(R.string.check_exact_alarms),
                        ok = app.alarmScheduler.canScheduleExactAlarms(),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.openSettings(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val canFullScreen =
                            context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true
                        CheckRow(stringResource(R.string.check_full_screen), ok = canFullScreen) {
                            context.openSettings(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        }
                    }
                    val ignoringBattery =
                        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ==
                            true
                    CheckRow(stringResource(R.string.check_battery), ok = ignoringBattery) {
                        context.openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }

            Section(stringResource(R.string.discreet_mode)) {
                Text(
                    stringResource(R.string.discreet_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val on = prefs.isDiscreetAt(vm.clock.instant())
                if (on) {
                    Text(
                        stringResource(R.string.discreet_on_until, TimeFormat.time(context, prefs.discreetUntil!!)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !on, onClick = {
                        vm.setDiscreet(SettingsViewModel.DiscreetChoice.OFF)
                    }, label = { Text(stringResource(R.string.discreet_off)) })
                    FilterChip(selected = false, onClick = {
                        vm.setDiscreet(SettingsViewModel.DiscreetChoice.ONE_HOUR)
                    }, label = { Text(stringResource(R.string.discreet_1h)) })
                    FilterChip(selected = false, onClick = {
                        vm.setDiscreet(SettingsViewModel.DiscreetChoice.TWO_HOURS)
                    }, label = { Text(stringResource(R.string.discreet_2h)) })
                    FilterChip(selected = false, onClick = {
                        vm.setDiscreet(SettingsViewModel.DiscreetChoice.UNTIL_MORNING)
                    }, label = { Text(stringResource(R.string.discreet_until_morning)) })
                }
            }

            Section(stringResource(R.string.alarm_settings)) {
                ChoiceRow(
                    title = stringResource(R.string.ring_limit),
                    options = listOf(30L, 60L, 120L, 300L).map(Duration::ofSeconds),
                    selected = prefs.alarm.ringLimit,
                    label = { durationText(it) },
                    onSelect = { value -> vm.updateAlarm { it.copy(ringLimit = value) } },
                )
                ChoiceRow(
                    title = stringResource(R.string.snooze_length),
                    options = listOf(5L, 10L, 15L).map(Duration::ofMinutes),
                    selected = prefs.alarm.snoozeLength,
                    label = { durationText(it) },
                    onSelect = { value -> vm.updateAlarm { it.copy(snoozeLength = value) } },
                )
                ChoiceRow(
                    title = stringResource(R.string.max_snoozes),
                    options = listOf(0, 1, 3, 5),
                    selected = prefs.alarm.maxSnoozes,
                    label = { it.toString() },
                    onSelect = { value -> vm.updateAlarm { it.copy(maxSnoozes = value) } },
                )
                SwitchRow(
                    stringResource(R.string.volume_button_setting),
                    stringResource(R.string.volume_button_setting_desc),
                    prefs.alarm.volumeButtonSilences,
                ) { value -> vm.updateAlarm { it.copy(volumeButtonSilences = value) } }
                SwitchRow(stringResource(R.string.gentle_start_setting), null, prefs.alarm.gentleStart) { value ->
                    vm.updateAlarm { it.copy(gentleStart = value) }
                }
                SwitchRow(
                    stringResource(R.string.critical_bypass_setting),
                    stringResource(R.string.critical_bypass_setting_desc),
                    prefs.criticalBypassesSilent,
                    vm::setCriticalBypass,
                )
            }

            Section(stringResource(R.string.about_app)) {
                Text(
                    stringResource(R.string.app_name) + " - " + stringResource(R.string.tagline),
                    style = MaterialTheme.typography.titleSmall,
                )
                val version =
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull().orEmpty()
                Text(
                    stringResource(R.string.version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Context.openSettings(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$packageName".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        content()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CheckRow(
    label: String,
    ok: Boolean,
    onFix: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ok) {
            DoneBadge(size = 22.dp, contentDescription = stringResource(R.string.ok_check))
        } else {
            Text(
                "!",
                color = KokhonColors.StopText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (!ok) {
            OutlinedButton(onClick = onFix, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(R.string.fix), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
