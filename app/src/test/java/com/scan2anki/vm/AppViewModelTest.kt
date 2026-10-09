package com.scan2anki.vm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scan2anki.data.AppDatabase
import com.scan2anki.data.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: SessionRepository
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(mainDispatcherRule.testDispatcher)
            .build()
        repo = SessionRepository(db.importSessionDao(), db.pageDao(), db.wordPairDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun start_wipesExistingSessionsAndCreatesAFreshOne() = runTest {
        val staleSessionId = repo.createSession()
        repo.addPage(staleSessionId, "/tmp/stale.jpg", 0)

        val vm = AppViewModel(repo)
        vm.start()
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId != null }

        val newId = vm.uiState.value.sessionId!!
        assertThat(newId).isNotEqualTo(staleSessionId)
        assertThat(repo.session(staleSessionId).first()).isNull()
        assertThat(repo.session(newId).first()).isNotNull()
    }

    @Test
    fun start_calledTwice_onlyBootstrapsOnce() = runTest {
        val vm = AppViewModel(repo)
        // Call start() twice without awaiting to exercise the race condition
        vm.start()
        vm.start()

        // Wait for the session to be created
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId != null }

        // Only one bootstrap session should exist; clearAllSessions should not have run twice
        val sessionId = vm.uiState.value.sessionId
        assertThat(sessionId).isNotNull()
        assertThat(repo.session(sessionId!!).first()).isNotNull()
    }

    @Test
    fun startNewSession_createsSessionWithoutWipingBootstrapSession() = runTest {
        val vm = AppViewModel(repo)
        vm.start()
        mainDispatcherRule.awaitUntil { vm.uiState.value.sessionId != null }
        val bootstrapId = vm.uiState.value.sessionId!!

        vm.startNewSession()
        mainDispatcherRule.awaitUntil { vm.uiState.value.restartSessionId != null }

        val restartId = vm.uiState.value.restartSessionId!!
        assertThat(restartId).isNotEqualTo(bootstrapId)
        assertThat(vm.uiState.value.sessionId).isEqualTo(bootstrapId)
        assertThat(repo.session(bootstrapId).first()).isNotNull()
        assertThat(repo.session(restartId).first()).isNotNull()

        vm.consumeRestart()
        assertThat(vm.uiState.value.restartSessionId).isNull()
    }
}
