package com.mazidulhaque.kokhon.core.data.backup

import com.mazidulhaque.kokhon.core.data.db.EntryEntity
import com.mazidulhaque.kokhon.core.data.db.reminderChainFromJson
import com.mazidulhaque.kokhon.core.data.db.toEntity
import com.mazidulhaque.kokhon.core.data.db.toJson
import com.mazidulhaque.kokhon.core.data.db.toModel
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.ReminderPreset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** A complete, human-readable backup of plans and presets, for moving to a new phone. */
object Backup {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(
        entries: List<Entry>,
        presets: List<ReminderPreset>,
        now: Instant,
    ): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                exportedAt = now.toEpochMilli(),
                entries = entries.map { it.toEntity(now).toBackup() },
                presets = presets.map { BackupPreset(it.name, it.chain.toJson()) },
            ),
        )

    /** Entries come back without ids, ready to be saved as new plans. */
    fun decode(text: String): Restored {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.app == APP) { "Not a Kokhon backup" }
        return Restored(
            entries = file.entries.map { it.toEntity().toModel().copy(id = 0) },
            presets = file.presets.map { ReminderPreset(it.name, reminderChainFromJson(it.chain)) },
        )
    }

    data class Restored(
        val entries: List<Entry>,
        val presets: List<ReminderPreset>,
    )

    private const val APP = "kokhon"

    @Serializable
    private data class BackupFile(
        val app: String = APP,
        val version: Int = 1,
        val exportedAt: Long,
        val entries: List<BackupEntry>,
        val presets: List<BackupPreset> = emptyList(),
    )

    @Serializable
    private data class BackupPreset(
        val name: String,
        val chain: String,
    )

    @Serializable
    private data class BackupEntry(
        val title: String,
        val type: String,
        val startMillis: Long,
        val durationMinutes: Long? = null,
        val allDay: Boolean = false,
        val approximate: Boolean = false,
        val location: String? = null,
        val notes: String? = null,
        val peopleJson: String = "[]",
        val importance: String,
        val remindersJson: String,
        val manualStatus: String? = null,
        val sourceKind: String = "MANUAL",
        val sourceText: String? = null,
        val sourceCapturedMillis: Long? = null,
    )

    private fun EntryEntity.toBackup() =
        BackupEntry(
            title,
            type,
            startMillis,
            durationMinutes,
            allDay,
            approximate,
            location,
            notes,
            peopleJson,
            importance,
            remindersJson,
            manualStatus,
            sourceKind,
            sourceText,
            sourceCapturedMillis,
        )

    private fun BackupEntry.toEntity() =
        EntryEntity(
            id = 0,
            title = title,
            type = type,
            startMillis = startMillis,
            durationMinutes = durationMinutes,
            allDay = allDay,
            approximate = approximate,
            location = location,
            notes = notes,
            peopleJson = peopleJson,
            importance = importance,
            remindersJson = remindersJson,
            manualStatus = manualStatus,
            sourceKind = sourceKind,
            sourceText = sourceText,
            sourceCapturedMillis = sourceCapturedMillis,
            updatedMillis = 0,
        )
}
