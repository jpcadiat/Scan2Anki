package com.scan2anki.ankidroid

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object AnkiDroidModule {

    @Provides
    @Singleton
    fun provideAnkiDroidSender(@ApplicationContext context: Context): AnkiDroidSender {
        Timber.v("Providing AnkiDroidSender")
        return AnkiDroidImporter(context)
    }
}
