package com.mazidulhaque.kokhon.core.data

import com.mazidulhaque.kokhon.core.data.backup.Backup
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntrySource
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderChain
import com.mazidulhaque.kokhon.core.model.ReminderPreset
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class BackupTest {
    @Test
    fun plansAndPresetsSurviveABackup() {
        val plan =
            Entry(
                id = 12,
                title = "Exam",
                type = EntryType.EXAM,
                start = Instant.parse("2026-11-20T04:00:00Z"),
                people = listOf("Rafi"),
                source = EntrySource(EntrySource.Kind.SCREENSHOT, "exam on 20/11 at 10am", Instant.parse("2026-11-06T10:00:00Z")),
            )
        val preset = ReminderPreset("Gentle", ReminderChain.defaultFor(Importance.LOW))
        val restored = Backup.decode(Backup.encode(listOf(plan), listOf(preset), Instant.EPOCH))
        assertEquals(listOf(plan.copy(id = 0)), restored.entries)
        assertEquals(listOf(preset), restored.presets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun otherFilesAreRejected() {
        Backup.decode("""{"app":"other","exportedAt":0,"entries":[]}""")
    }
}
