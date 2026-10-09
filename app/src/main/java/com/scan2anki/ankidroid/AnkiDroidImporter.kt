package com.scan2anki.ankidroid

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.ichi2.anki.FlashCardsContract.Model
import com.ichi2.anki.api.AddContentApi
import com.scan2anki.R
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Thin seam over the AnkiDroid content provider used by [AnkiDroidImporter],
 * so the importer's orchestration logic can be unit-tested without a device.
 */
internal interface AnkiContentStore {
    val installed: Boolean
    val hasAccess: Boolean
    fun deckId(deckName: String): Long?
    fun noteTypeId(name: String): Long?
    fun ensureDefaultNoteType(name: String): Long?
    fun noteTypeFieldCount(modelId: Long): Int?
    fun noteTypeCardCount(modelId: Long): Int?
    fun addNotes(modelId: Long, deckId: Long, fieldsList: List<Array<String>>): Int
    fun deckNames(): List<String>
    fun noteTypeNames(): List<String>
}

internal class AnkiDroidContentStore(
    context: Context,
) : AnkiContentStore {

    private val context = context.applicationContext
    private val api = AddContentApi(context)

    override val installed: Boolean
        get() = AddContentApi.getAnkiDroidPackageName(context) != null

    override val hasAccess: Boolean
        get() = context.checkSelfPermission(AddContentApi.READ_WRITE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    override fun deckId(deckName: String): Long? =
        api.deckList?.entries?.firstOrNull { it.value == deckName }?.key

    override fun deckNames(): List<String> = api.deckList?.values?.toList().orEmpty().sorted()

    override fun noteTypeId(name: String): Long? =
        api.modelList?.entries
            ?.firstOrNull { it.value.equals(name, ignoreCase = true) }
            ?.key

    override fun ensureDefaultNoteType(name: String): Long? =
        noteTypeId(name) ?: api.addNewBasic2Model(name)

    override fun noteTypeFieldCount(modelId: Long): Int? = api.getFieldList(modelId)?.size

    override fun noteTypeCardCount(modelId: Long): Int? {
        val uri = Uri.withAppendedPath(Model.CONTENT_URI, modelId.toString())
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                val cardIndex = c.getColumnIndex(Model.NUM_CARDS)
                if (cardIndex < 0) null else c.getInt(cardIndex)
            }
        }
    }

    override fun noteTypeNames(): List<String> =
        api.getModelList(2)?.values?.toList()?.sorted().orEmpty()

    override fun addNotes(modelId: Long, deckId: Long, fieldsList: List<Array<String>>): Int =
        api.addNotes(modelId, deckId, fieldsList, null)
}

internal class AnkiDroidImporter(
    context: Context,
    private val store: AnkiContentStore = AnkiDroidContentStore(context),
) : AnkiDroidSender {

    private val context = context.applicationContext

    override fun diagnose(): AnkiDroidDiagnosis {
        if (!store.installed) {
            Timber.i("AnkiDroid diagnosis: not installed")
            return AnkiDroidDiagnosis(
                installed = false,
                apiAvailable = false,
                message = context.getString(R.string.anki_not_installed),
            )
        }
        if (!store.hasAccess) {
            Timber.i("AnkiDroid diagnosis: API enabled but access not granted")
            return AnkiDroidDiagnosis(
                installed = true,
                apiAvailable = false,
                message = context.getString(R.string.anki_access_not_granted_diagnose),
            )
        }
        Timber.i("AnkiDroid diagnosis: OK")
        return AnkiDroidDiagnosis(
            installed = true,
            apiAvailable = true,
            message = context.getString(R.string.anki_connection_ok),
        )
    }

    override fun getDeckNames(): List<String> = try {
        store.deckNames()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "getDeckNames: failed to read deck list from AnkiDroid")
        emptyList()
    }

    override fun getNoteTypeNames(): List<String> = try {
        store.noteTypeNames()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "getNoteTypeNames: failed to read note type list from AnkiDroid")
        emptyList()
    }

    override fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult {
        if (notes.isEmpty()) {
            Timber.w("sendCards: no notes to send")
            return AnkiDroidSendResult.Failed(context.getString(R.string.anki_no_cards_to_export))
        }
        if (!store.installed) {
            Timber.w("sendCards: AnkiDroid not installed")
            return AnkiDroidSendResult.Failed(context.getString(R.string.anki_not_installed))
        }
        if (!store.hasAccess) {
            Timber.w("sendCards: AnkiDroid access not granted")
            return accessDenied()
        }

        return try {
            val defaultName = context.getString(R.string.default_note_type_name)
            val requestedType = noteTypeName.ifBlank { defaultName }
            val isDefault = requestedType.equals(defaultName, ignoreCase = true)
            val modelId = if (isDefault) {
                store.ensureDefaultNoteType(requestedType)
            } else {
                store.noteTypeId(requestedType)
            } ?: return AnkiDroidSendResult.Failed(context.getString(R.string.anki_no_usable_note_type))
            val fieldCount = store.noteTypeFieldCount(modelId)
            if (fieldCount == null || fieldCount < 2) {
                Timber.w("sendCards: note type \"%s\" has %s field(s), need at least 2", requestedType, fieldCount)
                return AnkiDroidSendResult.Failed(context.getString(R.string.anki_no_usable_note_type))
            }
            if (isDefault && store.noteTypeCardCount(modelId) != 2) {
                Timber.w("sendCards: note type \"%s\" does not have exactly two card templates", requestedType)
                return AnkiDroidSendResult.Failed(context.getString(R.string.anki_no_usable_note_type))
            }
            val deckId = store.deckId(deckName)
            if (deckId == null) {
                Timber.w("sendCards: deck \"%s\" not found in AnkiDroid", deckName)
                return AnkiDroidSendResult.Failed(
                    context.getString(R.string.anki_deck_not_found, deckName),
                )
            }
            val added = store.addNotes(
                modelId,
                deckId,
                notes.map { arrayOf(it.front, it.back) + Array(fieldCount - 2) { "" } },
            )
            Timber.i(
                "sendCards: added %d of %d card(s) to deck \"%s\" using note type \"%s\"",
                added,
                notes.size,
                deckName,
                requestedType,
            )
            AnkiDroidSendResult.Added(added, notes.size)
        } catch (e: SecurityException) {
            Timber.w(e, "sendCards: AnkiDroid access denied")
            accessDenied()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "sendCards: failed to send cards to AnkiDroid")
            AnkiDroidSendResult.Failed(e.message ?: context.getString(R.string.anki_send_failed))
        }
    }

    private fun accessDenied(): AnkiDroidSendResult.Failed =
        AnkiDroidSendResult.Failed(context.getString(R.string.anki_access_denied))
}