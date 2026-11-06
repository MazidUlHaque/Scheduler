package com.mazidulhaque.kokhon.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One line in a plan's history: what changed, when, and the message that caused it. */
@Entity(tableName = "entry_history", indices = [Index("entry_id")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "at_millis") val atMillis: Long,
    val kind: String,
    val summary: String,
    @ColumnInfo(name = "source_text") val sourceText: String?,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(item: HistoryEntity)

    @Query("SELECT * FROM entry_history WHERE entry_id = :entryId ORDER BY at_millis DESC, id DESC")
    fun observe(entryId: Long): Flow<List<HistoryEntity>>

    @Query("DELETE FROM entry_history WHERE entry_id = :entryId")
    suspend fun deleteFor(entryId: Long)
}
