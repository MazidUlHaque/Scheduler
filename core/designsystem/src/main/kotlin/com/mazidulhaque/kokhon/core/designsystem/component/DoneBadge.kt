package com.mazidulhaque.kokhon.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonColors

/** The green check shown on completed entries. Announced as "Completed" to screen readers. */
@Composable
fun DoneBadge(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    contentDescription: String = "Completed",
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(KokhonColors.Done)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.6f)) {
            val path =
                Path().apply {
                    moveTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height * 0.52f)
                    lineTo(this@Canvas.size.width * 0.40f, this@Canvas.size.height * 0.80f)
                    lineTo(this@Canvas.size.width * 0.90f, this@Canvas.size.height * 0.22f)
                }
            drawPath(
                path,
                color = KokhonColors.Paper,
                style = Stroke(width = this.size.width * 0.16f, cap = StrokeCap.Round),
            )
        }
    }
}
