package com.scan2anki.vm

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.data.AppDatabase
import com.scan2anki.data.SessionRepository
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrLine
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: SessionRepository
    private lateinit var db: AppDatabase
    private lateinit var context: Context

    private val fakeOcr = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult = OcrResult(
            lines = listOf(
                OcrLine("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                OcrLine("gato", 0.55f, 0.10f, 0.80f, 0.13f),
            ),
            source = OcrSource.ON_DEVICE,
        )
    }

    private val failingOcr = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult {
            throw RuntimeException("Engine error")
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SessionRepository(db.importSessionDao(), db.pageDao(), db.wordPairDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addPageFromUri_runsOcrAndSavesPairs() = runTest {
        val sessionId = repo.createSession()
        val imageFile = File(context.cacheDir, "test_image.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = Uri.fromFile(imageFile)

        val vm = CaptureViewModel(repo, fakeOcr, context)
        vm.init(sessionId)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        vm.addPageFromUri(uri)
        mainDispatcherRule.awaitUntil {
            vm.uiState.value.pages.firstOrNull()?.ocrState == "DONE" && !vm.uiState.value.isProcessing
        }

        assertThat(vm.uiState.value.pages).hasSize(1)
        assertThat(vm.uiState.value.pages[0].ocrState).isEqualTo("DONE")
        val pairs = repo.wordPairs(sessionId).first()
        assertThat(pairs).hasSize(1)
        assertThat(pairs[0].front).isEqualTo("cat")
        assertThat(pairs[0].back).isEqualTo("gato")
    }

    @Test
    fun addPageFromUri_onOcrFailure_updatesPageOcrStateToFailedAndSetsError() = runTest {
        val sessionId = repo.createSession()
        val imageFile = File(context.cacheDir, "test_image_fail.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = Uri.fromFile(imageFile)

        val vm = CaptureViewModel(repo, failingOcr, context)
        vm.init(sessionId)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        vm.addPageFromUri(uri)
        mainDispatcherRule.awaitUntil {
            vm.uiState.value.pages.firstOrNull()?.ocrState == "FAILED" && !vm.uiState.value.isProcessing
        }

        assertThat(vm.uiState.value.pages).hasSize(1)
        assertThat(vm.uiState.value.pages[0].ocrState).isEqualTo("FAILED")
        assertThat(vm.uiState.value.error).isEqualTo("Engine error")
    }

    @Test
    fun doneAddingPages_setsDoneFlag() = runTest {
        val sessionId = repo.createSession()
        val vm = CaptureViewModel(repo, fakeOcr, context)
        vm.init(sessionId)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        vm.doneAddingPages()
        assertThat(vm.uiState.value.done).isTrue()
    }

    @Test
    fun doneAddingPages_thenConsumeDone_resetsFlag() = runTest {
        val sessionId = repo.createSession()
        val vm = CaptureViewModel(repo, fakeOcr, context)
        vm.init(sessionId)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        vm.doneAddingPages()
        assertThat(vm.uiState.value.done).isTrue()
        vm.consumeDone()
        assertThat(vm.uiState.value.done).isFalse()
    }

    @Test
    fun reportError_setsError_andConsumeError_clearsIt() = runTest {
        val sessionId = repo.createSession()
        val vm = CaptureViewModel(repo, fakeOcr, context)
        vm.init(sessionId)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        vm.reportError("Capture failed")
        assertThat(vm.uiState.value.error).isEqualTo("Capture failed")
        vm.consumeError()
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun deletePage_removesPageAndItsWordPairs() = runTest {
        val sessionId = repo.createSession()
        val imageFile = File(context.cacheDir, "test_image_delete.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = Uri.fromFile(imageFile)

        val vm = CaptureViewModel(repo, fakeOcr, context)
        vm.init(sessionId)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        vm.addPageFromUri(uri)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.isNotEmpty() && !vm.uiState.value.isProcessing }
        val pageId = vm.uiState.value.pages[0].id

        vm.deletePage(pageId)
        mainDispatcherRule.awaitUntil { vm.uiState.value.pages.isEmpty() }

        assertThat(vm.uiState.value.pages).isEmpty()
        assertThat(repo.wordPairs(sessionId).first()).isEmpty()
    }
}
