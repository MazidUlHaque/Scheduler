package com.mazidulhaque.kokhon.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance

/** The accent colour for each entry type. Always paired with a text label, never colour alone. */
fun EntryType.accentColor(): Color =
    when (this) {
        EntryType.MEETING -> Color(0xFF0F7C72)
        EntryType.DINNER_DATE -> Color(0xFFC23B6B)
        EntryType.DINNER -> Color(0xFFB85C1E)
        EntryType.MATCH -> Color(0xFFB7770B)
        EntryType.TASK -> Color(0xFF7A4BB0)
        EntryType.TOUR -> Color(0xFF5E7A22)
        EntryType.BIRTHDAY -> Color(0xFFB0367F)
        EntryType.APPOINTMENT -> Color(0xFF2F7D5B)
        EntryType.FLIGHT -> Color(0xFF6B4E9B)
        EntryType.EXAM -> Color(0xFF9A3B1E)
        EntryType.EVENT -> Color(0xFF4A4F57)
        EntryType.OTHER -> Color(0xFF5F6570)
    }

/** Background tint and border used to mark the selected importance level. */
data class ImportanceColors(
    val container: Color,
    val border: Color,
    val text: Color,
)

fun Importance.colors(): ImportanceColors =
    when (this) {
        Importance.LOW -> ImportanceColors(Color(0xFFF3F4F2), Color(0xFF9AA0A8), Color(0xFF4A4F57))
        Importance.NORMAL -> ImportanceColors(Color(0xFFE0F1EE), Color(0xFF0F7C72), Color(0xFF0B5E57))
        Importance.IMPORTANT -> ImportanceColors(Color(0xFFFFF4D1), Color(0xFFE0A800), Color(0xFF6E4704))
        Importance.CRITICAL -> ImportanceColors(Color(0xFFFDE4E8), Color(0xFFC8102E), Color(0xFFA30D25))
    }
