package com.scan2anki.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import com.scan2anki.ocr.OcrLine
import com.scan2anki.parse.ColumnParser.ZoneRect

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SessionRepository
    private lateinit var imagesRoot: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        imagesRoot = File(context.cacheDir, "repo_images").apply { deleteRecursively() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SessionRepository(db.importSessionDao(), db.pageDao(), db.wordPairDao(), imagesRoot)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createSession_thenObserveIt() = runTest {
        val id = repo.createSession()
        val session = repo.session(id).first()
        assertThat(session).isNotNull()
        assertThat(session!!.status).isEqualTo("IN_PROGRESS")
    }

    @Test
    fun addPage_thenObserveIt() = runTest {
        val sessionId = repo.createSession()
        val pageId = repo.addPage(sessionId, "/tmp/page.jpg", 0)
        val pages = repo.pages(sessionId).first()
        assertThat(pages).hasSize(1)
        assertThat(pages[0].id).isEqualTo(pageId)
        assertThat(pages[0].ocrState).isEqualTo("PENDING")
    }

    @Test
    fun updatePageZoneCache_thenReadBack_roundTrips() = runTest {
        val sessionId = repo.createSession()
        val pageId = repo.addPage(sessionId, "/tmp/page.jpg", 0)
        val cache = PageZoneCache(
            imageWidth = 800,
            imageHeight = 1000,
            lines = listOf(OcrLine("cat", 0.05f, 0.10f, 0.30f, 0.13f)),
            splitX = 0.5f,
            ignoreZones = listOf(ZoneRect(0.0f, 0.0f, 0.2f, 0.1f)),
            deletedLines = listOf(3),
        )

        repo.updatePageZoneCache(pageId, cache)

        val page = repo.pages(sessionId).first().first { it.id == pageId }
        assertThat(PageZoneCacheCodec.decode(page.zoneCacheJson)).isEqualTo(cache)
    }

    @Test
    fun replaceWordPairsForPage_replacesExisting() = runTest {
        val sessionId = repo.createSession()
        val pageId = repo.addPage(sessionId, "/tmp/page.jpg", 0)
        repo.replaceWordPairsForPage(
            sessionId, pageId,
            listOf(WordPair(sessionId = sessionId, pageId = pageId, front = "gato", back = "cat", order = 0))
        )
        repo.replaceWordPairsForPage(
            sessionId, pageId,
            listOf(WordPair(sessionId = sessionId, pageId = pageId, front = "perro", back = "dog", order = 0))
        )
        val pairs = repo.wordPairs(sessionId).first()
        assertThat(pairs).hasSize(1)
        assertThat(pairs[0].front).isEqualTo("perro")
    }

    @Test
    fun deleteSession_cascadesToPagesAndPairs() = runTest {
        val sessionId = repo.createSession()
        val pageId = repo.addPage(sessionId, "/tmp/page.jpg", 0)
        repo.addWordPair(WordPair(sessionId = sessionId, pageId = pageId, front = "a", back = "b", order = 0))
        repo.deleteSession(sessionId)
        assertThat(repo.session(sessionId).first()).isNull()
        assertThat(repo.pages(sessionId).first()).isEmpty()
        assertThat(repo.wordPairs(sessionId).first()).isEmpty()
    }

    @Test
    fun deleteSession_removesImageFilesFromPagesDir() = runTest {
        val sessionId = repo.createSession()
        val pageDir = File(imagesRoot, sessionId.toString()).apply { mkdirs() }
        val image = File(pageDir, "page_0.jpg").apply { writeText("image-bytes") }
        val pageId = repo.addPage(sessionId, image.absolutePath, 0)
        repo.addWordPair(WordPair(sessionId = sessionId, pageId = pageId, front = "a", back = "b", order = 0))
        repo.deleteSession(sessionId)
        assertThat(image.exists()).isFalse()
        assertThat(pageDir.exists()).isFalse()
        assertThat(repo.session(sessionId).first()).isNull()
    }

    @Test
    fun clearAllSessions_removesAllSessionsAndTheirData() = runTest {
        val sessionId1 = repo.createSession()
        val pageId1 = repo.addPage(sessionId1, "/tmp/p1.jpg", 0)
        repo.addWordPair(WordPair(sessionId = sessionId1, pageId = pageId1, front = "a", back = "b", order = 0))
        val sessionId2 = repo.createSession()
        repo.addPage(sessionId2, "/tmp/p2.jpg", 0)

        repo.clearAllSessions()

        assertThat(repo.session(sessionId1).first()).isNull()
        assertThat(repo.session(sessionId2).first()).isNull()
        assertThat(repo.pages(sessionId1).first()).isEmpty()
        assertThat(repo.pages(sessionId2).first()).isEmpty()
        assertThat(repo.wordPairs(sessionId1).first()).isEmpty()
    }

    @Test
    fun deletePage_removesPageWordPairsAndImageFile() = runTest {
        val sessionId = repo.createSession()
        val pageDir = File(imagesRoot, sessionId.toString()).apply { mkdirs() }
        val image = File(pageDir, "page_0.jpg").apply { writeText("image-bytes") }
        val pageId = repo.addPage(sessionId, image.absolutePath, 0)
        repo.addWordPair(WordPair(sessionId = sessionId, pageId = pageId, front = "a", back = "b", order = 0))
        val page = repo.pages(sessionId).first().first { it.id == pageId }

        repo.deletePage(page)

        assertThat(repo.pages(sessionId).first()).isEmpty()
        assertThat(repo.wordPairs(sessionId).first()).isEmpty()
        assertThat(image.exists()).isFalse()
    }

    @Test
    fun deletePage_renumbersRemainingPages() = runTest {
        val sessionId = repo.createSession()
        val page0Id = repo.addPage(sessionId, "/tmp/p0.jpg", 0)
        val page1Id = repo.addPage(sessionId, "/tmp/p1.jpg", 1)
        val page2Id = repo.addPage(sessionId, "/tmp/p2.jpg", 2)
        val page1 = repo.pages(sessionId).first().first { it.id == page1Id }

        repo.deletePage(page1)

        val remaining = repo.pages(sessionId).first()
        assertThat(remaining.map { it.id }).containsExactly(page0Id, page2Id).inOrder()
        assertThat(remaining.map { it.order }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun setDeckNameAndMarkImported_persist() = runTest {
        val sessionId = repo.createSession()
        repo.setDeckName(sessionId, "Spanish")
        repo.markImported(sessionId)
        val session = repo.session(sessionId).first()
        assertThat(session!!.deckName).isEqualTo("Spanish")
        assertThat(session.status).isEqualTo("IMPORTED")
    }
}
