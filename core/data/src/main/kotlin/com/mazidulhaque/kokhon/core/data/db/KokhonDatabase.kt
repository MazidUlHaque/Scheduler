package com.mazidulhaque.kokhon.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EntryEntity::class], version = 1, exportSchema = true)
abstract class KokhonDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        fun create(context: Context): KokhonDatabase =
            Room.databaseBuilder(context.applicationContext, KokhonDatabase::class.java, "kokhon.db").build()
    }
}
