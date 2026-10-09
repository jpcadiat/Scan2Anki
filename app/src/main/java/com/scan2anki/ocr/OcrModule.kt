package com.scan2anki.ocr

import com.scan2anki.settings.AppSettings
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        Timber.v("Providing OkHttpClient")
        return OkHttpClient()
    }

    @Provides
    @Singleton
    @Named("onDevice")
    fun provideOnDeviceOcr(settings: AppSettings): OcrEngine {
        Timber.d("Providing on-device OCR engine (ML Kit)")
        return MlKitOcrEngine(settings.ocrScript)
    }

    @Provides
    @Singleton
    fun provideCloudVisionOcrEngine(client: OkHttpClient, settings: AppSettings): CloudVisionOcrEngine {
        Timber.d("Providing CloudVisionOcrEngine")
        return CloudVisionOcrEngine(client) { settings.cloudOcrApiKey.first() }
    }

    @Provides
    @Singleton
    @Named("cloud")
    fun provideCloudOcr(cloudVisionOcrEngine: CloudVisionOcrEngine): OcrEngine {
        Timber.d("Providing cloud OCR engine (Cloud Vision)")
        return cloudVisionOcrEngine
    }
}