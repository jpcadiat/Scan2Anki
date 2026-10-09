package com.scan2anki.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.ankidroid.ANKI_READ_WRITE_PERMISSION
import com.scan2anki.ankidroid.AnkiDroidDiagnosis
import com.scan2anki.ankidroid.AnkiDroidSendResult
import com.scan2anki.ankidroid.AnkiDroidSender
import com.scan2anki.ankidroid.AnkiNote
import com.scan2anki.data.AppDatabase
import com.scan2anki.data.SessionRepository
import com.scan2anki.data.WordPairMapper
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import com.scan2anki.parse.ColumnParser
import com.scan2anki.parse.OcrCleanup
import com.scan2anki.settings.AppSettings
import com.scan2anki.vm.ReviewViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repo: SessionRepository
    private lateinit var context: Context
    private lateinit var settings: AppSettings

    private val emptyEngine = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult = OcrResult(emptyList(), OcrSource.ON_DEVICE)
    }

    private class FakeSender : AnkiDroidSender {
        var deckNamesResult: List<String> = emptyList()
        var noteTypeNamesResult: List<String> = emptyList()
        override fun diagnose(): AnkiDroidDiagnosis = AnkiDroidDiagnosis(
            installed = true,
            apiAvailable = true,
            message = "OK",
        )
        override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult =
            AnkiDroidSendResult.Added(added = notes.size, total = notes.size)
        override fun getDeckNames(): List<String> = deckNamesResult
        override fun getNoteTypeNames(): List<String> = noteTypeNamesResult
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric does not auto-grant this permission from the manifest; without this,
        // clicking "Send to AnkiDroid" would go through the runtime-permission-request launcher,
        // whose callback never fires under Robolectric, hanging the test instead of sending.
        Shadows.shadowOf(context as android.app.Application).grantPermissions(ANKI_READ_WRITE_PERMISSION)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SessionRepository(db.importSessionDao(), db.pageDao(), db.wordPairDao())
        settings = AppSettings(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                produceFile = { File(context.filesDir, "review_screen_test.preferences_pb") },
            ),
        )
        // Cloud OCR is gated on a stored API key; most existing tests here exercise the
        // "key configured" case, so seed one by default and override per-test where needed.
        runBlocking { settings.setApiKey("test-key") }
    }

    @Test
    fun showsDetectedPairs() {
        val sessionId = runBlocking { repo.createSession() }
        val pageId = runBlocking { repo.addPage(sessionId, "/tmp/p.jpg", 0) }
        runBlocking {
            val rows = listOf(
                ColumnParser.ParsedRow("cat", "gato"),
                ColumnParser.ParsedRow("dog", "perro"),
            )
            repo.replaceWordPairsForPage(
                sessionId, pageId,
                rows.mapIndexed { i, r -> WordPairMapper.fromRow(sessionId, pageId, i, r) },
            )
        }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("gato").assertIsDisplayed()
        composeRule.onNodeWithText("2 words").assertIsDisplayed()
        composeRule.onNodeWithText("Row").assertDoesNotExist()
        composeRule.onNodeWithText("Header").assertDoesNotExist()
    }

    @Test
    fun showsReRunOcrControlsWhenPagesExist() {
        val sessionId = runBlocking { repo.createSession() }
        runBlocking { repo.addPage(sessionId, "/tmp/p.jpg", 0) }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Page 1").assertIsDisplayed()
        // Per-page actions moved behind the thumbnail's menu instead of four permanent controls.
        composeRule.onNodeWithText("Re-run OCR (On-device)").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Page 1").performClick()
        composeRule.onNodeWithText("Re-run OCR (On-device)").assertIsDisplayed()
        composeRule.onNodeWithText("Re-run OCR (Cloud)").assertIsDisplayed()
    }

    @Test
    fun clickingReRunOnDevice_requestsEditorForPage() {
        val sessionId = runBlocking { repo.createSession() }
        val imageFile = File(context.cacheDir, "rerun_screen.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val pageId = runBlocking { repo.addPage(sessionId, imageFile.absolutePath, 0) }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        var requested: Pair<Long, Boolean>? = null
        composeRule.setContent {
            ReviewScreen(
                sessionId = sessionId,
                onBack = {},
                onRerunOcr = { id, useCloud -> requested = id to useCloud },
                onViewZones = {},
                viewModel = vm,
            )
        }
        composeRule.onNodeWithContentDescription("Page 1").performClick()
        composeRule.onNodeWithText("Re-run OCR (On-device)").performClick()
        composeRule.waitForIdle()
        assertThat(requested).isNotNull()
        assertThat(requested!!.first).isEqualTo(pageId)
        assertThat(requested!!.second).isFalse()
    }

    @Test
    fun clickingReRunCloud_requestsCloudEditorMode() {
        val sessionId = runBlocking { repo.createSession() }
        val imageFile = File(context.cacheDir, "rerun_cloud_screen.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val pageId = runBlocking { repo.addPage(sessionId, imageFile.absolutePath, 0) }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        var requested: Pair<Long, Boolean>? = null
        composeRule.setContent {
            ReviewScreen(
                sessionId = sessionId,
                onBack = {},
                onRerunOcr = { id, useCloud -> requested = id to useCloud },
                onViewZones = {},
                viewModel = vm,
            )
        }
        composeRule.onNodeWithContentDescription("Page 1").performClick()
        composeRule.onNodeWithText("Re-run OCR (Cloud)").performClick()
        composeRule.waitForIdle()
        assertThat(requested).isEqualTo(pageId to true)
    }

    @Test
    fun clickingView_invokesOnViewZonesForThatPage() {
        val sessionId = runBlocking { repo.createSession() }
        val imageFile = File(context.cacheDir, "overlay_screen.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val pageId = runBlocking { repo.addPage(sessionId, imageFile.absolutePath, 0) }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        var requested: Long? = null
        composeRule.setContent {
            ReviewScreen(
                sessionId = sessionId,
                onBack = {},
                onRerunOcr = { _, _ -> },
                onViewZones = { requested = it },
                viewModel = vm,
            )
        }
        composeRule.onNodeWithContentDescription("Page 1").performClick()
        composeRule.onNodeWithText("View detected lines").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            requested != null
        }
        assertThat(requested).isEqualTo(pageId)
    }

    @Test
    fun clickingDeletePage_removesItFromTheList() {
        val sessionId = runBlocking { repo.createSession() }
        runBlocking { repo.addPage(sessionId, "/tmp/p.jpg", 0) }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.onNodeWithContentDescription("Page 1").performClick()
        composeRule.onNodeWithText("Delete page 1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages.isEmpty()
        }
        composeRule.onNodeWithText("Page 1").assertDoesNotExist()
    }

    @Test
    fun cleanUpButton_opensDialogWithRuleOptions() {
        val sessionId = runBlocking { repo.createSession() }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.onNodeWithContentDescription("Clean up").performClick()
        composeRule.onNodeWithText("Clean up text").assertIsDisplayed()
        composeRule.onNodeWithText("Trim junk characters").assertIsDisplayed()
        composeRule.onNodeWithText("Strip trailing page number").assertExists()
        composeRule.onNodeWithText("Cut after separator").assertExists()
        composeRule.onNodeWithText("Fix casing").assertExists()
    }

    @Test
    fun cleanup_pageNumberRule_previewAndApply_updatesBackText() {
        val sessionId = runBlocking { repo.createSession() }
        val pageId = runBlocking { repo.addPage(sessionId, "/tmp/p.jpg", 0) }
        runBlocking {
            repo.replaceWordPairsForPage(
                sessionId, pageId,
                listOf(WordPairMapper.fromRow(sessionId, pageId, 0, ColumnParser.ParsedRow("cat", "gato 12"))),
            )
        }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Clean up").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("Clean up text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cleanup_pageNumber_checkbox").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("Apply (1)").fetchSemanticsNodes().isNotEmpty()
        }
        // Preview is now an in-place expansion rather than a separate wizard step.
        composeRule.onNodeWithText("Preview").performClick()
        composeRule.onNodeWithText("Review changes (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Back: gato 12 → gato").assertIsDisplayed()
        composeRule.onNodeWithText("Apply (1)").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pairs.firstOrNull()?.back == "gato"
        }
        composeRule.onNodeWithText("Clean up text").assertDoesNotExist()
    }

    @Test
    fun sendingSuccessfully_invokesOnSentSuccessfully() {
        val sessionId = runBlocking { repo.createSession() }
        val pageId = runBlocking { repo.addPage(sessionId, "/tmp/p.jpg", 0) }
        runBlocking {
            repo.replaceWordPairsForPage(
                sessionId, pageId,
                listOf(WordPairMapper.fromRow(sessionId, pageId, 0, ColumnParser.ParsedRow("cat", "gato"))),
            )
        }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        var sentCallbackInvoked = false
        composeRule.setContent {
            ReviewScreen(
                sessionId = sessionId,
                onBack = {},
                onRerunOcr = { _, _ -> },
                onViewZones = {},
                onSentSuccessfully = { sentCallbackInvoked = true },
                viewModel = vm,
            )
        }
        // vm.init()'s async load of the default deck name (queued on the Main dispatcher) would
        // clobber a deck name set before setContent, so it must be set after the screen has
        // rendered and gone idle, or sendToAnkiDroid() sees a blank deck name and errors instead.
        composeRule.waitForIdle()
        vm.setDeckName("Spanish")
        composeRule.waitForIdle()
        // The extended FAB merges its icon and label into one semantics node, so the label is
        // only addressable in the unmerged tree.
        composeRule.onNodeWithText("Send to AnkiDroid", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.waitForIdle()
            sentCallbackInvoked
        }
        assertThat(sentCallbackInvoked).isTrue()
    }

    @Test
    fun swipingARowAway_deletesItImmediatelyAndOffersUndo() {
        val sessionId = runBlocking { repo.createSession() }
        val pageId = runBlocking { repo.addPage(sessionId, "/tmp/p.jpg", 0) }
        runBlocking {
            repo.replaceWordPairsForPage(
                sessionId, pageId,
                listOf(WordPairMapper.fromRow(sessionId, pageId, 0, ColumnParser.ParsedRow("cat", "gato"))),
            )
        }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        val pairId = vm.uiState.value.pairs.first().id

        composeRule.onNodeWithTag("pair_row_$pairId").performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pairs.isEmpty()
        }

        // Deletion is instant, but recoverable -- that is the trade for having no confirm dialog.
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pairs.size == 1
        }
        val restored = vm.uiState.value.pairs.first()
        assertThat(restored.id).isEqualTo(pairId)
        assertThat(restored.front).isEqualTo("cat")
        assertThat(restored.back).isEqualTo("gato")
    }

    @Test
    fun cleanupScopeChips_appearOnlyForEnabledRules() {
        val sessionId = runBlocking { repo.createSession() }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.onNodeWithContentDescription("Clean up").performClick()
        composeRule.onNodeWithText("Clean up text").assertIsDisplayed()

        // Every rule starts disabled, so none of their Front/Back/Both chips should be rendered.
        composeRule.onNodeWithTag("cleanup_pageNumber_scope_BOTH").assertDoesNotExist()
        composeRule.onNodeWithTag("cleanup_trimJunk_scope_BOTH").assertDoesNotExist()

        composeRule.onNodeWithTag("cleanup_pageNumber_checkbox").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cleanup_pageNumber_scope_BOTH").assertExists()
        // Turning one rule on must not reveal another rule's chips.
        composeRule.onNodeWithTag("cleanup_trimJunk_scope_BOTH").assertDoesNotExist()
    }

    @Test
    fun cleanup_seedsDialogFromPersistedConfig() {
        val sessionId = runBlocking { repo.createSession() }
        runBlocking {
            settings.setCleanupConfig(
                OcrCleanup.CleanupConfig(
                    pageNumber = OcrCleanup.PageNumberRule(enabled = true, scope = OcrCleanup.ColumnScope.BACK),
                ),
            )
        }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Clean up").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("Clean up text").fetchSemanticsNodes().isNotEmpty()
        }
        // Proves initialConfig (loaded from settings before the dialog opened) actually reached
        // the dialog's controls: the checkbox is checked and the BACK scope chip is selected,
        // rather than the rule starting disabled with no scope chips shown.
        composeRule.onNodeWithTag("cleanup_pageNumber_scope_BACK").assertExists()
    }

    @Test
    fun cleanup_editInDialog_persistsToSettings() {
        val sessionId = runBlocking { repo.createSession() }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Clean up").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("Clean up text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cleanup_pageNumber_checkbox").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            runBlocking { settings.cleanupConfig.first() }.pageNumber.enabled
        }
        assertThat(runBlocking { settings.cleanupConfig.first() }.pageNumber.enabled).isTrue()
    }

    @Test
    fun cleanup_toggleRuleOnThenOff_persistsRevertedState() {
        val sessionId = runBlocking { repo.createSession() }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Clean up").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("Clean up text").fetchSemanticsNodes().isNotEmpty()
        }
        // Verify initial state: pageNumber rule should be disabled
        assertThat(runBlocking { settings.cleanupConfig.first() }.pageNumber.enabled).isFalse()

        // Toggle the pageNumber rule ON
        composeRule.onNodeWithTag("cleanup_pageNumber_checkbox").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            runBlocking { settings.cleanupConfig.first() }.pageNumber.enabled
        }
        assertThat(runBlocking { settings.cleanupConfig.first() }.pageNumber.enabled).isTrue()

        // Toggle the same rule OFF (reverting to initial disabled state)
        // This is the critical test: without the hasSeeded fix, the persisted value would stay
        // at true because config == initialConfig would prevent the onConfigChange callback.
        composeRule.onNodeWithTag("cleanup_pageNumber_checkbox").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            !runBlocking { settings.cleanupConfig.first() }.pageNumber.enabled
        }
        // Assert the persisted config reflects the reverted (off) state, not the stale (on) state
        assertThat(runBlocking { settings.cleanupConfig.first() }.pageNumber.enabled).isFalse()
    }

    @Test
    fun clicksBrowseDecks_showsDeckPickerAndSelectingOneSetsDeckName() {
        val sessionId = runBlocking { repo.createSession() }
        val sender = FakeSender().apply { deckNamesResult = listOf("Spanish", "French") }
        val vm = ReviewViewModel(repo, emptyEngine, sender, settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }

        composeRule.onNodeWithContentDescription("Browse decks").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Spanish").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("French").performClick()

        composeRule.onNodeWithText("Deck: French").assertIsDisplayed()
    }

    @Test
    fun noteTypeRow_showsEffectiveDefaultName() {
        val sessionId = runBlocking { repo.createSession() }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Type: General").assertIsDisplayed()
    }

    @Test
    fun clicksBrowseNoteTypes_showsNoteTypePickerAndSelectingOneUpdatesRow() {
        val sessionId = runBlocking { repo.createSession() }
        val sender = FakeSender().apply { noteTypeNamesResult = listOf("Basic", "General") }
        val vm = ReviewViewModel(repo, emptyEngine, sender, settings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }

        composeRule.onNodeWithContentDescription("Browse note types").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Select a note type").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Basic").assertIsDisplayed()
        composeRule.onNodeWithText("General").assertIsDisplayed()

        composeRule.onNodeWithText("Basic").performClick()

        composeRule.onNodeWithText("Type: Basic").assertIsDisplayed()
    }

    @Test
    fun reRunOcrCloud_hiddenWhenNoApiKeyConfigured() {
        val noKeySettings = AppSettings(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                produceFile = { File(context.filesDir, "review_screen_test_no_key.preferences_pb") },
            ),
        )
        val sessionId = runBlocking { repo.createSession() }
        runBlocking { repo.addPage(sessionId, "/tmp/p.jpg", 0) }
        val vm = ReviewViewModel(repo, emptyEngine, FakeSender(), noKeySettings, context)
        vm.init(sessionId)
        composeRule.setContent {
            ReviewScreen(sessionId = sessionId, onBack = {}, onRerunOcr = { _, _ -> }, onViewZones = {}, viewModel = vm)
        }
        composeRule.onNodeWithContentDescription("Page 1").performClick()
        composeRule.onNodeWithText("Re-run OCR (On-device)").assertIsDisplayed()
        composeRule.onNodeWithText("Re-run OCR (Cloud)").assertDoesNotExist()
    }
}
