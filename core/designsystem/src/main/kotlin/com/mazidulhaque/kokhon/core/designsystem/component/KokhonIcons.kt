package com.mazidulhaque.kokhon.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Simple stroke icons (24 dp grid). Tinted by the Icon composable, so the stroke colour is irrelevant. */
object KokhonIcons {
    private fun icon(
        name: String,
        vararg paths: String,
        strokeWidth: Float = 2f,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                paths.forEach {
                    addPath(
                        pathData = addPathNodes(it),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = strokeWidth,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    val Back = icon("Back", "M15 18l-6-6 6-6")
    val ChevronRight = icon("ChevronRight", "M9 6l6 6-6 6")
    val Close = icon("Close", "M18 6L6 18", "M6 6l12 12")
    val Plus = icon("Plus", "M12 5v14", "M5 12h14", strokeWidth = 2.4f)
    val Check = icon("Check", "M5 12l5 5L20 7", strokeWidth = 2.6f)
    val Bell = icon("Bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val Alarm = icon("Alarm", "M12 5a8 8 0 1 0 0.01 0", "M12 9v4l2 2", "M5 3L2 6", "M22 6l-3-3")
    val Moon = icon("Moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")
    val Settings =
        icon(
            "Settings",
            "M4 21v-7",
            "M4 10V3",
            "M12 21v-9",
            "M12 8V3",
            "M20 21v-5",
            "M20 12V3",
            "M1 14h6",
            "M9 8h6",
            "M17 16h6",
        )
    val Pencil = icon("Pencil", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val Trash = icon("Trash", "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6")
}
