package com.scan2anki.vm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.ankidroid.AnkiDroidDiagnosis
import com.scan2anki.ankidroid.AnkiDroidSendResult
import com.scan2anki.ankidroid.AnkiDroidSender
import com.scan2anki.ankidroid.AnkiNote
import com.scan2anki.ocr.CloudVisionOcrEngine
import com.scan2anki.settings.AgeCheck
import com.scan2anki.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Year

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var settings: AppSettings
    private lateinit var context: Context
    private lateinit var defaultFakeCloudOcr: CloudVisionOcrEngine
    private val defaultFakeSender = object : AnkiDroidSender {
        override fun diagnose(): AnkiDroidDiagnosis = AnkiDroidDiagnosis(
            installed = true,
            apiAvailable = true,
            message = "Connection to AnkiDroid works.",
        )
        override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
            AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
        override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
    }

    private fun cloudOcrReturning(code: Int, body: String): CloudVisionOcrEngine {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return CloudVisionOcrEngine(client) { "fake-key" }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        settings = AppSettings(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                produceFile = { File(context.filesDir, "settings_vm_test.preferences_pb") },
            ),
        )
        defaultFakeCloudOcr = cloudOcrReturning(200, "{\"responses\":[{}]}")
    }

    @Test
    fun testAnkiDroid_updatesDiagnosisResult() = runTest {
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose(): AnkiDroidDiagnosis = AnkiDroidDiagnosis(
                installed = true,
                apiAvailable = true,
                message = "Connection to AnkiDroid works.",
            )
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val vm = SettingsViewModel(settings, fakeSender, defaultFakeCloudOcr, context)
        vm.testAnkiDroid()
        mainDispatcherRule.awaitUntil { vm.uiState.value.ankiDroidTestResult != null }
        val result = vm.uiState.value.ankiDroidTestResult
        assertThat(result?.ok).isTrue()
        assertThat(result?.message).isEqualTo("Connection to AnkiDroid works.")
        assertThat(vm.uiState.value.isTestingAnkiDroid).isFalse()
    }

    @Test
    fun testAnkiDroid_onException_reportsFailureAndResetsTesting() = runTest {
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose(): AnkiDroidDiagnosis =
                throw IllegalStateException("boom")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = emptyList()
        }
        val vm = SettingsViewModel(settings, fakeSender, defaultFakeCloudOcr, context)
        vm.testAnkiDroid()
        assertThat(vm.uiState.value.isTestingAnkiDroid).isTrue()
        mainDispatcherRule.awaitUntil { vm.uiState.value.ankiDroidTestResult != null }
        val result = vm.uiState.value.ankiDroidTestResult
        assertThat(result?.ok).isFalse()
        assertThat(result?.message).isEqualTo("Test failed: boom")
        assertThat(vm.uiState.value.isTestingAnkiDroid).isFalse()
    }

    @Test
    fun setters_updateUiState() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()
        vm.setApiKey("key")
        vm.setDefaultDeckName("Spanish")
        vm.setOcrScript("chinese")
        mainDispatcherRule.awaitUntil {
            vm.uiState.value.apiKey == "key" &&
                vm.uiState.value.defaultDeckName == "Spanish" &&
                vm.uiState.value.ocrScript == "chinese"
        }
        assertThat(vm.uiState.value.apiKey).isEqualTo("key")
        assertThat(vm.uiState.value.defaultDeckName).isEqualTo("Spanish")
        assertThat(vm.uiState.value.ocrScript).isEqualTo("chinese")
    }

    @Test
    fun init_loadsPersistedSettings() = runTest {
        settings.setApiKey("persisted-key")
        settings.setDefaultDeckName("Deutsch")
        settings.setOcrScript("cyrillic")
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()
        mainDispatcherRule.awaitUntil { vm.uiState.value.apiKey == "persisted-key" }
        assertThat(vm.uiState.value.apiKey).isEqualTo("persisted-key")
        assertThat(vm.uiState.value.defaultDeckName).isEqualTo("Deutsch")
        assertThat(vm.uiState.value.ocrScript).isEqualTo("cyrillic")
    }

    @Test
    fun openDeckPicker_loadsDeckNamesAndUpdatesState() = runTest {
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose(): AnkiDroidDiagnosis = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = listOf("Spanish", "French")
            override fun getNoteTypeNames(): List<String> = listOf("Basic", "General")
        }
        val vm = SettingsViewModel(settings, fakeSender, defaultFakeCloudOcr, context)

        vm.openDeckPicker()

        assertThat(vm.uiState.value.showDeckPicker).isTrue()
        assertThat(vm.uiState.value.isLoadingDeckNames).isTrue()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingDeckNames }
        assertThat(vm.uiState.value.deckNames).containsExactly("Spanish", "French")
    }

    @Test
    fun dismissDeckPicker_hidesDialog() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.openDeckPicker()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingDeckNames }

        vm.dismissDeckPicker()

        assertThat(vm.uiState.value.showDeckPicker).isFalse()
    }

    @Test
    fun testApiKey_success_updatesResultAsOk() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.setApiKey("real-key")
        mainDispatcherRule.awaitUntil { vm.uiState.value.apiKey == "real-key" }

        vm.testApiKey()

        mainDispatcherRule.awaitUntil { vm.uiState.value.apiKeyTestResult != null }
        val result = vm.uiState.value.apiKeyTestResult
        assertThat(result?.ok).isTrue()
        assertThat(vm.uiState.value.isTestingApiKey).isFalse()
    }

    @Test
    fun testApiKey_invalidKey_updatesResultAsFailureWithMessage() = runTest {
        val body = """{"responses":[{"error":{"code":403,"message":"API key not valid."}}]}"""
        val vm = SettingsViewModel(settings, defaultFakeSender, cloudOcrReturning(200, body), context)
        vm.setApiKey("bad-key")
        mainDispatcherRule.awaitUntil { vm.uiState.value.apiKey == "bad-key" }

        vm.testApiKey()

        mainDispatcherRule.awaitUntil { vm.uiState.value.apiKeyTestResult != null }
        val result = vm.uiState.value.apiKeyTestResult
        assertThat(result?.ok).isFalse()
        assertThat(result?.message).contains("API key not valid")
        assertThat(vm.uiState.value.isTestingApiKey).isFalse()
    }

    @Test
    fun testApiKey_noKeyConfigured_failsWithoutNetworkCall() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        // apiKey starts blank; no vm.setApiKey() call.

        vm.testApiKey()

        val result = vm.uiState.value.apiKeyTestResult
        assertThat(result?.ok).isFalse()
        assertThat(vm.uiState.value.isTestingApiKey).isFalse()
    }

    @Test
    fun init_loadsPersistedNoteType() = runTest {
        settings.setDefaultNoteType("General")

        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()

        mainDispatcherRule.awaitUntil { vm.uiState.value.defaultNoteType == "General" }
        assertThat(vm.uiState.value.defaultNoteType).isEqualTo("General")
    }

    @Test
    fun init_blankNoteTypePreference_staysBlank() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.defaultNoteType).isEmpty()
    }

    @Test
    fun setDefaultNoteType_updatesStateAndPersists() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()

        vm.setDefaultNoteType("Basic")

        mainDispatcherRule.awaitUntil { vm.uiState.value.defaultNoteType == "Basic" }

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        assertThat(settings.defaultNoteType.first()).isEqualTo("Basic")
    }

    @Test
    fun openNoteTypePicker_loadsNoteTypeNamesAndUpdatesState() = runTest {
        val fakeSender = object : AnkiDroidSender {
            override fun diagnose(): AnkiDroidDiagnosis = AnkiDroidDiagnosis(true, true, "OK")
            override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
                AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
            override fun getDeckNames(): List<String> = emptyList()
            override fun getNoteTypeNames(): List<String> = listOf("Basic", "General")
        }
        val vm = SettingsViewModel(settings, fakeSender, defaultFakeCloudOcr, context)

        vm.openNoteTypePicker()

        assertThat(vm.uiState.value.showNoteTypePicker).isTrue()
        assertThat(vm.uiState.value.isLoadingNoteTypeNames).isTrue()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingNoteTypeNames }
        assertThat(vm.uiState.value.noteTypeNames).containsExactly("Basic", "General")
    }

    @Test
    fun dismissNoteTypePicker_hidesDialog() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.openNoteTypePicker()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingNoteTypeNames }

        vm.dismissNoteTypePicker()

        assertThat(vm.uiState.value.showNoteTypePicker).isFalse()
    }

    @Test
    fun init_loadsPersistedAgeCheck() = runTest {
        settings.setAgeCheck(AgeCheck.UNDER_13)
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()
        mainDispatcherRule.awaitUntil { vm.uiState.value.ageCheck == AgeCheck.UNDER_13 }
        assertThat(vm.uiState.value.ageCheck).isEqualTo(AgeCheck.UNDER_13)
    }

    @Test
    fun submitBirthYear_adult_storesAdultOrTeenAndClosesDialog() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()
        vm.openAgeDialog()
        assertThat(vm.uiState.value.showAgeDialog).isTrue()

        vm.submitBirthYear(Year.now().value - 30)

        mainDispatcherRule.awaitUntil { vm.uiState.value.ageCheck == AgeCheck.ADULT_OR_TEEN }
        assertThat(vm.uiState.value.showAgeDialog).isFalse()
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.ADULT_OR_TEEN)
    }

    @Test
    fun submitBirthYear_child_storesUnder13() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()
        vm.openAgeDialog()

        vm.submitBirthYear(Year.now().value - 12)

        mainDispatcherRule.awaitUntil { vm.uiState.value.ageCheck == AgeCheck.UNDER_13 }
        assertThat(vm.uiState.value.showAgeDialog).isFalse()
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.UNDER_13)
    }

    @Test
    fun submitBirthYear_invalidYear_isIgnored() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()
        vm.openAgeDialog()

        vm.submitBirthYear(Year.now().value + 5)
        mainDispatcherRule.awaitUntil { true }

        assertThat(vm.uiState.value.showAgeDialog).isTrue()
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.UNKNOWN)
    }

    @Test
    fun dismissAgeDialog_leavesAgeUnknown() = runTest {
        val vm = SettingsViewModel(settings, defaultFakeSender, defaultFakeCloudOcr, context)
        vm.init()
        vm.openAgeDialog()

        vm.dismissAgeDialog()
        mainDispatcherRule.awaitUntil { true }

        assertThat(vm.uiState.value.showAgeDialog).isFalse()
        assertThat(settings.ageCheck.first()).isEqualTo(AgeCheck.UNKNOWN)
    }
}
