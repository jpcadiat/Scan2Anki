package com.scan2anki.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.parse.OcrCleanup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private lateinit var settings: AppSettings
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "test_settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            produceFile = { file },
        )
        settings = AppSettings(dataStore)
    }

    @Test
    fun defaults_areEmptyAndLatin() = runTest {
        assertThat(settings.apiKey.first()).isEmpty()
        assertThat(settings.defaultDeckName.first()).isEmpty()
        assertThat(settings.ocrScript.first()).isEqualTo("latin")
    }

    @Test
    fun setters_persistValues() = runTest {
        settings.setApiKey("abc")
        settings.setDefaultDeckName("Spanish")
        settings.setOcrScript("chinese")
        assertThat(settings.apiKey.first()).isEqualTo("abc")
        assertThat(settings.defaultDeckName.first()).isEqualTo("Spanish")
        assertThat(settings.ocrScript.first()).isEqualTo("chinese")
    }

    @Test
    fun defaultNoteType_startsBlankAndPersistsValue() = runTest {
        assertThat(settings.defaultNoteType.first()).isEmpty()
        settings.setDefaultNoteType("Basic (and reversed card)")
        assertThat(settings.defaultNoteType.first()).isEqualTo("Basic (and reversed card)")
    }

    @Test
    fun cleanupConfig_defaultsToCleanupConfigDefaults() = runTest {
        assertThat(settings.cleanupConfig.first()).isEqualTo(OcrCleanup.CleanupConfig())
    }

    @Test
    fun setCleanupConfig_persistsValue() = runTest {
        val config = OcrCleanup.CleanupConfig(
            trimJunk = OcrCleanup.TrimJunkRule(
                enabled = true,
                scope = OcrCleanup.ColumnScope.BACK,
                extraChars = "#~",
            ),
        )
        settings.setCleanupConfig(config)
        assertThat(settings.cleanupConfig.first()).isEqualTo(config)
    }

    @Test
    fun ageCheck_defaultsToUnknownAndPersists() = runTest {
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.UNKNOWN)
        settings.setAgeCheck(AgeCheck.UNDER_13)
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.UNDER_13)
        settings.setAgeCheck(AgeCheck.ADULT_OR_TEEN)
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.ADULT_OR_TEEN)
    }

    @Test
    fun ageCheck_unrecognisedStoredValue_isUnknown() = runTest {
        dataStore.edit { it[stringPreferencesKey("age_check")] = "SOMETHING_ELSE" }
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.UNKNOWN)
    }

    @Test
    fun cloudOcrApiKey_isBlankUnlessAdultOrTeen() = runTest {
        settings.setApiKey("abc")
        assertThat(settings.cloudOcrApiKey.first()).isEmpty()
        settings.setAgeCheck(AgeCheck.UNDER_13)
        assertThat(settings.cloudOcrApiKey.first()).isEmpty()
        settings.setAgeCheck(AgeCheck.ADULT_OR_TEEN)
        assertThat(settings.cloudOcrApiKey.first()).isEqualTo("abc")
    }
}
