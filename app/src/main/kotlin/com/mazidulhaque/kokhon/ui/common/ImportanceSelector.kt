package com.mazidulhaque.kokhon.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.designsystem.theme.colors
import com.mazidulhaque.kokhon.core.model.Importance

/**
 * Importance as a list of choices that explain their consequence ("Three reminders. The last one rings.")
 * instead of bare labels, so the user recognises rather than recalls what each level does.
 */
@Composable
fun ImportanceSelector(
    selected: Importance,
    isCustom: Boolean,
    onSelect: (Importance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.how_important), style = MaterialTheme.typography.titleMedium)
        Importance.entries.forEach { level ->
            val isSelected = level == selected
            val colors = level.colors()
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) colors.container else Color.Transparent,
                border = if (isSelected) BorderStroke(2.dp, colors.border) else null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(level) }),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(
                            stringResource(level.labelRes()),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) colors.text else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(
                                if (isSelected &&
                                    isCustom
                                ) {
                                    R.string.importance_custom_desc
                                } else {
                                    level.descriptionRes()
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) colors.text else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
