package com.scan2anki.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pages ADD COLUMN zoneCacheJson TEXT")
    }
}

@Database(
    entities = [ImportSession::class, Page::class, WordPair::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun importSessionDao(): ImportSessionDao
    abstract fun pageDao(): PageDao
    abstract fun wordPairDao(): WordPairDao
}
