package com.scan2anki

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class Scan2AnkiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.v("Timber initialized with DebugTree for debug build")
        } else {
            Timber.plant(ReleaseTree())
            Timber.i("Timber initialized with ReleaseTree (WARN+) for release build")
        }
        Timber.i("Scan2Anki application started")
    }
}