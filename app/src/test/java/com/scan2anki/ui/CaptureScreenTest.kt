package com.scan2anki.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.data.AppDatabase
import com.scan2anki.data.SessionRepository
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import com.scan2anki.vm.CaptureViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repo: SessionRepository
    private lateinit var context: Context

    private val emptyEngine = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult = OcrResult(emptyList(), OcrSource.ON_DEVICE)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SessionRepository(db.importSessionDao(), db.pageDao(), db.wordPairDao())
    }

    @Test
    fun showsSettingsIcon() {
        val sessionId = runBlocking { repo.createSession() }
        val vm = CaptureViewModel(repo, emptyEngine, context)
        vm.init(sessionId)
        composeRule.setContent {
            CaptureScreen(sessionId = sessionId, onDone = {}, onSettings = {}, viewModel = vm)
        }
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun deletingAPageThumbnail_removesItFromTheList() {
        val sessionId = runBlocking { repo.createSession() }
        val imageFile = File(context.cacheDir, "capture_screen_test.jpg")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        runBlocking { repo.addPage(sessionId, imageFile.absolutePath, 0) }
        val vm = CaptureViewModel(repo, emptyEngine, context)
        vm.init(sessionId)
        composeRule.setContent {
            CaptureScreen(sessionId = sessionId, onDone = {}, viewModel = vm)
        }
        // Robolectric's main Looper runs paused: SessionRepository.pages()'s Room Flow delivers
        // its emission via a background-executor thread posting back to the main Looper, and
        // plain `waitUntil`'s poll loop (Thread.sleep + a Compose frame-clock advance) never
        // drains that paused Looper, so the posted update can sit forever unseen. Nesting
        // waitForIdle() in the predicate drains it each iteration.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages.size == 1
        }

        composeRule.onNodeWithContentDescription("Delete page 1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages.isEmpty()
        }

        assertThat(vm.uiState.value.pages).isEmpty()
    }

    @Test
    fun flashlightIconHidden_whenNoFlashUnitBound() {
        val sessionId = runBlocking { repo.createSession() }
        val vm = CaptureViewModel(repo, emptyEngine, context)
        vm.init(sessionId)
        composeRule.setContent {
            CaptureScreen(sessionId = sessionId, onDone = {}, onSettings = {}, viewModel = vm)
        }
        composeRule.onNodeWithContentDescription("Turn on flashlight").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Turn off flashlight").assertDoesNotExist()
    }
}
