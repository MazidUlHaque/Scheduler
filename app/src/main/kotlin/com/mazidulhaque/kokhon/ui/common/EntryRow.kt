package com.mazidulhaque.kokhon.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mazidulhaque.kokhon.core.designsystem.component.DoneBadge
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import com.mazidulhaque.kokhon.core.designsystem.theme.accentColor
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import java.time.Instant

/** A compact plan row used by the calendar and search: time, colour mark, title, kind. Finished plans are faded. */
@Composable
fun EntryRow(
    entry: Entry,
    now: Instant,
    showDate: Boolean,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val status = entry.statusAt(now)
    val finished = entry.isFinishedAt(now)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .alpha(if (finished) 0.62f else 1f)
                .clickable(role = Role.Button, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.width(if (showDate) 96.dp else 76.dp)) {
            if (showDate) Text(TimeFormat.shortDate(entry.start), style = MaterialTheme.typography.bodySmall)
            Text(TimeFormat.time(context, entry.start), style = MaterialTheme.typography.labelMedium)
        }
        if (status == EntryStatus.COMPLETED) {
            DoneBadge()
        } else {
            Box(Modifier.width(4.dp).height(36.dp).background(entry.type.accentColor(), RoundedCornerShape(2.dp)))
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (status == EntryStatus.CANCELLED) TextDecoration.LineThrough else null,
            )
            Text(
                stringResource(entry.type.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
