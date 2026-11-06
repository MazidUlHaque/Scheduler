package com.mazidulhaque.kokhon.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonTheme

/** Minimum touch target for primary actions: comfortably above the 48 dp guideline. */
private val PrimaryHeight = 60.dp
private val SecondaryHeight = 48.dp

/** The one main action on a screen. Full width, placed in the bottom thumb zone. */
@Composable
fun KokhonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = PrimaryHeight),
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A secondary choice. Outlined so it never competes with the primary button. */
@Composable
fun KokhonSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SecondaryHeight),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A quiet, underlined text action such as "Edit all details". */
@Composable
fun KokhonTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 44.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = TextDecoration.Underline,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ButtonsPreview() {
    KokhonTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KokhonSecondaryButton("Save as new plan", onClick = {})
            KokhonTextButton("Edit all details", onClick = {})
        }
    }
}
