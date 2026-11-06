package com.mazidulhaque.kokhon.core.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EntryEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class KokhonDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): KokhonDatabase =
            Room.databaseBuilder(context.applicationContext, KokhonDatabase::class.java, "kokhon.db").build()
    }
}
