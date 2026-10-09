package com.scan2anki

import android.util.Log
import timber.log.Timber

class ReleaseTree : Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN
}