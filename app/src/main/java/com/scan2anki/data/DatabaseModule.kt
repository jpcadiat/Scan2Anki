package com.scan2anki.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        Timber.d("Building Room database scan2anki.db")
        return Room.databaseBuilder(context, AppDatabase::class.java, "scan2anki.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideSessionDao(db: AppDatabase): ImportSessionDao {
        Timber.v("Providing ImportSessionDao")
        return db.importSessionDao()
    }

    @Provides
    fun providePageDao(db: AppDatabase): PageDao {
        Timber.v("Providing PageDao")
        return db.pageDao()
    }

    @Provides
    fun provideWordPairDao(db: AppDatabase): WordPairDao {
        Timber.v("Providing WordPairDao")
        return db.wordPairDao()
    }

    @Provides
    fun provideRepository(
        sessionDao: ImportSessionDao,
        pageDao: PageDao,
        wordPairDao: WordPairDao,
        @ApplicationContext context: Context,
    ): SessionRepository {
        val root = File(context.filesDir, "pages")
        Timber.v("Providing SessionRepository with page images root=%s", root.absolutePath)
        return SessionRepository(sessionDao, pageDao, wordPairDao, root)
    }
}
