package com.scan2anki.ui.navigation

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import com.google.common.truth.Truth.assertThat
import com.scan2anki.data.SessionRepository
import com.scan2anki.ocr.CloudVisionOcrEngine
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrModule
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import com.scan2anki.ui.MainActivity
import com.scan2anki.vm.AppViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class AppNavTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repo: SessionRepository

    @Before
    fun injectHilt() {
        hiltRule.inject()
    }

    @Test
    fun coldStart_opensDirectlyOnCaptureScreen() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Add pages").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Add pages").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    /**
     * Regression test for the popUpTo-staleness bug found in the final whole-branch review:
     * `AppNav`'s "zones" composable used to anchor its `popUpTo` to the bootstrap session's
     * capture route (`startRoute`, fixed for the whole composition) instead of the *current*
     * session's capture route. That worked by coincidence on the very first pass through
     * Capture -> Zones -> Review (where the bootstrap session and the live session are the
     * same), but on a *second* pass -- after `AppViewModel.startNewSession()` swaps in a fresh
     * session id -- `popUpTo(startRoute)` no longer matched anything on the back stack, so it
     * silently no-op'd and left a stale `zones/{sessionId}...` entry sitting underneath
     * `review/{sessionId}` on the back stack.
     *
     * This test drives the real `AppNav` composable (with the real `NavHostController` it
     * creates internally, captured off `MainActivity.navController` -- a test-observation seam
     * that is always wired but has no effect on production behavior) through
     * Capture -> Zones -> Review *twice*, forcing a restart in between, and asserts directly on
     * `navController.currentBackStack` that the zones entry is actually popped on both passes --
     * not just the first.
     *
     * The camera/gallery UI is bypassed: pages are inserted directly via the injected
     * [SessionRepository] (backed by a tiny in-memory-generated JPEG so the real OCR/decode
     * pipeline -- with OCR faked to return zero lines via [AppNavTestOcrModule] -- still runs
     * end to end), since driving actual camera capture or the system gallery picker isn't
     * practical under Robolectric.
     */
    @Test
    fun secondPass_zonesToReview_popsStaleZonesEntryFromBackStack() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Add pages").fetchSemanticsNodes().isNotEmpty()
        }

        val vm = ViewModelProvider(composeRule.activity)[AppViewModel::class.java]
        val nav = requireNotNull(composeRule.activity.navController)

        val sessionId1 = requireNotNull(vm.uiState.value.sessionId)
        addPageAndAdvanceToReview(sessionId1)

        // First pass: startRoute (bootstrap capture route) and the live session's capture
        // route are the same id, so even the buggy code path would pop zones/id1 here. This
        // just establishes the baseline before the id changes.
        assertThat(zoneEntryCount(nav)).isEqualTo(0)
        assertThat(routePatterns(nav)).containsExactly(
            "capture/{sessionId}",
            "review/{sessionId}",
        ).inOrder()

        // Leave Review via the explicit "Start new scan" action -> AppViewModel.startNewSession()
        // -> restart navigation to a fresh session (id2), fully clearing the back stack. (The
        // Back arrow deliberately no longer does this; see backFromReview_keepsTheSession.)
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Start new scan").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Add pages").fetchSemanticsNodes().isNotEmpty()
        }

        val sessionId2 = requireNotNull(
            nav.currentBackStack.value.lastOrNull()?.arguments?.getLong("sessionId"),
        )
        assertThat(sessionId2).isNotEqualTo(sessionId1)

        addPageAndAdvanceToReview(sessionId2)

        // Second pass -- this is where the bug was hiding. With the stale `startRoute` bug,
        // popUpTo("capture/$sessionId1") would no longer match anything on a stack scoped to
        // sessionId2, so zones/{sessionId2} would still be here.
        assertThat(zoneEntryCount(nav)).isEqualTo(0)
        assertThat(routePatterns(nav)).containsExactly(
            "capture/{sessionId}",
            "review/{sessionId}",
        ).inOrder()
    }

    /**
     * Back used to be wired to `AppViewModel.startNewSession()`, so the one control that
     * universally means "go back one step" silently destroyed every captured page and every
     * edited word pair, with no warning and no undo. Back now returns to Capture with the
     * session intact; discarding it is the explicit "Start new scan" menu action.
     */
    @Test
    fun backFromReview_keepsTheSession() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Add pages").fetchSemanticsNodes().isNotEmpty()
        }
        val vm = ViewModelProvider(composeRule.activity)[AppViewModel::class.java]
        val nav = requireNotNull(composeRule.activity.navController)
        val sessionId = requireNotNull(vm.uiState.value.sessionId)
        addPageAndAdvanceToReview(sessionId)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Add pages").fetchSemanticsNodes().isNotEmpty()
        }

        // Same session, and the page captured into it is still there.
        val stillHere = requireNotNull(
            nav.currentBackStack.value.lastOrNull()?.arguments?.getLong("sessionId"),
        )
        assertThat(stillHere).isEqualTo(sessionId)
        assertThat(runBlocking { repo.pages(sessionId).first() }).hasSize(1)
    }

    private fun zoneEntryCount(nav: NavHostController): Int =
        nav.currentBackStack.value.count {
            it.destination.route == "zones/{sessionId}?focusedPageId={focusedPageId}&useCloud={useCloud}"
        }

    private fun routePatterns(nav: NavHostController): List<String> =
        nav.currentBackStack.value.mapNotNull { it.destination.route }

    /** Inserts a page directly via the repository, then drives the UI from Capture through
     * Zones to Review, bypassing the camera/gallery pickers which aren't practical to drive
     * under Robolectric. */
    private fun addPageAndAdvanceToReview(sessionId: Long) {
        val imagePath = writeFakeJpeg(composeRule.activity, "page_$sessionId.jpg")
        runBlocking { repo.addPage(sessionId, imagePath, 0) }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription("Page 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Done — review pairs").performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Adjust columns").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Done").performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Review").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun writeFakeJpeg(context: Context, name: String): String {
        val dir = File(context.cacheDir, "app-nav-test-images").apply { mkdirs() }
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        return file.absolutePath
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [OcrModule::class])
object AppNavTestOcrModule {
    private val fake = object : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): OcrResult =
            OcrResult(emptyList(), OcrSource.ON_DEVICE)
    }

    @Provides
    @Singleton
    @Named("onDevice")
    fun provideOnDeviceOcr(): OcrEngine = fake

    @Provides
    @Singleton
    @Named("cloud")
    fun provideCloudOcr(): OcrEngine = fake

    @Provides
    @Singleton
    fun provideCloudVisionOcrEngine(): CloudVisionOcrEngine = CloudVisionOcrEngine(OkHttpClient()) { "" }
}
