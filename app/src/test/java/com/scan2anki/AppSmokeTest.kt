package com.scan2anki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSmokeTest {

    @Test
    fun appNameIsSet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(context.getString(R.string.app_name)).isEqualTo("Scan2Anki")
    }
}