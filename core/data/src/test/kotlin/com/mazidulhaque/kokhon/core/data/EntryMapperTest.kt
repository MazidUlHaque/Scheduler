package com.mazidulhaque.kokhon.core.data

import com.mazidulhaque.kokhon.core.data.db.toEntity
import com.mazidulhaque.kokhon.core.data.db.toModel
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntrySource
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class EntryMapperTest {
    @Test
    fun entrySurvivesARoundTripThroughTheDatabaseRow() {
        val entry =
            Entry(
                id = 3,
                title = "Dinner with Nabila",
                type = EntryType.DINNER_DATE,
                start = Instant.parse("2026-11-06T14:00:00Z"),
                duration = Duration.ofMinutes(90),
                isApproximate = true,
                location = "Gulshan 2",
                notes = "Book a table",
                people = listOf("Nabila"),
                manualStatus = EntryStatus.CANCELLED,
                source =
                    EntrySource(
                        EntrySource.Kind.PASTED_TEXT,
                        "dinner at 8?",
                        Instant.parse("2026-11-06T10:00:00Z"),
                    ),
            ).withImportance(Importance.CRITICAL)
                .let { it.copy(reminders = it.reminders.copy(enabled = false)) }

        assertEquals(entry, entry.toEntity(Instant.EPOCH).toModel())
    }
}
