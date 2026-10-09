package com.ichi2.anki.api

/**
 * Build-time configuration for the vendored AnkiDroid API client.
 *
 * The upstream `api` module injects these values from its Gradle `buildConfigField`
 * (release variant). Since this copy lives inside the host app, they are pinned
 * to the matching release values here.
 */
object AnkiContractConfig {
    const val AUTHORITY: String = "com.ichi2.anki.flashcards"
    const val READ_WRITE_PERMISSION: String = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
}