package com.mazidulhaque.kokhon.core.data.repository

import com.mazidulhaque.kokhon.core.data.db.EntryDao
import com.mazidulhaque.kokhon.core.data.db.HistoryDao
import com.mazidulhaque.kokhon.core.data.db.HistoryEntity
import com.mazidulhaque.kokhon.core.data.db.toEntity
import com.mazidulhaque.kokhon.core.data.db.toModel
import com.mazidulhaque.kokhon.core.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

/** Single source of truth for entries. Callers schedule reminders after each change. */
class EntryRepository(
    private val dao: EntryDao,
    private val historyDao: HistoryDao,
    private val clock: Clock,
) {
    fun observeFrom(from: Instant): Flow<List<Entry>> = dao.observeFrom(from.toEpochMilli()).map { rows -> rows.map { it.toModel() } }

    fun observe(id: Long): Flow<Entry?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: Long): Entry? = dao.get(id)?.toModel()

    suspend fun startingFrom(from: Instant): List<Entry> = dao.startingFrom(from.toEpochMilli()).map { it.toModel() }

    /** Inserts or replaces the entry and returns it with its database id. */
    suspend fun save(entry: Entry): Entry {
        val id = dao.upsert(entry.toEntity(clock.instant()))
        return entry.copy(id = id)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    fun observeHistory(entryId: Long): Flow<List<HistoryItem>> =
        historyDao.observe(entryId).map { rows ->
            rows.map { HistoryItem(Instant.ofEpochMilli(it.atMillis), HistoryItem.Kind.from(it.kind), it.summary, it.sourceText) }
        }

    suspend fun addHistory(
        entryId: Long,
        kind: HistoryItem.Kind,
        summary: String,
        sourceText: String? = null,
    ) {
        historyDao.insert(
            HistoryEntity(
                entryId = entryId,
                atMillis = clock.instant().toEpochMilli(),
                kind = kind.name,
                summary = summary,
                sourceText = sourceText,
            ),
        )
    }
}

/** A line in a plan's history. */
data class HistoryItem(
    val at: Instant,
    val kind: Kind,
    val summary: String,
    val sourceText: String?,
) {
    enum class Kind {
        CREATED,
        CREATED_FROM_MESSAGE,
        EDITED,
        UPDATED_FROM_MESSAGE,
        STATUS,
        ;

        companion object {
            fun from(name: String) = entries.firstOrNull { it.name == name } ?: EDITED
        }
    }
}

/** Builds the repository on top of the app database, keeping Room an implementation detail of this module. */
fun createEntryRepository(
    context: android.content.Context,
    clock: Clock,
): EntryRepository {
    val db =
        com.mazidulhaque.kokhon.core.data.db.KokhonDatabase
            .create(context)
    return EntryRepository(db.entryDao(), db.historyDao(), clock)
}
