package com.scan2anki.ankidroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnkiDroidImporterTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun importer(store: AnkiContentStore): AnkiDroidImporter =
        AnkiDroidImporter(context(), store)

    private class FakeStore : AnkiContentStore {
        override var installed = true
        override var hasAccess = true
        var deckIdResult: Long? = 1L
        val models = mutableMapOf(3L to "General", 7L to "Basic (and reversed card)")
        var createBasic2Result: Long? = 4L
        val modelFieldCounts = mutableMapOf(3L to 2, 4L to 2, 7L to 2)
        val modelCardCounts = mutableMapOf(3L to 2, 4L to 2, 7L to 2)
        var addNotesResult = 0
        var addNotesThrows: Exception? = null
        var deckNamesResult: List<String> = emptyList()
        var deckNamesThrows: Exception? = null
        var noteTypeNamesResult: List<String> = emptyList()
        var noteTypeNamesThrows: Exception? = null
        val requestedNoteTypes = mutableListOf<String>()
        val createdBasic2 = mutableListOf<String>()
        val addedFields = mutableListOf<Array<String>>()
        var lastModelId: Long? = null
        var lastDeckId: Long? = null

        override fun deckId(deckName: String): Long? = deckIdResult
        override fun noteTypeId(name: String): Long? {
            requestedNoteTypes.add(name)
            return models.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.key
        }
        override fun ensureDefaultNoteType(name: String): Long? {
            requestedNoteTypes.add(name)
            return models.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.key ?: run {
                createdBasic2.add(name)
                createBasic2Result
            }
        }
        override fun noteTypeFieldCount(modelId: Long): Int? = modelFieldCounts[modelId]
        override fun noteTypeCardCount(modelId: Long): Int? = modelCardCounts[modelId]
        override fun addNotes(modelId: Long, deckId: Long, fieldsList: List<Array<String>>): Int {
            addNotesThrows?.let { throw it }
            lastModelId = modelId
            lastDeckId = deckId
            addedFields.addAll(fieldsList)
            return addNotesResult
        }
        override fun deckNames(): List<String> {
            deckNamesThrows?.let { throw it }
            return deckNamesResult
        }
        override fun noteTypeNames(): List<String> {
            noteTypeNamesThrows?.let { throw it }
            return noteTypeNamesResult
        }
    }

    @Test
    fun diagnose_whenNotInstalled_returnsNotInstalled() {
        val store = FakeStore().apply { installed = false }
        val diagnosis = importer(store).diagnose()
        assertThat(diagnosis.installed).isFalse()
        assertThat(diagnosis.apiAvailable).isFalse()
        assertThat(diagnosis.ok).isFalse()
        assertThat(diagnosis.message).contains("AnkiDroid isn't installed")
    }

    @Test
    fun diagnose_whenAccessNotGranted_returnsGuidance() {
        val store = FakeStore().apply { hasAccess = false }
        val diagnosis = importer(store).diagnose()
        assertThat(diagnosis.installed).isTrue()
        assertThat(diagnosis.apiAvailable).isFalse()
        assertThat(diagnosis.ok).isFalse()
        assertThat(diagnosis.message).contains("allow the access")
    }

    @Test
    fun diagnose_whenAccessGranted_returnsOk() {
        val diagnosis = importer(FakeStore()).diagnose()
        assertThat(diagnosis.installed).isTrue()
        assertThat(diagnosis.apiAvailable).isTrue()
        assertThat(diagnosis.ok).isTrue()
        assertThat(diagnosis.message).contains("works")
    }

    @Test
    fun sendCards_withEmptyNotes_returnsFailedWithoutStoreCalls() {
        val store = FakeStore()
        val result = importer(store).sendCards("Spanish", "General", emptyList())
        assertThat(result).isEqualTo(AnkiDroidSendResult.Failed("No cards to export"))
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_whenNotInstalled_returnsFailed() {
        val store = FakeStore().apply { installed = false }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("gato", "cat")))
        assertThat(result).isEqualTo(
            AnkiDroidSendResult.Failed("AnkiDroid isn't installed. Install it from Google Play or F-Droid."),
        )
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_whenAccessNotGranted_returnsGuidance() {
        val store = FakeStore().apply { hasAccess = false }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("gato", "cat")))
        assertThat(result).isInstanceOf(AnkiDroidSendResult.Failed::class.java)
        assertThat((result as AnkiDroidSendResult.Failed).reason).contains("allow the access")
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_addsAllNotesInOneBatch() {
        val store = FakeStore().apply { addNotesResult = 2 }
        val result = importer(store).sendCards(
            "Spanish",
            "",
            listOf(AnkiNote("cat", "gato"), AnkiNote("dog", "perro")),
        )
        assertThat(result).isEqualTo(AnkiDroidSendResult.Added(added = 2, total = 2))
        assertThat(store.lastModelId).isEqualTo(3L)
        assertThat(store.createdBasic2).isEmpty()
        assertThat(store.addedFields).hasSize(2)
        assertThat(store.addedFields[0].toList()).containsExactly("cat", "gato")
        assertThat(store.addedFields[1].toList()).containsExactly("dog", "perro")
    }

    @Test
    fun sendCards_whenDeckMissing_returnsFailedWithoutCreatingIt() {
        val store = FakeStore().apply { deckIdResult = null }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(
            AnkiDroidSendResult.Failed("Deck \"Spanish\" doesn't exist in AnkiDroid. Create it there first."),
        )
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_whenNoBasicModel_returnsFailed() {
        val store = FakeStore().apply {
            models.clear()
            createBasic2Result = null
        }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(AnkiDroidSendResult.Failed("AnkiDroid has no usable note type"))
    }

    @Test
    fun sendCards_usesRequestedExistingNoteType() {
        val store = FakeStore().apply { addNotesResult = 1 }
        val result = importer(store).sendCards(
            "Spanish",
            "Basic (and reversed card)",
            listOf(AnkiNote("cat", "gato")),
        )
        assertThat(result).isEqualTo(AnkiDroidSendResult.Added(added = 1, total = 1))
        assertThat(store.requestedNoteTypes).containsExactly("Basic (and reversed card)")
        assertThat(store.createdBasic2).isEmpty()
        assertThat(store.lastModelId).isEqualTo(7L)
    }

    @Test
    fun sendCards_createsMissingLocalizedDefaultAsBasic2() {
        val store = FakeStore().apply {
            models.clear()
            addNotesResult = 1
        }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(AnkiDroidSendResult.Added(added = 1, total = 1))
        assertThat(store.createdBasic2).containsExactly("General")
        assertThat(store.lastModelId).isEqualTo(4L)
    }

    @Test
    fun sendCards_rejectsMissingNonDefaultNoteType() {
        val store = FakeStore().apply { models.clear() }
        val result = importer(store).sendCards("Spanish", "Custom Type", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(AnkiDroidSendResult.Failed("AnkiDroid has no usable note type"))
        assertThat(store.createdBasic2).isEmpty()
        assertThat(store.requestedNoteTypes).containsExactly("Custom Type")
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_padsFieldsToSelectedNoteTypeFieldCount() {
        val threeFieldStore = FakeStore().apply {
            addNotesResult = 1
            modelFieldCounts[7L] = 3
        }
        val result = importer(threeFieldStore).sendCards(
            "Spanish",
            "Basic (and reversed card)",
            listOf(AnkiNote("cat", "gato")),
        )
        assertThat(result).isEqualTo(AnkiDroidSendResult.Added(added = 1, total = 1))
        assertThat(threeFieldStore.addedFields[0].toList()).containsExactly("cat", "gato", "").inOrder()

        val fourFieldStore = FakeStore().apply {
            addNotesResult = 1
            modelFieldCounts[3L] = 4
        }
        importer(fourFieldStore).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(fourFieldStore.addedFields[0].toList()).containsExactly("cat", "gato", "", "").inOrder()
    }

    @Test
    fun sendCards_whenDefaultNameTakenByOneFieldModel_returnsFailed() {
        val store = FakeStore().apply { modelFieldCounts[3L] = 1 }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(AnkiDroidSendResult.Failed("AnkiDroid has no usable note type"))
        assertThat(store.createdBasic2).isEmpty()
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_whenDefaultNameTakenByOneCardModel_returnsFailed() {
        val store = FakeStore().apply { modelCardCounts[3L] = 1 }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(AnkiDroidSendResult.Failed("AnkiDroid has no usable note type"))
        assertThat(store.createdBasic2).isEmpty()
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_rejectsNonDefaultNoteTypeWithOneField() {
        val store = FakeStore().apply { modelFieldCounts[7L] = 1 }
        val result = importer(store).sendCards("Spanish", "Basic (and reversed card)", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(AnkiDroidSendResult.Failed("AnkiDroid has no usable note type"))
        assertThat(store.addedFields).isEmpty()
    }

    @Test
    fun sendCards_partialAdd_reportsAddedCount() {
        val store = FakeStore().apply { addNotesResult = 1 }
        val result = importer(store).sendCards(
            "Spanish",
            "General",
            listOf(AnkiNote("cat", "gato"), AnkiNote("dog", "perro")),
        )
        assertThat(result).isEqualTo(AnkiDroidSendResult.Added(added = 1, total = 2))
    }

    @Test
    fun sendCards_whenAccessRevokedDuringAdd_returnsGuidance() {
        val store = FakeStore().apply {
            addNotesThrows = SecurityException("Permission Denial")
        }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isInstanceOf(AnkiDroidSendResult.Failed::class.java)
        assertThat((result as AnkiDroidSendResult.Failed).reason).contains("allow the access")
    }

    @Test
    fun sendCards_whenStoreFails_returnsFailedWithReason() {
        val store = FakeStore().apply {
            addNotesThrows = IOException("provider crashed")
        }
        val result = importer(store).sendCards("Spanish", "General", listOf(AnkiNote("cat", "gato")))
        assertThat(result).isEqualTo(AnkiDroidSendResult.Failed("provider crashed"))
    }

    @Test
    fun getDeckNames_returnsNamesFromStore() {
        val store = FakeStore().apply { deckNamesResult = listOf("Spanish", "French") }
        val names = importer(store).getDeckNames()
        assertThat(names).containsExactly("Spanish", "French")
    }

    @Test
    fun getDeckNames_whenStoreThrows_returnsEmptyList() {
        val store = FakeStore().apply { deckNamesThrows = SecurityException("denied") }
        val names = importer(store).getDeckNames()
        assertThat(names).isEmpty()
    }

    @Test
    fun getNoteTypeNames_returnsStoreNamesAndEmptyWhenStoreThrows() {
        val store = FakeStore().apply { noteTypeNamesResult = listOf("Basic", "General") }
        assertThat(importer(store).getNoteTypeNames()).containsExactly("Basic", "General")
        val throwingStore = FakeStore().apply { noteTypeNamesThrows = SecurityException("denied") }
        assertThat(importer(throwingStore).getNoteTypeNames()).isEmpty()
    }
}