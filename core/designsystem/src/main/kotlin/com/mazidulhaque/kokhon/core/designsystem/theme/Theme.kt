package com.mazidulhaque.kokhon.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors: ColorScheme =
    lightColorScheme(
        primary = KokhonColors.Ink,
        onPrimary = KokhonColors.Paper,
        primaryContainer = KokhonColors.Highlighter,
        onPrimaryContainer = KokhonColors.Ink,
        secondary = KokhonColors.InkSecondary,
        onSecondary = KokhonColors.Paper,
        tertiary = KokhonColors.Done,
        onTertiary = KokhonColors.Paper,
        tertiaryContainer = KokhonColors.DoneSoft,
        onTertiaryContainer = KokhonColors.DoneText,
        error = KokhonColors.Stop,
        onError = KokhonColors.Paper,
        background = KokhonColors.Paper,
        onBackground = KokhonColors.Ink,
        surface = KokhonColors.Paper,
        onSurface = KokhonColors.Ink,
        surfaceVariant = KokhonColors.Surface,
        onSurfaceVariant = KokhonColors.InkSecondary,
        surfaceContainer = KokhonColors.Surface,
        surfaceContainerHigh = KokhonColors.Surface,
        outline = KokhonColors.InkMuted,
        outlineVariant = KokhonColors.Line,
    )

private val DarkColors: ColorScheme =
    darkColorScheme(
        primary = KokhonColors.NightInk,
        onPrimary = KokhonColors.Ink,
        primaryContainer = KokhonColors.Highlighter,
        onPrimaryContainer = KokhonColors.Ink,
        secondary = KokhonColors.NightInkSecondary,
        onSecondary = KokhonColors.Ink,
        tertiary = KokhonColors.Done,
        onTertiary = KokhonColors.Paper,
        tertiaryContainer = KokhonColors.DoneText,
        onTertiaryContainer = KokhonColors.DoneSoft,
        error = KokhonColors.Stop,
        onError = KokhonColors.Paper,
        background = KokhonColors.NightBackground,
        onBackground = KokhonColors.NightInk,
        surface = KokhonColors.NightBackground,
        onSurface = KokhonColors.NightInk,
        surfaceVariant = KokhonColors.NightSurface,
        onSurfaceVariant = KokhonColors.NightInkSecondary,
        surfaceContainer = KokhonColors.NightSurface,
        surfaceContainerHigh = KokhonColors.NightSurface,
        outline = KokhonColors.NightInkSecondary,
        outlineVariant = KokhonColors.NightLine,
    )

private val KokhonShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

@Composable
fun KokhonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KokhonTypography,
        shapes = KokhonShapes,
        content = content,
    )
}
