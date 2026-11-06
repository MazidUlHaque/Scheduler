package com.mazidulhaque.kokhon.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors

/** A unit the user can type a number in, and how many base units it is worth (e.g. minutes = 60 seconds). */
data class ValueUnit(
    val label: String,
    val factor: Int,
)

/**
 * Lets the user type any value instead of picking a ready-made one.
 * The result is in base units (e.g. seconds), and must fall within [range].
 */
@Composable
fun CustomValueDialog(
    title: String,
    range: IntRange,
    units: List<ValueUnit>,
    initialBase: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // Start with the largest unit that divides the current value evenly.
    val startUnit = units.indices.lastOrNull { initialBase % units[it].factor == 0 && initialBase >= units[it].factor } ?: 0
    var unitIndex by remember { mutableIntStateOf(startUnit) }
    var text by remember { mutableStateOf((initialBase / units[startUnit].factor).toString()) }
    val value = text.trim().toIntOrNull()?.times(units[unitIndex].factor)
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleMedium,
                )
                if (units.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        units.forEachIndexed { i, unit ->
                            FilterChip(selected = i == unitIndex, onClick = { unitIndex = i }, label = { Text(unit.label) })
                        }
                    }
                }
                if (!valid && text.isNotBlank()) {
                    Text(
                        stringResource(R.string.custom_out_of_range, rangeText(range, units)),
                        style = MaterialTheme.typography.bodySmall,
                        color = KokhonColors.StopText,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { value?.let(onConfirm) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "10 seconds - 10 minutes" style hint, using the units the dialog offers. */
private fun rangeText(
    range: IntRange,
    units: List<ValueUnit>,
): String {
    fun describe(base: Int): String {
        val unit = units.lastOrNull { base % it.factor == 0 && base >= it.factor } ?: units.first()
        return "${base / unit.factor} ${unit.label}"
    }
    return describe(range.first) + " - " + describe(range.last)
}
