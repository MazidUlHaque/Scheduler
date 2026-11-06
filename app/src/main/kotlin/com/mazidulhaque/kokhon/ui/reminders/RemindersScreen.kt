package com.mazidulhaque.kokhon.ui.reminders

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonIcons
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonPrimaryButton
import com.mazidulhaque.kokhon.core.designsystem.component.KokhonTextButton
import com.mazidulhaque.kokhon.ui.common.ImportanceSelector
import com.mazidulhaque.kokhon.ui.common.KokhonTopBar
import com.mazidulhaque.kokhon.ui.common.ReminderChainEditor
import com.mazidulhaque.kokhon.ui.common.isCustomFor
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import com.mazidulhaque.kokhon.ui.common.reminderSentence
import com.mazidulhaque.kokhon.ui.settings.durationText

@Composable
fun RemindersScreen(
    id: Long,
    onBack: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
) {
    val app = kokhonApp()
    val vm: RemindersViewModel =
        viewModel(key = "reminders-$id") { RemindersViewModel(id, app.entryRepository, app.reminderCoordinator, app.settingsRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = state ?: return

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        KokhonTopBar(title = stringResource(R.string.reminders_for, s.entry.title), onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ImportanceSelector(
                selected = s.importance,
                isCustom = s.chain.isCustomFor(s.importance, s.entry.type),
                onSelect = vm::setImportance,
            )
            Text(reminderSentence(s.chain), style = MaterialTheme.typography.bodyLarge)
            if (s.chain.enabled) {
                ReminderChainEditor(chain = s.chain, entryStart = s.entry.start, onChange = vm::setChain)
            }
            settings?.let { prefs ->
                val volumeRes = if (prefs.alarm.volumeButtonSilences) R.string.volume_silences else R.string.volume_does_not_silence
                // Rarely changed, so folded into one summary row (progressive disclosure).
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onOpenAlarmSettings),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.when_alarm_rings), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                R.string.alarm_summary,
                                durationText(prefs.alarm.ringLimit),
                                prefs.alarm.snoozeLength
                                    .toMinutes()
                                    .toInt(),
                                prefs.alarm.maxSnoozes,
                                stringResource(volumeRes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(KokhonIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KokhonPrimaryButton(text = stringResource(R.string.done), onClick = { vm.save(onBack) })
            KokhonTextButton(
                text = stringResource(if (s.chain.enabled) R.string.turn_off_all_reminders else R.string.turn_on_reminders),
                onClick = { vm.setAllEnabled(!s.chain.enabled) },
            )
        }
    }
}
