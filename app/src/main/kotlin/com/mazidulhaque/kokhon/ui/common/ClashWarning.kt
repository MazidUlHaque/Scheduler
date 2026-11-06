package com.mazidulhaque.kokhon.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.model.Entry

private val WarningText = Color(0xFF8A6200)

/** A gentle, non-blocking note: two plans at the same time is allowed, but the user should know. */
@Composable
fun ClashWarning(clashes: List<Entry>) {
    val context = LocalContext.current
    clashes.take(2).forEach { other ->
        Text(
            stringResource(R.string.clashes_with, other.title, TimeFormat.time(context, other.start)),
            style = MaterialTheme.typography.bodySmall,
            color = WarningText,
        )
    }
}

/** Shown when the same plan seems to be saved already, with a way to open it instead. */
@Composable
fun DuplicateWarning(
    duplicate: Entry,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(
                R.string.already_have,
                duplicate.title,
                TimeFormat.shortDate(duplicate.start) + ", " + TimeFormat.time(context, duplicate.start),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = WarningText,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.open_it), textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
