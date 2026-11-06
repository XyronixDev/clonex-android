package com.clonex.space.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CloneEntry::class], version = 1, exportSchema = false)
abstract class CloneDatabase : RoomDatabase() {
    abstract fun cloneDao(): CloneDao

    companion object {
        @Volatile
        private var instance: CloneDatabase? = null

        fun get(context: Context): CloneDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloneDatabase::class.java,
                    "clonex.db"
                ).build().also { instance = it }
            }
    }
}
