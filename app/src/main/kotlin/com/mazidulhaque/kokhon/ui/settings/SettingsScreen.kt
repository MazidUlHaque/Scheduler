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
import androidx.compose.runtime.mutableStateOf
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
import com.mazidulhaque.kokhon.ui.common.CustomValueDialog
import com.mazidulhaque.kokhon.ui.common.KokhonTopBar
import com.mazidulhaque.kokhon.ui.common.ValueUnit
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import kotlinx.coroutines.launch
import java.time.Duration

@Composable
fun durationText(value: Duration): String =
    when {
        value.seconds < 60 -> pluralStringResource(R.plurals.seconds_value, value.seconds.toInt(), value.seconds.toInt())
        value.seconds % 60 != 0L -> stringResource(R.string.minutes_seconds_value, value.toMinutes().toInt(), (value.seconds % 60).toInt())
        else -> pluralStringResource(R.plurals.minutes_value, value.toMinutes().toInt(), value.toMinutes().toInt())
    }

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = kokhonApp()
    val context = LocalContext.current
    val vm: SettingsViewModel =
        viewModel {
            SettingsViewModel(app.settingsRepository, app.clock, app.dailySummary) { enabled ->
                com.mazidulhaque.kokhon.screenshots.ScreenshotWatcher
                    .update(app, enabled)
            }
        }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val photoPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.setWatchScreenshots(granted)
        }
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
                    // Only "Restricted" background use stops reminders; "Optimized" works because Kokhon uses alarm-clock timers.
                    val restricted =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                            context.getSystemService<android.app.ActivityManager>()?.isBackgroundRestricted == true
                    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    CheckRow(stringResource(R.string.check_background), ok = !restricted) { context.openSettings(appDetails) }
                    val unrestricted =
                        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) == true
                    if (!restricted && !unrestricted) {
                        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.battery_optional),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 34.dp, end = 12.dp),
                            )
                            OutlinedButton(onClick = { context.openSettings(appDetails) }, modifier = Modifier.heightIn(min = 44.dp)) {
                                Text(stringResource(R.string.open), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                AutoStart.makerName()?.let { maker ->
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.check_autostart), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.check_autostart_desc, maker),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { AutoStart.open(context) }, modifier = Modifier.heightIn(min = 44.dp)) {
                            Text(stringResource(R.string.open), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Section(stringResource(R.string.appearance)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        com.mazidulhaque.kokhon.core.data.settings.Appearance.SYSTEM to R.string.appearance_system,
                        com.mazidulhaque.kokhon.core.data.settings.Appearance.LIGHT to R.string.appearance_light,
                        com.mazidulhaque.kokhon.core.data.settings.Appearance.DARK to R.string.appearance_dark,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = prefs.appearance == mode,
                            onClick = { vm.setAppearance(mode) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            Section(stringResource(R.string.discreet_mode)) {
                var customDiscreet by remember { mutableStateOf(false) }
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
                    FilterChip(selected = false, onClick = { customDiscreet = true }, label = { Text(stringResource(R.string.custom)) })
                }
                if (customDiscreet) {
                    DiscreetCustomDialog(
                        onDismiss = { customDiscreet = false },
                        onConfirm = { until ->
                            vm.setDiscreetUntil(until)
                            customDiscreet = false
                        },
                        now = vm.clock.instant(),
                    )
                }
            }

            Section(stringResource(R.string.alarm_settings)) {
                val seconds = ValueUnit(stringResource(R.string.unit_seconds), 1)
                val minutes = ValueUnit(stringResource(R.string.unit_minutes), 60)
                ChoiceRow(
                    title = stringResource(R.string.ring_limit),
                    options = listOf(30L, 60L, 120L, 300L).map(Duration::ofSeconds),
                    selected = prefs.alarm.ringLimit,
                    label = { durationText(it) },
                    onSelect = { value -> vm.updateAlarm { it.copy(ringLimit = value) } },
                    custom = CustomSpec(stringResource(R.string.ring_limit), 10..600, listOf(seconds, minutes)),
                    toBase = { it.seconds.toInt() },
                    fromBase = { Duration.ofSeconds(it.toLong()) },
                )
                ChoiceRow(
                    title = stringResource(R.string.snooze_length),
                    options = listOf(5L, 10L, 15L).map(Duration::ofMinutes),
                    selected = prefs.alarm.snoozeLength,
                    label = { durationText(it) },
                    onSelect = { value -> vm.updateAlarm { it.copy(snoozeLength = value) } },
                    custom =
                        CustomSpec(
                            stringResource(R.string.snooze_length),
                            1..60,
                            listOf(ValueUnit(stringResource(R.string.unit_minutes), 1)),
                        ),
                    toBase = { it.toMinutes().toInt() },
                    fromBase = { Duration.ofMinutes(it.toLong()) },
                )
                ChoiceRow(
                    title = stringResource(R.string.max_snoozes),
                    options = listOf(0, 1, 3, 5),
                    selected = prefs.alarm.maxSnoozes,
                    label = { it.toString() },
                    onSelect = { value -> vm.updateAlarm { it.copy(maxSnoozes = value) } },
                    custom =
                        CustomSpec(
                            stringResource(R.string.max_snoozes),
                            0..10,
                            listOf(ValueUnit(stringResource(R.string.unit_times), 1)),
                        ),
                    toBase = { it },
                    fromBase = { it },
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

            Section(stringResource(R.string.daily_summary)) {
                SwitchRow(
                    stringResource(R.string.morning_summary),
                    null,
                    prefs.morningSummary,
                ) { vm.setSummaries(it, prefs.eveningSummary) }
                SwitchRow(
                    stringResource(R.string.evening_summary),
                    null,
                    prefs.eveningSummary,
                ) { vm.setSummaries(prefs.morningSummary, it) }
            }

            Section(stringResource(R.string.screenshot_section)) {
                val watcher = com.mazidulhaque.kokhon.screenshots.ScreenshotWatcher
                SwitchRow(
                    stringResource(R.string.watch_screenshots),
                    stringResource(R.string.watch_screenshots_desc),
                    prefs.watchScreenshots && watcher.hasPermission(context),
                ) { enabled ->
                    if (enabled && !watcher.hasPermission(context)) {
                        photoPermission.launch(watcher.permission())
                    } else {
                        vm.setWatchScreenshots(enabled)
                    }
                }
                if (prefs.watchScreenshots && watcher.hasOnlySelectedPhotos(context)) {
                    Text(
                        stringResource(R.string.only_selected_photos),
                        style = MaterialTheme.typography.bodySmall,
                        color = KokhonColors.StopText,
                    )
                }
            }

            BackupSection(app)

            Section(stringResource(R.string.my_presets)) {
                if (prefs.presets.isEmpty()) {
                    Text(
                        stringResource(R.string.presets_settings_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                prefs.presets.forEach { preset ->
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                com.mazidulhaque.kokhon.ui.common
                                    .reminderSentence(preset.chain),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = { vm.deletePreset(preset.name) }) {
                            androidx.compose.material3.Icon(
                                com.mazidulhaque.kokhon.core.designsystem.component.KokhonIcons.Trash,
                                contentDescription = stringResource(R.string.delete_preset_cd, preset.name),
                            )
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Section(stringResource(R.string.language)) {
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.language_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                context.openSettings(
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()),
                                )
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) { Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
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
    custom: CustomSpec,
    toBase: (T) -> Int,
    fromBase: (Int) -> T,
) {
    var editing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
            // A value the user typed shows as its own selected chip.
            if (selected !in options) FilterChip(selected = true, onClick = { editing = true }, label = { Text(label(selected)) })
            FilterChip(selected = false, onClick = { editing = true }, label = { Text(stringResource(R.string.custom)) })
        }
    }
    if (editing) {
        CustomValueDialog(
            title = custom.title,
            range = custom.range,
            units = custom.units,
            initialBase = toBase(selected),
            onDismiss = { editing = false },
            onConfirm = { base ->
                onSelect(fromBase(base))
                editing = false
            },
        )
    }
}

/** What the "Custom…" dialog of a choice row asks for. */
private data class CustomSpec(
    val title: String,
    val range: IntRange,
    val units: List<ValueUnit>,
)

/** Discreet mode for any length of time, or until a chosen clock time. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DiscreetCustomDialog(
    now: java.time.Instant,
    onDismiss: () -> Unit,
    onConfirm: (java.time.Instant) -> Unit,
) {
    val context = LocalContext.current
    var untilMode by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("45") }
    var hoursUnit by remember { mutableStateOf(false) }
    val picker =
        androidx.compose.material3.rememberTimePickerState(
            initialHour = (now.atZone(java.time.ZoneId.systemDefault()).hour + 1) % 24,
            initialMinute = 0,
            is24Hour =
                android.text.format.DateFormat
                    .is24HourFormat(context),
        )
    val minutes = amount.toIntOrNull()?.let { if (hoursUnit) it * 60 else it }
    val validFor = minutes != null && minutes in 1..(24 * 60)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discreet_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !untilMode,
                        onClick = { untilMode = false },
                        label = { Text(stringResource(R.string.discreet_for)) },
                    )
                    FilterChip(
                        selected = untilMode,
                        onClick = { untilMode = true },
                        label = { Text(stringResource(R.string.discreet_until)) },
                    )
                }
                if (untilMode) {
                    androidx.compose.material3.TimePicker(state = picker)
                } else {
                    androidx.compose.material3.OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        keyboardOptions =
                            androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !hoursUnit,
                            onClick = { hoursUnit = false },
                            label = { Text(stringResource(R.string.unit_minutes)) },
                        )
                        FilterChip(
                            selected = hoursUnit,
                            onClick = { hoursUnit = true },
                            label = { Text(stringResource(R.string.unit_hours)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = untilMode || validFor,
                onClick = {
                    val until =
                        if (untilMode) {
                            val zone = java.time.ZoneId.systemDefault()
                            var at =
                                now
                                    .atZone(zone)
                                    .toLocalDate()
                                    .atTime(picker.hour, picker.minute)
                                    .atZone(zone)
                            if (!at.toInstant().isAfter(now)) at = at.plusDays(1)
                            at.toInstant()
                        } else {
                            now.plus(Duration.ofMinutes(minutes!!.toLong()))
                        }
                    onConfirm(until)
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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

@Composable
private fun BackupSection(app: com.mazidulhaque.kokhon.KokhonApplication) {
    val transfer =
        remember(app) {
            com.mazidulhaque.kokhon.transfer
                .DataTransfer(app)
        }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val failed = stringResource(R.string.transfer_failed)

    fun run(block: suspend () -> String) {
        scope.launch { message = runCatching { block() }.getOrElse { failed } }
    }

    fun imported(result: com.mazidulhaque.kokhon.transfer.DataTransfer.ImportResult) =
        listOfNotNull(
            resources.getQuantityString(R.plurals.imported_added, result.added, result.added),
            result.skipped.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.imported_skipped, it, it) },
        ).joinToString(" ")

    fun exported(count: Int) = resources.getQuantityString(R.plurals.exported_n, count, count)

    val backup =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { run { exported(transfer.backup(it)) } }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { run { imported(transfer.restore(it)) } }
        }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
            uri?.let { run { exported(transfer.exportCalendar(it)) } }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { run { imported(transfer.importCalendar(it)) } }
        }
    val today = java.time.LocalDate.now(app.clock)

    Section(stringResource(R.string.backup_section)) {
        Text(
            stringResource(R.string.backup_section_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BackupButton(stringResource(R.string.backup_now)) { backup.launch("kokhon-backup-$today.json") }
        BackupButton(stringResource(R.string.restore_backup)) {
            restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        BackupButton(stringResource(R.string.export_calendar)) { export.launch("kokhon-$today.ics") }
        BackupButton(
            stringResource(R.string.import_calendar),
        ) { import.launch(arrayOf("text/calendar", "text/plain", "application/octet-stream")) }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun BackupButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}
