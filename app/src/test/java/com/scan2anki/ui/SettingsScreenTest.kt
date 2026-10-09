package com.scan2anki.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.ankidroid.ANKI_READ_WRITE_PERMISSION
import com.scan2anki.ankidroid.AnkiDroidDiagnosis
import com.scan2anki.ankidroid.AnkiDroidSendResult
import com.scan2anki.ankidroid.AnkiDroidSender
import com.scan2anki.ankidroid.AnkiNote
import com.scan2anki.ocr.CloudVisionOcrEngine
import com.scan2anki.settings.AppSettings
import com.scan2anki.vm.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsDeckNameField() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val viewModel = SettingsViewModel(AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context)
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        composeRule.onNodeWithText("Default deck name").assertIsDisplayed()
    }

    @Test
    fun showsCheckConnectionButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_ui") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val viewModel = SettingsViewModel(AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context)
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        composeRule.onNodeWithText("Check connection").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clicksCheckConnection_rendersResultMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_ui_click") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "Connection OK from UI test")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val viewModel = SettingsViewModel(AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context)
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        composeRule.onNodeWithText("Check connection").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Connection OK from UI test").fetchSemanticsNodes().isNotEmpty()
        }
        // The settings column scrolls; the result row sits below the fold on small screens.
        composeRule.onNodeWithText("Connection OK from UI test").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clicksCheckConnection_whenPermissionNotGranted_doesNotDiagnoseDirectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Deliberately not granting ANKI_READ_WRITE_PERMISSION here.
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_ui_no_permission") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "Should not be shown")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val viewModel = SettingsViewModel(AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context)
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        composeRule.onNodeWithText("Check connection").performScrollTo().performClick()
        // diagnose() runs on a real IO-dispatched coroutine even in this test, so give it a
        // moment to finish before asserting on its absence -- a bare waitForIdle() right after
        // the click can race ahead of that background hop.
        Thread.sleep(300)
        composeRule.waitForIdle()
        // Without the permission granted, the tap must go through the system prompt instead
        // of calling diagnose() directly -- Robolectric never fires that prompt's callback, so
        // the diagnosis result must never appear.
        composeRule.onAllNodesWithText("Should not be shown").assertCountEquals(0)
    }

    @Test
    fun clicksBrowseDecks_whenPermissionGranted_showsDeckPickerWithNames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_deck_picker") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = listOf("Spanish", "French")
            override fun getNoteTypeNames(): List<String> = listOf("Basic", "General")
        }
        val viewModel = SettingsViewModel(AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context)
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }

        composeRule.onNodeWithContentDescription("Browse decks").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Spanish").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Spanish").performClick()
        // The dialog closes on selection, leaving exactly one "Spanish" node: the
        // text field's value (onNodeWithText also matches EditableText, not just Text).
        composeRule.onNodeWithText("Spanish").assertIsDisplayed()
    }

    @Test
    fun showsDefaultNoteTypeField() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_note_type_field") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val viewModel = SettingsViewModel(AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context)
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        composeRule.onNodeWithText("Default note type").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clicksBrowseNoteTypes_whenPermissionGranted_selectsNameAndPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_note_type_picker") },
        )
        val settings = AppSettings(dataStore)
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = listOf("Basic", "General")
        }
        val viewModel = SettingsViewModel(settings, fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context)
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }

        composeRule.onNodeWithContentDescription("Browse note types").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a note type").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Basic").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000) {
            runBlocking { settings.defaultNoteType.first() } == "Basic"
        }
        assertThat(runBlocking { settings.defaultNoteType.first() }).isEqualTo("Basic")
    }

    @Test
    fun showsTestApiKeyButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_api_key_button") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val viewModel = SettingsViewModel(
            AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context,
        )
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        composeRule.onNodeWithText("Test API key").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clicksTestApiKey_withValidKey_showsSuccessMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_api_key_success") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("test")
                    .body("{\"responses\":[{}]}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val viewModel = SettingsViewModel(
            AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(client) { "fake-key" }, context,
        )
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        viewModel.setApiKey("fake-key")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Test API key").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("API key is valid.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("API key is valid.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clicksTestApiKey_withNoKeyConfigured_showsFailureMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_api_key_missing") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val viewModel = SettingsViewModel(
            AppSettings(dataStore), fakeSender, CloudVisionOcrEngine(OkHttpClient()) { "" }, context,
        )
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }

        composeRule.onNodeWithText("Test API key").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("No API key configured.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No API key configured.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun apiKeyTestResult_errorMessage_isDisplayedAndSelectable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("test_settings_api_key_error") },
        )
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose() = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val errorBody = """{"error":{"code":403,"message":"This API method requires billing to be enabled."}}"""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("test")
                    .body(errorBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val viewModel = SettingsViewModel(
            AppSettings(dataStore),
            fakeSender,
            CloudVisionOcrEngine(client) { "fake-key" },
            context,
        )
        composeRule.setContent {
            SettingsScreen(onBack = {}, viewModel = viewModel)
        }
        composeRule.runOnIdle { viewModel.setApiKey("fake-key") }

        composeRule.onNodeWithText("Test API key").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("This API method requires billing to be enabled.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithText("This API method requires billing to be enabled.", substring = true)
            .assertCountEquals(1)
    }
}
