package com.scan2anki.data

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class SessionRepository(
    private val sessionDao: ImportSessionDao,
    private val pageDao: PageDao,
    private val wordPairDao: WordPairDao,
    private val pageImagesRoot: File? = null,
) {
    fun session(id: Long): Flow<ImportSession?> =
        sessionDao.observeById(id).onEach { session ->
            Timber.v("Session flow emitted for id=%d: %s", id, session?.status ?: "null")
        }

    fun pages(sessionId: Long): Flow<List<Page>> =
        pageDao.observeBySession(sessionId).onEach { pages ->
            Timber.v("Pages flow emitted %d page(s) for session=%d", pages.size, sessionId)
        }

    fun wordPairs(sessionId: Long): Flow<List<WordPair>> =
        wordPairDao.observeBySession(sessionId).onEach { pairs ->
            Timber.v("WordPairs flow emitted %d pair(s) for session=%d", pairs.size, sessionId)
        }

    suspend fun createSession(): Long {
        val id = sessionDao.insert(ImportSession(createdAt = System.currentTimeMillis()))
        Timber.i("Created import session id=%d", id)
        return id
    }

    suspend fun addPage(sessionId: Long, imagePath: String, order: Int): Long {
        val id = pageDao.insert(Page(sessionId = sessionId, imagePath = imagePath, order = order))
        Timber.i("Added page id=%d to session=%d (order=%d, path=%s)", id, sessionId, order, imagePath)
        return id
    }

    suspend fun updatePageOcrState(pageId: Long, state: String) {
        Timber.d("Updating OCR state for page=%d to %s", pageId, state)
        pageDao.updateOcrState(pageId, state)
    }

    suspend fun updatePageZoneCache(pageId: Long, cache: PageZoneCache) {
        Timber.d("Updating zone cache for page=%d", pageId)
        pageDao.updateZoneCache(pageId, PageZoneCacheCodec.encode(cache))
    }

    suspend fun replaceWordPairsForPage(sessionId: Long, pageId: Long, rows: List<WordPair>) {
        Timber.d("Replacing %d word pair(s) for page=%d in session=%d", rows.size, pageId, sessionId)
        wordPairDao.replaceForPage(sessionId, pageId, rows)
    }

    suspend fun updateWordPair(pair: WordPair) {
        Timber.d("Updating word pair id=%d (front=%s, back=%s)", pair.id, pair.front, pair.back)
        wordPairDao.update(pair)
    }

    suspend fun deleteWordPair(id: Long) {
        Timber.d("Deleting word pair id=%d", id)
        wordPairDao.deleteById(id)
    }

    suspend fun addWordPair(pair: WordPair): Long {
        val id = wordPairDao.insert(pair)
        Timber.d("Inserted word pair id=%d for session=%d", id, pair.sessionId)
        return id
    }

    suspend fun setDeckName(sessionId: Long, deck: String) {
        Timber.d("Setting deck name for session=%d to \"%s\"", sessionId, deck)
        val current = sessionDao.observeById(sessionId).first()
        current?.let { sessionDao.update(it.copy(deckName = deck)) }
    }

    suspend fun markImported(sessionId: Long) {
        Timber.i("Marking session=%d as IMPORTED", sessionId)
        val current = sessionDao.observeById(sessionId).first()
        current?.let { sessionDao.update(it.copy(status = "IMPORTED")) }
    }

    suspend fun deleteSession(id: Long) {
        Timber.i("Deleting session id=%d", id)
        val pages = pageDao.observeBySession(id).first()
        pages.forEach { page ->
            val deleted = File(page.imagePath).delete()
            Timber.d("Deleted page image %s (success=%b)", page.imagePath, deleted)
        }
        pageImagesRoot?.let { root ->
            val dir = File(root, id.toString())
            val removed = dir.deleteRecursively()
            Timber.d("Deleted page image directory %s (success=%b)", dir.absolutePath, removed)
        }
        sessionDao.deleteById(id)
        Timber.i("Session id=%d deleted with %d page(s)", id, pages.size)
    }

    suspend fun clearAllSessions() {
        val sessions = sessionDao.observeAll().first()
        Timber.i("Clearing %d existing session(s) on startup", sessions.size)
        sessions.forEach { deleteSession(it.id) }
    }

    suspend fun deletePage(page: Page) {
        Timber.i("Deleting page id=%d from session=%d", page.id, page.sessionId)
        wordPairDao.deleteByPage(page.sessionId, page.id)
        File(page.imagePath).delete()
        pageDao.deleteById(page.id)
        val remaining = pageDao.observeBySession(page.sessionId).first()
        remaining.forEachIndexed { index, p ->
            if (p.order != index) pageDao.update(p.copy(order = index))
        }
    }
}