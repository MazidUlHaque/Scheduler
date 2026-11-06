package com.mazidulhaque.kokhon.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mazidulhaque.kokhon.core.designsystem.R

/**
 * Atkinson Hyperlegible, by the Braille Institute: letters and numbers that are easily
 * confused (1/l/I, 0/O) are deliberately distinct. Licensed under the SIL OFL 1.1.
 */
val Hyperlegible =
    FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = Hyperlegible,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
)

/** Body text starts at 16-17 sp so it stays readable at arm's length. */
val KokhonTypography =
    Typography(
        displayLarge = style(40, 44, FontWeight.Bold, -0.01),
        displayMedium = style(36, 40, FontWeight.Bold, -0.02),
        headlineLarge = style(32, 37, FontWeight.Bold, -0.01),
        headlineMedium = style(28, 33, FontWeight.Bold, -0.01),
        headlineSmall = style(26, 32, FontWeight.Bold),
        titleLarge = style(20, 26, FontWeight.Bold),
        titleMedium = style(18, 24, FontWeight.Bold),
        titleSmall = style(16, 22, FontWeight.Bold),
        bodyLarge = style(17, 25),
        bodyMedium = style(16, 23),
        bodySmall = style(14, 20),
        labelLarge = style(16, 22, FontWeight.Bold),
        labelMedium = style(14, 20, FontWeight.Bold),
        labelSmall = style(13, 18),
    )
