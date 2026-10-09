package com.scan2anki.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_addsNullableZoneCacheJsonColumn() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO import_sessions (id, createdAt, deckName, status) VALUES (1, 0, NULL, 'IN_PROGRESS')",
            )
            execSQL(
                "INSERT INTO pages (id, sessionId, imagePath, \"order\", ocrState) " +
                    "VALUES (1, 1, '/tmp/a.jpg', 0, 'PENDING')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val cursor = db.query("SELECT zoneCacheJson FROM pages WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("zoneCacheJson"))).isTrue()
        cursor.close()
    }

    companion object {
        private const val TEST_DB = "app-database-migration-test"
    }
}
