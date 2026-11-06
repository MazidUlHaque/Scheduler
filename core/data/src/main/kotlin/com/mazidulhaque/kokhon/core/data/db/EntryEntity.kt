package com.mazidulhaque.kokhon.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Database row for an [com.mazidulhaque.kokhon.core.model.Entry]. Times are epoch milliseconds (UTC). */
@Entity(tableName = "entries", indices = [Index("start_millis")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    @ColumnInfo(name = "start_millis") val startMillis: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Long?,
    @ColumnInfo(name = "all_day") val allDay: Boolean,
    val approximate: Boolean,
    val location: String?,
    val notes: String?,
    @ColumnInfo(name = "people_json") val peopleJson: String,
    val importance: String,
    @ColumnInfo(name = "reminders_json") val remindersJson: String,
    @ColumnInfo(name = "manual_status") val manualStatus: String?,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "source_text") val sourceText: String?,
    @ColumnInfo(name = "source_captured_millis") val sourceCapturedMillis: Long?,
    @ColumnInfo(name = "updated_millis") val updatedMillis: Long,
)
