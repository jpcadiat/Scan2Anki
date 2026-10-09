package com.scan2anki.vm

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.ankidroid.AnkiDroidDiagnosis
import com.scan2anki.ankidroid.AnkiDroidSendResult
import com.scan2anki.ankidroid.AnkiDroidSender
import com.scan2anki.ankidroid.AnkiNote
import com.scan2anki.data.AppDatabase
import com.scan2anki.data.SessionRepository
import com.scan2anki.data.WordPair
import com.scan2anki.data.WordPairMapper
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrLine
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import com.scan2anki.R
import com.scan2anki.parse.ColumnParser
import com.scan2anki.parse.OcrCleanup
import com.scan2anki.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: SessionRepository
    private lateinit var context: Context
    private lateinit var settings: AppSettings

    private val fakeOnDevice = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult = OcrResult(
            lines = listOf(
                OcrLine("perro", 0.05f, 0.10f, 0.30f, 0.13f),
                OcrLine("dog", 0.55f, 0.10f, 0.80f, 0.13f),
            ),
            source = OcrSource.ON_DEVICE,
        )
    }

    private class FakeSender : AnkiDroidSender {
        var available = true
        var addedOverride: Int? = null
        var deckNamesResult: List<String> = emptyList()
        var noteTypeNamesResult: List<String> = emptyList()
        val sent = mutableListOf<AnkiNote>()
        val decks = mutableListOf<String>()
        val noteTypes = mutableListOf<String>()
        override fun diagnose(): AnkiDroidDiagnosis = AnkiDroidDiagnosis(
            installed = available,
            apiAvailable = available,
            message = if (available) "OK" else "Unavailable",
        )
        override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult {
            if (!available) return AnkiDroidSendResult.Failed("AnkiDroid API unavailable")
            sent.addAll(notes)
            decks.add(deckName)
            noteTypes.add(noteTypeName)
            return AnkiDroidSendResult.Added(added = addedOverride ?: notes.size, total = notes.size)
        }
        override fun getDeckNames(): List<String> = deckNamesResult
        override fun getNoteTypeNames(): List<String> = noteTypeNamesResult
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.testDispatcher)
            .build()
        repo = SessionRepository(db.importSessionDao(), db.pageDao(), db.wordPairDao())
        settings = AppSettings(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                produceFile = { File(context.filesDir, "review_test.preferences_pb") },
            ),
        )
    }

    private suspend fun seedPairs(sessionId: Long): List<WordPair> {
        val pageId = repo.addPage(sessionId, "/tmp/p.jpg", 0)
        val rows = listOf(
            ColumnParser.ParsedRow("cat", "gato"),
            ColumnParser.ParsedRow("dog", "perro"),
        )
        val pairs = rows.mapIndexed { i, r -> WordPairMapper.fromRow(sessionId, pageId, i, r) }
        repo.replaceWordPairsForPage(sessionId, pageId, pairs)
        return pairs
    }

    @Test
    fun init_loadsPairs() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size ==  2 }
        assertThat(vm.uiState.value.pairs).hasSize(2)
    }

    @Test
    fun init_ordersPairsByPageThenWithinPageOrderDespiteOverlappingLocalOrder() = runTest {
        val sessionId = repo.createSession()
        val page2Id = repo.addPage(sessionId, "/tmp/p2.jpg", 1)
        val page1Id = repo.addPage(sessionId, "/tmp/p1.jpg", 0)
        // Each page's pairs are stored with order restarting at 0, mirroring real OCR import.
        repo.replaceWordPairsForPage(
            sessionId,
            page2Id,
            listOf(ColumnParser.ParsedRow("bird", "pajaro"), ColumnParser.ParsedRow("fish", "pez"))
                .mapIndexed { i, r -> WordPairMapper.fromRow(sessionId, page2Id, i, r) },
        )
        repo.replaceWordPairsForPage(
            sessionId,
            page1Id,
            listOf(ColumnParser.ParsedRow("cat", "gato"), ColumnParser.ParsedRow("dog", "perro"))
                .mapIndexed { i, r -> WordPairMapper.fromRow(sessionId, page1Id, i, r) },
        )
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 4 }
        val fronts = vm.uiState.value.pairs.map { it.front }
        assertThat(fronts).containsExactly("cat", "dog", "bird", "fish").inOrder()
    }

    @Test
    fun updateFront_reflectsInStateImmediately_beforeDbWriteCompletes() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }
        val pairId = vm.uiState.value.pairs.first { it.front == "cat" }.id

        vm.updateFront(pairId, "catt")

        // Must be visible right away, without advancing the test dispatcher -- otherwise
        // the persisted-to-DB round trip is what makes a keystroke visible, and any
        // recomposition in between briefly reverts the field to the stale value.
        assertThat(vm.uiState.value.pairs.first { it.id == pairId }.front).isEqualTo("catt")
    }

    @Test
    fun updateBack_reflectsInStateImmediately_beforeDbWriteCompletes() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }
        val pairId = vm.uiState.value.pairs.first { it.front == "cat" }.id

        vm.updateBack(pairId, "gatoo")

        assertThat(vm.uiState.value.pairs.first { it.id == pairId }.back).isEqualTo("gatoo")
    }

    @Test
    fun swapColumns_exchangesFrontAndBack() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size ==  2 }
        vm.swapColumns()
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs[0].front == "gato" }
        val pairs = vm.uiState.value.pairs
        assertThat(pairs[0].front).isEqualTo("gato")
        assertThat(pairs[0].back).isEqualTo("cat")
    }

    @Test
    fun deletePair_removesRow() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size ==  2 }
        vm.deletePair(vm.uiState.value.pairs[0].id)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size ==  1 }
        assertThat(vm.uiState.value.pairs).hasSize(1)
    }

    @Test
    fun deletePage_removesPageAndItsWordPairs() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.size == 1 }
        val pageId = vm.uiState.value.pages[0].id

        vm.deletePage(pageId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.isEmpty() }

        assertThat(vm.uiState.value.pages).isEmpty()
        assertThat(vm.uiState.value.pairs).isEmpty()
    }

    @Test
    fun sendToAnkiDroid_sendsAllCardsInOneBatchAndMarksImported() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val sender = FakeSender()
        val vm = ReviewViewModel(repo, fakeOnDevice, sender, settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }
        vm.setDeckName("Spanish")
        vm.sendToAnkiDroid()
        mainDispatcherRule.awaitUntil { vm.uiState.value.sendResult != null }
        assertThat(sender.decks).containsExactly("Spanish")
        assertThat(sender.sent).hasSize(2)
        assertThat(sender.sent[0]).isEqualTo(AnkiNote("cat", "gato"))
        assertThat(sender.sent[1]).isEqualTo(AnkiNote("dog", "perro"))
        assertThat(vm.uiState.value.sendResult).isEqualTo(SendResult.SENT)
        val session = repo.session(sessionId).first()
        assertThat(session!!.status).isEqualTo("IMPORTED")
    }

    @Test
    fun sendToAnkiDroid_whenUnavailable_reportsError() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val sender = FakeSender().apply { available = false }
        val vm = ReviewViewModel(repo, fakeOnDevice, sender, settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }
        vm.setDeckName("Spanish")
        vm.sendToAnkiDroid()
        mainDispatcherRule.awaitUntil { vm.uiState.value.error != null }
        assertThat(vm.uiState.value.error).isEqualTo("AnkiDroid API unavailable")
        assertThat(vm.uiState.value.sendResult).isNull()
        assertThat(sender.sent).isEmpty()
        val session = repo.session(sessionId).first()
        assertThat(session!!.status).isEqualTo("IN_PROGRESS")
    }

    @Test
    fun sendToAnkiDroid_partialFailure_reportsWhichWereAdded() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val sender = FakeSender().apply { addedOverride = 1 }
        val vm = ReviewViewModel(repo, fakeOnDevice, sender, settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }
        vm.setDeckName("Spanish")
        vm.sendToAnkiDroid()
        mainDispatcherRule.awaitUntil { vm.uiState.value.error != null }
        assertThat(vm.uiState.value.error).contains("Added 1 of 2")
        assertThat(vm.uiState.value.sendResult).isNull()
        val session = repo.session(sessionId).first()
        assertThat(session!!.status).isEqualTo("IMPORTED")
    }

    @Test
    fun sendToAnkiDroid_withNoValidPairs_setsErrorAndDoesNotSend() = runTest {
        val sessionId = repo.createSession()
        val sender = FakeSender()
        val vm = ReviewViewModel(repo, fakeOnDevice, sender, settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId && vm.uiState.value.pairs.isEmpty() }
        vm.setDeckName("Spanish")
        vm.sendToAnkiDroid()
        assertThat(vm.uiState.value.error).isEqualTo("No valid word pairs to export")
        assertThat(sender.sent).isEmpty()
        val session = repo.session(sessionId).first()
        assertThat(session!!.status).isEqualTo("IN_PROGRESS")
    }

    @Test
    fun applyCleanup_updatesOnlyChangedPairsInRepo() = runTest {
        val sessionId = repo.createSession()
        val pageId = repo.addPage(sessionId, "/tmp/p.jpg", 0)
        val rows = listOf(
            ColumnParser.ParsedRow("cat", "gato 12"),
            ColumnParser.ParsedRow("dog", "perro"),
        )
        val pairs = rows.mapIndexed { i, r -> WordPairMapper.fromRow(sessionId, pageId, i, r) }
        repo.replaceWordPairsForPage(sessionId, pageId, pairs)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }

        val config = OcrCleanup.CleanupConfig(
            pageNumber = OcrCleanup.PageNumberRule(enabled = true, scope = OcrCleanup.ColumnScope.BACK),
        )
        vm.applyCleanup(config)
        mainDispatcherRule.awaitUntil {
            vm.uiState.value.pairs.first { it.front == "cat" }.back == "gato"
        }

        val updated = vm.uiState.value.pairs
        assertThat(updated.first { it.front == "cat" }.back).isEqualTo("gato")
        assertThat(updated.first { it.front == "dog" }.back).isEqualTo("perro")
    }

    @Test
    fun previewCleanup_returnsOnlyChangedPairs() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }

        val config = OcrCleanup.CleanupConfig(
            casing = OcrCleanup.CasingRule(
                enabled = true,
                scope = OcrCleanup.ColumnScope.FRONT,
                mode = OcrCleanup.CasingMode.LOWERCASE,
            ),
        )
        // "cat"/"dog" fronts are already lowercase; nothing should change.
        val preview = vm.previewCleanup(config)
        assertThat(preview).isEmpty()
    }

    @Test
    fun init_loadsPersistedCleanupConfig() = runTest {
        val sessionId = repo.createSession()
        val persisted = OcrCleanup.CleanupConfig(
            casing = OcrCleanup.CasingRule(
                enabled = true,
                scope = OcrCleanup.ColumnScope.BACK,
                mode = OcrCleanup.CasingMode.LOWERCASE,
            ),
        )
        settings.setCleanupConfig(persisted)

        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.cleanupConfig == persisted }

        assertThat(vm.uiState.value.cleanupConfig).isEqualTo(persisted)
    }

    @Test
    fun setCleanupConfig_updatesStateImmediatelyAndPersists() = runTest {
        val sessionId = repo.createSession()
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId }

        val config = OcrCleanup.CleanupConfig(
            trimJunk = OcrCleanup.TrimJunkRule(
                enabled = true,
                scope = OcrCleanup.ColumnScope.FRONT,
                extraChars = "#~",
            ),
        )
        vm.setCleanupConfig(config)

        // Reflected synchronously, before the DataStore write completes -- same guarantee
        // updateFront/updateBack already give the UI for DB writes.
        assertThat(vm.uiState.value.cleanupConfig).isEqualTo(config)

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        assertThat(settings.cleanupConfig.first()).isEqualTo(config)
    }

    @Test
    fun openDeckPicker_loadsDeckNamesAndUpdatesState() = runTest {
        val sessionId = repo.createSession()
        val sender = FakeSender().apply { deckNamesResult = listOf("Spanish", "French") }
        val vm = ReviewViewModel(repo, fakeOnDevice, sender, settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId }

        vm.openDeckPicker()

        assertThat(vm.uiState.value.showDeckPicker).isTrue()
        assertThat(vm.uiState.value.isLoadingDeckNames).isTrue()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingDeckNames }
        assertThat(vm.uiState.value.deckNames).containsExactly("Spanish", "French")
    }

    @Test
    fun dismissDeckPicker_hidesDialog() = runTest {
        val sessionId = repo.createSession()
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId }
        vm.openDeckPicker()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingDeckNames }

        vm.dismissDeckPicker()

        assertThat(vm.uiState.value.showDeckPicker).isFalse()
    }

    @Test
    fun init_cloudOcrAvailable_falseWhenNoApiKeyConfigured() = runTest {
        val sessionId = repo.createSession()
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId }

        assertThat(vm.uiState.value.cloudOcrAvailable).isFalse()
    }

    @Test
    fun init_cloudOcrAvailable_trueWhenApiKeyConfigured() = runTest {
        settings.setApiKey("test-key")
        val sessionId = repo.createSession()
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)

        mainDispatcherRule.awaitUntil { vm.uiState.value.cloudOcrAvailable }

        assertThat(vm.uiState.value.cloudOcrAvailable).isTrue()
    }

    @Test
    fun init_loadsPersistedNoteType() = runTest {
        val sessionId = repo.createSession()
        settings.setDefaultNoteType("Basic")

        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)

        mainDispatcherRule.awaitUntil { vm.uiState.value.noteTypeName == "Basic" }
        assertThat(vm.uiState.value.noteTypeName).isEqualTo("Basic")
    }

    @Test
    fun init_blankNoteTypePreference_exposesLocalizedDefault() = runTest {
        val sessionId = repo.createSession()
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)

        mainDispatcherRule.awaitUntil {
            vm.uiState.value.noteTypeName == context.getString(R.string.default_note_type_name)
        }

        assertThat(vm.uiState.value.noteTypeName)
            .isEqualTo(context.getString(R.string.default_note_type_name))
    }

    @Test
    fun setNoteType_updatesStateImmediatelyAndPersists() = runTest {
        val sessionId = repo.createSession()
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId }

        vm.setNoteType("Basic")

        assertThat(vm.uiState.value.noteTypeName).isEqualTo("Basic")

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        assertThat(settings.defaultNoteType.first()).isEqualTo("Basic")
    }

    @Test
    fun openNoteTypePicker_loadsNoteTypeNamesAndUpdatesState() = runTest {
        val sessionId = repo.createSession()
        val sender = FakeSender().apply { noteTypeNamesResult = listOf("Basic", "General") }
        val vm = ReviewViewModel(repo, fakeOnDevice, sender, settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId }

        vm.openNoteTypePicker()

        assertThat(vm.uiState.value.showNoteTypePicker).isTrue()
        assertThat(vm.uiState.value.isLoadingNoteTypeNames).isTrue()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingNoteTypeNames }
        assertThat(vm.uiState.value.noteTypeNames).containsExactly("Basic", "General")
    }

    @Test
    fun dismissNoteTypePicker_hidesDialog() = runTest {
        val sessionId = repo.createSession()
        val vm = ReviewViewModel(repo, fakeOnDevice, FakeSender(), settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId == sessionId }
        vm.openNoteTypePicker()
        mainDispatcherRule.awaitUntil { !vm.uiState.value.isLoadingNoteTypeNames }

        vm.dismissNoteTypePicker()

        assertThat(vm.uiState.value.showNoteTypePicker).isFalse()
    }

    @Test
    fun sendToAnkiDroid_sendsSelectedNoteTypeName() = runTest {
        val sessionId = repo.createSession()
        seedPairs(sessionId)
        val sender = FakeSender()
        val vm = ReviewViewModel(repo, fakeOnDevice, sender, settings, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pairs.size == 2 }
        vm.setDeckName("Spanish")
        vm.setNoteType("Basic")

        vm.sendToAnkiDroid()

        mainDispatcherRule.awaitUntil { vm.uiState.value.sendResult != null }
        assertThat(sender.noteTypes).containsExactly("Basic")
        assertThat(sender.sent).hasSize(2)
    }

}
