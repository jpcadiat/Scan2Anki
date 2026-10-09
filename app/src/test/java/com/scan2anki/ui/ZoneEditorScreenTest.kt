package com.scan2anki.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.data.AppDatabase
import com.scan2anki.data.SessionRepository
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrLine
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import com.scan2anki.vm.ZoneEditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import timber.log.Timber
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoneEditorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: SessionRepository
    private lateinit var context: Context

    private val engine = object : OcrEngine {
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

    private val singleColumnEngine = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult = OcrResult(
            lines = listOf(
                OcrLine("apple", 0.05f, 0.10f, 0.60f, 0.13f),
                OcrLine("banana", 0.05f, 0.18f, 0.60f, 0.21f),
                OcrLine("cherry", 0.05f, 0.26f, 0.60f, 0.29f),
            ),
            source = OcrSource.ON_DEVICE,
        )
    }

    private val cloudEngine = object : OcrEngine {
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
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
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

    private fun addPage(sessionId: Long, name: String): Long {
        val imageFile = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return runBlocking { repo.addPage(sessionId, imageFile.absolutePath, 0) }
    }

    private fun launch(sessionId: Long, vm: ZoneEditorViewModel) {
        composeRule.setContent {
            ZoneEditorScreen(sessionId = sessionId, onDone = {}, onBack = {}, viewModel = vm)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true && !vm.uiState.value.isProcessing
        }
    }

    /** Arms add-zone and draws one; releasing the drag commits it, with no confirmation step. */
    private fun drawZone(vm: ZoneEditorViewModel) {
        composeRule.onNodeWithText("Add exclusion zone").performClick()
        composeRule.onNodeWithTag("zone-canvas").performTouchInput {
            swipe(
                start = Offset(center.x * 0.1f, center.y * 0.1f),
                end = Offset(center.x * 0.4f, center.y * 0.4f),
                durationMillis = 200,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages.firstOrNull()?.layout?.ignoreZones?.isNotEmpty() == true
        }
    }

    @Test
    fun showsPageImageAndPreview() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_screen.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithText("Adjust columns").assertIsDisplayed()
        // A single-page session shows no pager at all -- there is nowhere to page to.
        composeRule.onNodeWithText("Page 1 of 1").assertDoesNotExist()
        composeRule.onNodeWithText("Add exclusion zone").assertIsDisplayed()
        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("gato").assertIsDisplayed()
    }

    @Test
    fun showsOverlayLegend() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_legend.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithText("Front").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Ignored").assertIsDisplayed()
        composeRule.onNodeWithText("Split").assertIsDisplayed()
    }

    @Test
    fun clickingDone_replacesPairsAndNavigates() {
        var navigated = false
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_done.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        composeRule.setContent {
            ZoneEditorScreen(sessionId = sessionId, onDone = { navigated = true }, onBack = {}, viewModel = vm)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            vm.uiState.value.pages.firstOrNull()?.lines?.isNotEmpty() == true && !vm.uiState.value.isProcessing
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Done").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            navigated
        }
        val pairs = runBlocking { repo.wordPairs(sessionId).first() }
        assertThat(pairs).hasSize(3)
    }

    @Test
    fun focusedMode_disablesPageNavigation() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_focused1.jpg")
        val focusId = addPage(sessionId, "zone_focused2.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId, focusedPageId = focusId)
        composeRule.setContent {
            ZoneEditorScreen(
                sessionId = sessionId,
                focusedPageId = focusId,
                onDone = {},
                onBack = {},
                viewModel = vm,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            vm.uiState.value.pages.firstOrNull { it.page.id == focusId }?.lines?.isNotEmpty() == true &&
                !vm.uiState.value.isProcessing
        }
        composeRule.onNodeWithText("Page 2 of 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous page").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next page").assertIsNotEnabled()
    }

    @Test
    fun tapOnLine_selectsItAndEnablesDelete() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_line.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(center.x * 0.2f, center.y * 0.2f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete line").assertIsEnabled()
    }

    @Test
    fun tapOnZone_selectsItAndOffersDelete() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_sel.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        drawZone(vm)
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(center.x * 0.15f, center.y * 0.25f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete zone").assertIsEnabled()
        // The old editor needed a "Modify" step to arm dragging; direct manipulation replaced it.
        composeRule.onNodeWithText("Modify").assertDoesNotExist()
    }

    @Test
    fun drawingAZone_commitsOnReleaseWithoutConfirmation() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_add.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        drawZone(vm)
        assertThat(vm.uiState.value.pages[0].layout.ignoreZones).hasSize(1)
        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
        // Add-zone disarms itself once the zone lands.
        composeRule.onNodeWithText("Add exclusion zone").assertIsDisplayed()
    }

    @Test
    fun addSplit_createsSplitImmediately() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_nosplit.jpg")
        val vm = ZoneEditorViewModel(repo, singleColumnEngine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithText("Add split").assertIsDisplayed()
        composeRule.onNodeWithText("Add split").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages.firstOrNull()?.layout?.splitX != null
        }
        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
    }

    @Test
    fun draggingSplit_commitsOnRelease() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_modsplit.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        // No selection or "Modify" step: grab the split line and drag it straight away.
        composeRule.onNodeWithTag("zone-canvas").performTouchInput {
            swipe(
                start = Offset(width * 0.30f, height * 0.5f),
                end = Offset(width * 0.45f, height * 0.5f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()
        assertThat(vm.uiState.value.pages[0].layout.splitX).isGreaterThan(0.40f)
    }

    @Test
    fun draggingZoneEdge_resizesOnRelease() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_modzone.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        drawZone(vm)
        val before = vm.uiState.value.pages[0].layout.ignoreZones[0]
        composeRule.onNodeWithTag("zone-canvas").performTouchInput {
            swipe(
                start = Offset(center.x * 0.40f, center.y * 0.25f),
                end = Offset(center.x * 0.60f, center.y * 0.25f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()
        val after = vm.uiState.value.pages[0].layout.ignoreZones[0]
        assertThat(after.right).isGreaterThan(before.right)
    }

    @Test
    fun resizingZoneBelowMinimumSize_isDiscarded() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_tinyresize.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        drawZone(vm)
        val before = vm.uiState.value.pages[0].layout.ignoreZones[0]
        // Drag the zone's right edge almost all the way to its left edge -- the
        // resulting rect is far under the 2% width/height minimum.
        composeRule.onNodeWithTag("zone-canvas").performTouchInput {
            swipe(
                start = Offset(center.x * 0.40f, center.y * 0.25f),
                end = Offset(center.x * 0.11f, center.y * 0.25f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()
        val after = vm.uiState.value.pages[0].layout.ignoreZones[0]
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun resetPage_fromOverflowMenu_restoresSplitZonesAndLines() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_resetpage.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(center.x * 0.2f, center.y * 0.38f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete line").performClick()
        composeRule.waitForIdle()
        assertThat(vm.uiState.value.pages[0].deletedLines).containsExactly(1)
        drawZone(vm)

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Reset page").performClick()
        composeRule.waitForIdle()
        assertThat(vm.uiState.value.pages[0].deletedLines).isEmpty()
        assertThat(vm.uiState.value.pages[0].layout.ignoreZones).isEmpty()
        assertThat(vm.uiState.value.pages[0].layout.splitX).isEqualTo(0.30f)
    }

    @Test
    fun deletingALine_canBeUndoneFromTheSnackbar() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_undo.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(center.x * 0.2f, center.y * 0.38f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete line").performClick()
        composeRule.waitForIdle()
        assertThat(vm.uiState.value.pages[0].deletedLines).containsExactly(1)

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages[0].deletedLines.isEmpty()
        }
    }

    @Test
    fun hitTestPriority_splitOverZone() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_priority.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithText("Add exclusion zone").performClick()
        composeRule.onNodeWithTag("zone-canvas").performTouchInput {
            swipe(
                start = Offset(width * 0.10f, height * 0.1f),
                end = Offset(width * 0.50f, height * 0.9f),
                durationMillis = 200,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages.firstOrNull()?.layout?.ignoreZones?.isNotEmpty() == true
        }
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(width * 0.30f, height * 0.5f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete split").assertIsDisplayed()
        composeRule.onNodeWithText("Delete zone").assertDoesNotExist()
    }

    @Test
    fun selectionDisabled_whileAddZoneIsArmed() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_addsel.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithText("Add exclusion zone").performClick()
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(width * 0.20f, height * 0.20f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete line").assertDoesNotExist()
        // A tap too small to be a zone is discarded and the arm-state is cleared.
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        assertThat(vm.uiState.value.pages[0].layout.ignoreZones).isEmpty()
    }

    @Test
    fun previewUpdatesLive_whileAZoneIsBeingDrawn() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_pending.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithText("Chapter 1").assertIsDisplayed()
        composeRule.onNodeWithText("Add exclusion zone").performClick()
        // Hold the drag open so the *uncommitted* zone is what the preview reflects.
        composeRule.onNodeWithTag("zone-canvas").performTouchInput {
            down(Offset(width * 0.0f, height * 0.08f))
            moveTo(Offset(width * 0.40f, height * 0.16f))
        }
        composeRule.waitForIdle()
        assertThat(vm.uiState.value.pages[0].layout.ignoreZones).isEmpty()
        composeRule.onNodeWithText("Chapter 1").assertDoesNotExist()
        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("gato").assertIsDisplayed()

        composeRule.onNodeWithTag("zone-canvas").performTouchInput { up() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.waitForIdle()
            vm.uiState.value.pages.firstOrNull()?.layout?.ignoreZones?.isNotEmpty() == true
        }
    }

    @Test
    fun tapSplit_selectAndDelete_isImmediate() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_delsplit.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(width * 0.30f, height * 0.5f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete split").assertIsDisplayed()
        composeRule.onNodeWithText("Delete split").performClick()
        composeRule.waitForIdle()
        assertThat(vm.uiState.value.pages[0].layout.splitX).isNull()
    }

    @Test
    fun tapZone_selectAndDelete_isImmediate() {
        val sessionId = runBlocking { repo.createSession() }
        addPage(sessionId, "zone_delzone.jpg")
        val vm = ZoneEditorViewModel(repo, engine, cloudEngine, context)
        vm.init(sessionId)
        launch(sessionId, vm)
        drawZone(vm)
        composeRule.onNodeWithTag("zone-canvas").performTouchInput { click(Offset(center.x * 0.15f, center.y * 0.25f)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete zone").assertIsDisplayed()
        composeRule.onNodeWithText("Delete zone").performClick()
        composeRule.waitForIdle()
        assertThat(vm.uiState.value.pages[0].layout.ignoreZones).isEmpty()
    }

    /**
     * The bar is the last thing above the system navigation bar, so it has to inset itself:
     * Scaffold hands its bottomBar slot the raw window bottom, not an inset-adjusted one.
     * With 3-button navigation (~48dp) the buttons were drawn underneath it and unreachable.
     */
    @Test
    fun actionBar_keepsButtonsClearOfTheNavigationBar() {
        val navBar = 48.dp
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.testTag("action-bar")) {
                    ZoneEditorActionBar(
                        addingZone = false,
                        selection = null,
                        hasSplit = true,
                        enabled = true,
                        onAddSplit = {},
                        onAddZone = {},
                        onCancelAddZone = {},
                        onDeleteSelection = {},
                        windowInsets = WindowInsets(bottom = navBar),
                    )
                }
            }
        }
        val bar = composeRule.onNodeWithTag("action-bar").getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText("Add exclusion zone").getUnclippedBoundsInRoot()
        assertThat((bar.bottom - button.bottom).value).isAtLeast(navBar.value)
    }
}
