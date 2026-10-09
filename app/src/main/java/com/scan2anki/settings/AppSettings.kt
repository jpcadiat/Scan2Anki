package com.scan2anki.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scan2anki.parse.CleanupConfigCodec
import com.scan2anki.parse.OcrCleanup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import timber.log.Timber

class AppSettings(
    private val dataStore: DataStore<Preferences>,
) {
    val apiKey: Flow<String> = dataStore.data.map { it[Keys.API_KEY] ?: "" }
    val defaultDeckName: Flow<String> = dataStore.data.map { it[Keys.DECK_NAME] ?: "" }
    val defaultNoteType: Flow<String> = dataStore.data.map { it[Keys.NOTE_TYPE] ?: "" }
    val ocrScript: Flow<String> = dataStore.data.map { it[Keys.OCR_SCRIPT] ?: "latin" }
    val cleanupConfig: Flow<OcrCleanup.CleanupConfig> =
        dataStore.data.map { CleanupConfigCodec.decode(it[Keys.CLEANUP_CONFIG]) }
    val ageCheck: Flow<AgeCheck> = dataStore.data.map { prefs ->
        prefs[Keys.AGE_CHECK]?.let { stored -> AgeCheck.entries.firstOrNull { it.name == stored } }
            ?: AgeCheck.UNKNOWN
    }

    /** The Cloud Vision key, or "" unless the user passed the age check. Everything that calls Cloud Vision reads this. */
    val cloudOcrApiKey: Flow<String> = combine(apiKey, ageCheck) { key, age ->
        if (age == AgeCheck.ADULT_OR_TEEN) key else ""
    }

    suspend fun setApiKey(value: String) {
        Timber.i("Updating Cloud Vision API key (configured=%b)", value.isNotBlank())
        dataStore.edit { it[Keys.API_KEY] = value }
    }

    suspend fun setDefaultDeckName(value: String) {
        Timber.d("Updating default deck name to \"%s\"", value)
        dataStore.edit { it[Keys.DECK_NAME] = value }
    }

    suspend fun setDefaultNoteType(value: String) {
        Timber.d("Updating default note type to \"%s\"", value)
        dataStore.edit { it[Keys.NOTE_TYPE] = value }
    }

    suspend fun setOcrScript(value: String) {
        Timber.d("Updating OCR script to \"%s\"", value)
        dataStore.edit { it[Keys.OCR_SCRIPT] = value }
    }

    suspend fun setCleanupConfig(config: OcrCleanup.CleanupConfig) {
        Timber.d("Updating cleanup config")
        dataStore.edit { it[Keys.CLEANUP_CONFIG] = CleanupConfigCodec.encode(config) }
    }

    suspend fun setAgeCheck(value: AgeCheck) {
        Timber.i("Updating age check to %s", value)
        dataStore.edit { it[Keys.AGE_CHECK] = value.name }
    }

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val DECK_NAME = stringPreferencesKey("default_deck_name")
        val NOTE_TYPE = stringPreferencesKey("default_note_type")
        val OCR_SCRIPT = stringPreferencesKey("ocr_script")
        val CLEANUP_CONFIG = stringPreferencesKey("cleanup_config")
        val AGE_CHECK = stringPreferencesKey("age_check")
    }
}