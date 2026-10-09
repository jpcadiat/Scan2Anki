package com.scan2anki.vm

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.data.AppDatabase
import com.scan2anki.data.SessionRepository
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrLine
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import com.scan2anki.parse.ColumnParser.ZoneRect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ZoneEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: SessionRepository
    private lateinit var context: Context

    private val fakeEngine = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult = OcrResult(
            lines = listOf(
                OcrLine("Chapter 1", 0.05f, 0.10f, 0.25f, 0.13f),
                OcrLine("cat", 0.05f, 0.18f, 0.30f, 0.21f),
                OcrLine("dog", 0.05f, 0.26f, 0.30f, 0.29f),
                OcrLine("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                OcrLine("perro", 0.55f, 0.18f, 0.80f, 0.21f),
            ),
            source = OcrSource.ON_DEVICE,
        )
    }

    private val fakeCloud = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult = OcrResult(
            lines = listOf(
                OcrLine("gato", 0.05f, 0.10f, 0.30f, 0.13f),
                OcrLine("cat", 0.55f, 0.10f, 0.80f, 0.13f),
            ),
            source = OcrSource.CLOUD,
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.testDispatcher)
            .build()
        repo = SessionRepository(db.importSessionDao(), db.pageDao(), db.wordPairDao())
    }

    private suspend fun addPage(sessionId: Long, name: String): Long {
        val imageFile = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return repo.addPage(sessionId, imageFile.absolutePath, 0)
    }

    @Test
    fun init_loadsCurrentPageWithAutoDetectedSplit() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_vm.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        val state = vm.uiState.value
        assertThat(state.pages).hasSize(1)
        assertThat(state.pages[0].lines).hasSize(5)
        assertThat(state.pages[0].layout.splitX).isEqualTo(0.30f)
        assertThat(state.preview).hasSize(3)
        assertThat(state.preview[0].front).isEqualTo("Chapter 1")
        assertThat(state.preview[0].back).isEqualTo("gato")
    }

    @Test
    fun addIgnoreZone_excludesTitleAndFixesPairing() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_vm2.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.addIgnoreZone(ZoneRect(left = 0.0f, top = 0.08f, right = 0.4f, bottom = 0.16f))
        val preview = vm.uiState.value.preview
        assertThat(preview).hasSize(2)
        assertThat(preview[0].front).isEqualTo("cat")
        assertThat(preview[0].back).isEqualTo("gato")
        assertThat(preview[1].front).isEqualTo("dog")
        assertThat(preview[1].back).isEqualTo("perro")
    }

    @Test
    fun setSplitX_updatesPreview() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_vm3.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.setSplitX(0.90f)
        val preview = vm.uiState.value.preview
        assertThat(preview).hasSize(5)
        assertThat(preview.all { it.isUnpaired }).isTrue()
    }

    @Test
    fun removeIgnoreZone_restoresLine() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_vm4.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.addIgnoreZone(ZoneRect(left = 0.0f, top = 0.08f, right = 0.4f, bottom = 0.16f))
        assertThat(vm.uiState.value.preview).hasSize(2)
        vm.removeIgnoreZone(0)
        assertThat(vm.uiState.value.preview).hasSize(3)
    }

    @Test
    fun done_replacesPairsForAllPagesAndMarksOcrDone() = runTest {
        val sessionId = repo.createSession()
        val page1 = addPage(sessionId, "zone_vm5.jpg")
        val page2 = addPage(sessionId, "zone_vm6.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.done()
        mainDispatcherRule.awaitUntil { vm.uiState.value.done }
        val pairs = repo.wordPairs(sessionId).first()
        assertThat(pairs).hasSize(6)
        assertThat(pairs.count { it.pageId == page1 }).isEqualTo(3)
        assertThat(pairs.count { it.pageId == page2 }).isEqualTo(3)
        val pages = repo.pages(sessionId).first()
        assertThat(pages.all { it.ocrState == "DONE" }).isTrue()
    }

    @Test
    fun ocrFailure_setsErrorAndRetryRecovers() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_retry.jpg")
        var calls = 0
        val flaky = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): OcrResult {
                calls++
                if (calls == 1) throw java.io.IOException("Engine error")
                return OcrResult(
                    lines = listOf(
                        OcrLine("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                        OcrLine("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                    ),
                    source = OcrSource.ON_DEVICE,
                )
            }
        }
        val vm = ZoneEditorViewModel(repo, flaky, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.error != null }
        assertThat(vm.uiState.value.isProcessing).isFalse()
        assertThat(vm.uiState.value.pages[0].isLoaded).isFalse()
        assertThat(calls).isEqualTo(1)
        vm.retryCurrentPage()
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages[0].isLoaded }
        assertThat(calls).isEqualTo(2)
        assertThat(vm.uiState.value.preview).hasSize(1)
    }

    @Test
    fun emptyOcrResult_pageIsLoadedAndDoneDoesNotRerunOcr() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_empty.jpg")
        var calls = 0
        val emptyEngine = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): OcrResult {
                calls++
                return OcrResult(emptyList(), OcrSource.ON_DEVICE)
            }
        }
        val vm = ZoneEditorViewModel(repo, emptyEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.isLoaded == true }
        assertThat(calls).isEqualTo(1)
        assertThat(vm.uiState.value.preview).isEmpty()
        vm.done()
        mainDispatcherRule.awaitUntil { vm.uiState.value.done }
        assertThat(calls).isEqualTo(1)
        val pairs = repo.wordPairs(sessionId).first()
        assertThat(pairs).isEmpty()
        val pages = repo.pages(sessionId).first()
        assertThat(pages[0].ocrState).isEqualTo("DONE")
    }

    @Test
    fun done_whenLaterPageFails_commitsNothing() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_tx1.jpg")
        addPage(sessionId, "zone_tx2.jpg")
        var calls = 0
        val flaky = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): OcrResult {
                calls++
                if (calls == 2) throw java.io.IOException("Engine error")
                return OcrResult(
                    lines = listOf(
                        OcrLine("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                        OcrLine("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                    ),
                    source = OcrSource.ON_DEVICE,
                )
            }
        }
        val vm = ZoneEditorViewModel(repo, flaky, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.isLoaded == true }
        vm.done()
        mainDispatcherRule.awaitUntil { vm.uiState.value.error != null }
        assertThat(vm.uiState.value.isProcessing).isFalse()
        assertThat(vm.uiState.value.done).isFalse()
        val pairs = repo.wordPairs(sessionId).first()
        assertThat(pairs).isEmpty()
        val pages = repo.pages(sessionId).first()
        assertThat(pages.all { it.ocrState == "PENDING" }).isTrue()
    }

    @Test
    fun deleteLine_removesLineFromVisibleLinesAndPreview() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_del.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.deleteLine(0)
        val state = vm.uiState.value
        assertThat(state.pages[0].visibleLines.map { it.text })
            .containsExactly("cat", "dog", "gato", "perro")
        assertThat(state.pages[0].deletedLines).containsExactly(0)
        assertThat(state.preview).hasSize(2)
        assertThat(state.preview[0].front).isEqualTo("cat")
        assertThat(state.preview[0].back).isEqualTo("gato")
        assertThat(state.preview[1].front).isEqualTo("dog")
        assertThat(state.preview[1].back).isEqualTo("perro")
    }

    @Test
    fun deleteLine_outOfRange_isIgnored() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_del2.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.deleteLine(99)
        assertThat(vm.uiState.value.pages[0].deletedLines).isEmpty()
    }

    @Test
    fun deleteLine_twice_dedupes() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_del3.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.deleteLine(0)
        vm.deleteLine(0)
        assertThat(vm.uiState.value.pages[0].deletedLines).containsExactly(0)
    }

    @Test
    fun updateIgnoreZone_replacesZoneAndUpdatesPreview() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_upd.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.addIgnoreZone(ZoneRect(0.0f, 0.08f, 0.4f, 0.16f))
        assertThat(vm.uiState.value.preview).hasSize(2)
        vm.updateIgnoreZone(0, ZoneRect(0.0f, 0.16f, 0.9f, 0.30f))
        val preview = vm.uiState.value.preview
        assertThat(preview).hasSize(1)
        assertThat(preview[0].front).isEqualTo("Chapter 1")
        assertThat(preview[0].back).isEqualTo("gato")
    }

    @Test
    fun addSplit_setsSplitAndUpdatesPreview() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_addsplit.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.removeSplit()
        assertThat(vm.uiState.value.pages[0].layout.splitX).isNull()
        assertThat(vm.uiState.value.preview).hasSize(5)
        vm.addSplit(0.40f)
        assertThat(vm.uiState.value.pages[0].layout.splitX).isEqualTo(0.40f)
        assertThat(vm.uiState.value.preview).hasSize(3)
    }

    @Test
    fun addSplit_clampsToUnitRange() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_clamp.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.removeSplit()
        vm.addSplit(1.5f)
        assertThat(vm.uiState.value.pages[0].layout.splitX).isEqualTo(1.0f)
    }

    @Test
    fun resetPage_restoresSplitClearsZonesAndDeletedLines() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_reset.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.deleteLine(0)
        vm.addIgnoreZone(ZoneRect(0.0f, 0.08f, 0.4f, 0.16f))
        vm.removeSplit()
        assertThat(vm.uiState.value.pages[0].deletedLines).containsExactly(0)
        assertThat(vm.uiState.value.pages[0].layout.ignoreZones).hasSize(1)
        assertThat(vm.uiState.value.pages[0].layout.splitX).isNull()
        vm.resetPage()
        val page = vm.uiState.value.pages[0]
        assertThat(page.deletedLines).isEmpty()
        assertThat(page.layout.ignoreZones).isEmpty()
        assertThat(page.layout.splitX).isEqualTo(0.30f)
        assertThat(vm.uiState.value.preview).hasSize(3)
    }

    @Test
    fun done_excludesDeletedLinesFromPairs() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_donedel.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.deleteLine(0)
        vm.done()
        mainDispatcherRule.awaitUntil { vm.uiState.value.done }
        val pairs = repo.wordPairs(sessionId).first()
        assertThat(pairs).hasSize(2)
        assertThat(pairs.map { it.front }).containsExactly("cat", "dog")
    }

    @Test
    fun init_withFocusedPage_setsCurrentPageToFocused() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_focus1.jpg")
        val focusedId = addPage(sessionId, "zone_focus2.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId, focusedPageId = focusedId)
        mainDispatcherRule.awaitUntil {
            vm.uiState.value.pages.firstOrNull { it.page.id == focusedId }?.lines?.isNotEmpty() == true
        }
        val state = vm.uiState.value
        assertThat(state.focusedPageId).isEqualTo(focusedId)
        assertThat(state.currentIndex).isEqualTo(state.pages.indexOfFirst { it.page.id == focusedId })
        assertThat(state.pages[state.currentIndex].lines).hasSize(5)
    }

    @Test
    fun init_withCloudEngine_runsCloudOcr() = runTest {
        val sessionId = repo.createSession()
        val pageId = addPage(sessionId, "zone_cloud.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId, focusedPageId = pageId, useCloud = true)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        val state = vm.uiState.value
        assertThat(state.useCloud).isTrue()
        assertThat(state.pages[0].lines.map { it.text }).containsExactly("gato", "cat")
        assertThat(state.preview).hasSize(1)
    }

    @Test
    fun done_withFocusedPage_appliesOnlyThatPage() = runTest {
        val sessionId = repo.createSession()
        addPage(sessionId, "zone_fdone1.jpg")
        val page2 = addPage(sessionId, "zone_fdone2.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId, focusedPageId = page2)
        mainDispatcherRule.awaitUntil {
            vm.uiState.value.pages.firstOrNull { it.page.id == page2 }?.lines?.isNotEmpty() == true
        }
        vm.done()
        mainDispatcherRule.awaitUntil { vm.uiState.value.done }
        val pairs = repo.wordPairs(sessionId).first()
        assertThat(pairs).hasSize(3)
        assertThat(pairs.all { it.pageId == page2 }).isTrue()
        val pages = repo.pages(sessionId).first()
        assertThat(pages.first { it.id == page2 }.ocrState).isEqualTo("DONE")
        assertThat(pages.filter { it.id != page2 }.all { it.ocrState == "PENDING" }).isTrue()
    }

    @Test
    fun done_withCloudFocused_marksDONE_CLOUD() = runTest {
        val sessionId = repo.createSession()
        val pageId = addPage(sessionId, "zone_fcldone.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId, focusedPageId = pageId, useCloud = true)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm.done()
        mainDispatcherRule.awaitUntil { vm.uiState.value.done }
        val pages = repo.pages(sessionId).first()
        assertThat(pages.first { it.id == pageId }.ocrState).isEqualTo("DONE_CLOUD")
    }

    @Test
    fun done_inRestoreModeWithoutUseCloud_preservesExistingDONE_CLOUDState() = runTest {
        val sessionId = repo.createSession()
        val pageId = addPage(sessionId, "zone_restore_provenance.jpg")
        val vm1 = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm1.init(sessionId, focusedPageId = pageId, useCloud = true)
        mainDispatcherRule.awaitUntil { vm1.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm1.done()
        mainDispatcherRule.awaitUntil { vm1.uiState.value.done }
        val afterFirstDone = repo.pages(sessionId).first()
        assertThat(afterFirstDone.first { it.id == pageId }.ocrState).isEqualTo("DONE_CLOUD")

        val vm2 = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm2.init(sessionId, focusedPageId = pageId, restoreFromCache = true)
        mainDispatcherRule.awaitUntil {
            vm2.uiState.value.pages.firstOrNull { it.page.id == pageId }?.isLoaded == true
        }
        vm2.done()
        mainDispatcherRule.awaitUntil { vm2.uiState.value.done }

        val afterRestoreDone = repo.pages(sessionId).first()
        assertThat(afterRestoreDone.first { it.id == pageId }.ocrState).isEqualTo("DONE_CLOUD")
    }

    @Test
    fun done_persistsZoneCacheThatCanBeRestoredWithoutRerunningOcr() = runTest {
        val sessionId = repo.createSession()
        val pageId = addPage(sessionId, "zone_restore.jpg")
        var onDeviceCalls = 0
        val countingEngine = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): OcrResult {
                onDeviceCalls++
                return fakeEngine.recognize(bitmap)
            }
        }
        val vm1 = ZoneEditorViewModel(repo, countingEngine, fakeCloud, context)
        vm1.init(sessionId)
        mainDispatcherRule.awaitUntil { vm1.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm1.addIgnoreZone(ZoneRect(left = 0.0f, top = 0.08f, right = 0.4f, bottom = 0.16f))
        vm1.deleteLine(1)
        vm1.done()
        mainDispatcherRule.awaitUntil { vm1.uiState.value.done }
        assertThat(onDeviceCalls).isEqualTo(1)

        val vm2 = ZoneEditorViewModel(repo, countingEngine, fakeCloud, context)
        vm2.init(sessionId, focusedPageId = pageId, restoreFromCache = true)
        mainDispatcherRule.awaitUntil {
            vm2.uiState.value.pages.firstOrNull { it.page.id == pageId }?.isLoaded == true
        }

        assertThat(onDeviceCalls).isEqualTo(1)
        val restored = vm2.uiState.value.pages.first { it.page.id == pageId }
        assertThat(restored.lines).hasSize(5)
        assertThat(restored.layout.ignoreZones)
            .containsExactly(ZoneRect(left = 0.0f, top = 0.08f, right = 0.4f, bottom = 0.16f))
        assertThat(restored.deletedLines).containsExactly(1)
        assertThat(restored.layout.splitX).isEqualTo(0.30f)
    }

    @Test
    fun done_fromRestoredSession_persistsSecondGenerationCacheThatAlsoRestoresWithoutRerunningOcr() = runTest {
        val sessionId = repo.createSession()
        val pageId = addPage(sessionId, "zone_restore2.jpg")
        var onDeviceCalls = 0
        val countingEngine = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): OcrResult {
                onDeviceCalls++
                return fakeEngine.recognize(bitmap)
            }
        }

        val vm1 = ZoneEditorViewModel(repo, countingEngine, fakeCloud, context)
        vm1.init(sessionId)
        mainDispatcherRule.awaitUntil { vm1.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        vm1.done()
        mainDispatcherRule.awaitUntil { vm1.uiState.value.done }
        assertThat(onDeviceCalls).isEqualTo(1)

        val vm2 = ZoneEditorViewModel(repo, countingEngine, fakeCloud, context)
        vm2.init(sessionId, focusedPageId = pageId, restoreFromCache = true)
        mainDispatcherRule.awaitUntil {
            vm2.uiState.value.pages.firstOrNull { it.page.id == pageId }?.isLoaded == true
        }
        assertThat(onDeviceCalls).isEqualTo(1)

        val extraZone = ZoneRect(left = 0.55f, top = 0.08f, right = 0.9f, bottom = 0.16f)
        vm2.addIgnoreZone(extraZone)
        vm2.done()
        mainDispatcherRule.awaitUntil { vm2.uiState.value.done }
        assertThat(onDeviceCalls).isEqualTo(1)

        val vm3 = ZoneEditorViewModel(repo, countingEngine, fakeCloud, context)
        vm3.init(sessionId, focusedPageId = pageId, restoreFromCache = true)
        mainDispatcherRule.awaitUntil {
            vm3.uiState.value.pages.firstOrNull { it.page.id == pageId }?.isLoaded == true
        }

        assertThat(onDeviceCalls).isEqualTo(1)
        val restored = vm3.uiState.value.pages.first { it.page.id == pageId }
        assertThat(restored.layout.ignoreZones).contains(extraZone)
    }

    @Test
    fun init_restoreFromCacheWithNoCache_fallsBackToFreshOcr() = runTest {
        val sessionId = repo.createSession()
        val pageId = addPage(sessionId, "zone_restore_nocache.jpg")
        val vm = ZoneEditorViewModel(repo, fakeEngine, fakeCloud, context)
        vm.init(sessionId, focusedPageId = pageId, restoreFromCache = true)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true }
        val state = vm.uiState.value
        assertThat(state.restoreFromCache).isTrue()
        assertThat(state.pages[0].lines).hasSize(5)
        assertThat(state.pages[0].layout.splitX).isEqualTo(0.30f)
    }
}
