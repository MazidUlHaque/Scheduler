package com.mazidulhaque.kokhon.ui.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors

/**
 * Knows whether the clipboard holds text, without reading it: Android shows a privacy notice
 * whenever an app reads the clipboard, so the text is only read when the user asks.
 */
class ClipboardOffer internal constructor(
    private val manager: ClipboardManager?,
) {
    var available by mutableStateOf(false)
        private set
    private var dismissedStamp by mutableLongStateOf(-1L)

    internal fun refresh() {
        val description = manager?.primaryClipDescription
        val hasText = description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
        available = hasText && description.timestamp != dismissedStamp
    }

    fun dismiss() {
        dismissedStamp = manager?.primaryClipDescription?.timestamp ?: -1L
        available = false
    }

    /** Reads the copied text. Called only after the user tapped a button. */
    fun read(context: Context): String {
        val clip = manager?.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        dismiss()
        return clip
            .getItemAt(0)
            .coerceToText(context)
            ?.toString()
            .orEmpty()
    }
}

@Composable
fun rememberClipboardOffer(): ClipboardOffer {
    val context = LocalContext.current
    val offer = remember { ClipboardOffer(context.getSystemService<ClipboardManager>()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { offer.refresh() }
    DisposableEffect(offer) {
        val manager = context.getSystemService<ClipboardManager>()
        val listener = ClipboardManager.OnPrimaryClipChangedListener { offer.refresh() }
        manager?.addPrimaryClipChangedListener(listener)
        onDispose { manager?.removePrimaryClipChangedListener(listener) }
    }
    return offer
}

/** A polite, dismissible suggestion above the bottom bar. It never interrupts. */
@Composable
fun ClipboardBanner(
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .fillMaxWidth()
                .background(KokhonColors.Ink, MaterialTheme.shapes.medium)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.copied_message), color = KokhonColors.Paper, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onUse,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = KokhonColors.Highlighter, contentColor = KokhonColors.Ink),
            ) { Text(stringResource(R.string.make_it_a_plan), style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.not_now), color = KokhonColors.Paper)
            }
        }
    }
}

/** "Add a plan" offers the two ways in, with what each does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlanSheet(
    onDismiss: () -> Unit,
    onPaste: () -> Unit,
    onType: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.add_a_plan), style = MaterialTheme.typography.titleLarge)
            SheetOption(stringResource(R.string.paste_a_message), stringResource(R.string.paste_a_message_desc), onPaste)
            SheetOption(stringResource(R.string.type_it_yourself), stringResource(R.string.type_it_yourself_desc), onType)
        }
    }
}

@Composable
private fun SheetOption(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
