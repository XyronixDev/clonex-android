package com.clonex.space.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CloneEntry::class], version = 2, exportSchema = false)
abstract class CloneDatabase : RoomDatabase() {
    abstract fun cloneDao(): CloneDao

    companion object {
        @Volatile
        private var instance: CloneDatabase? = null

        /** v1 -> v2: Secret Zone flag on clones (hidden from the home grid). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clones ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): CloneDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloneDatabase::class.java,
                    "clonex.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
