package com.scan2anki.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scan2anki.ui.CaptureScreen
import com.scan2anki.ui.ReviewScreen
import com.scan2anki.ui.SettingsScreen
import com.scan2anki.ui.ZoneEditorScreen
import com.scan2anki.vm.AppViewModel
import timber.log.Timber

@Composable
fun AppNav(
    appViewModel: AppViewModel = hiltViewModel(),
    onNavControllerReady: (NavHostController) -> Unit = {},
) {
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { appViewModel.start() }

    val initialSessionId = appState.sessionId ?: return
    val startRoute = "capture/$initialSessionId"

    val navController = rememberNavController()
    LaunchedEffect(navController) { onNavControllerReady(navController) }
    NavHost(navController = navController, startDestination = startRoute) {
        composable(
            route = "capture/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            CaptureScreen(
                sessionId = sessionId,
                onDone = { id ->
                    Timber.i("Navigation: capture done for session id=%d", id)
                    navController.navigate("zones/$id")
                },
                onSettings = {
                    Timber.d("Navigation: opening settings")
                    navController.navigate("settings")
                },
            )
        }
        composable(
            route = "zones/{sessionId}?focusedPageId={focusedPageId}&useCloud={useCloud}&restoreFromCache={restoreFromCache}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("focusedPageId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("useCloud") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("restoreFromCache") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            val focusedPageId = entry.arguments?.getLong("focusedPageId")?.takeIf { it >= 0 }
            val useCloud = entry.arguments?.getBoolean("useCloud") == true
            val restoreFromCache = entry.arguments?.getBoolean("restoreFromCache") == true
            ZoneEditorScreen(
                sessionId = sessionId,
                focusedPageId = focusedPageId,
                useCloud = useCloud,
                restoreFromCache = restoreFromCache,
                onDone = {
                    if (focusedPageId != null) {
                        Timber.i("Navigation: zone editor done for session=%d page=%d; returning to review", sessionId, focusedPageId)
                        navController.popBackStack()
                    } else {
                        Timber.i("Navigation: zone editor done for session id=%d", sessionId)
                        navController.navigate("review/$sessionId") { popUpTo("capture/$sessionId") }
                    }
                },
                onBack = {
                    Timber.d("Navigation: back from zone editor to %s", if (focusedPageId != null) "review" else "capture")
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = "review/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            ReviewScreen(
                sessionId = sessionId,
                onBack = {
                    // Back means "back to the pages I captured", not "throw the session away".
                    // Discarding is an explicit action ("Start new scan") in Review's menu.
                    Timber.d("Navigation: back from review to capture, keeping session=%d", sessionId)
                    navController.popBackStack()
                },
                onStartNewSession = {
                    Timber.i("Navigation: discarding session=%d, starting a fresh capture", sessionId)
                    appViewModel.startNewSession()
                },
                onRerunOcr = { pageId, useCloud ->
                    Timber.i("Navigation: re-run OCR for session=%d page=%d useCloud=%b", sessionId, pageId, useCloud)
                    navController.navigate("zones/$sessionId?focusedPageId=$pageId&useCloud=$useCloud")
                },
                onViewZones = { pageId ->
                    Timber.i("Navigation: view zones for session=%d page=%d", sessionId, pageId)
                    navController.navigate("zones/$sessionId?focusedPageId=$pageId&restoreFromCache=true")
                },
                onSentSuccessfully = {
                    Timber.d("Navigation: cards sent, starting a fresh capture session")
                    appViewModel.startNewSession()
                },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = {
                Timber.d("Navigation: closing settings")
                navController.popBackStack()
            })
        }
    }

    LaunchedEffect(appState.restartSessionId) {
        val id = appState.restartSessionId
        if (id != null) {
            Timber.i("Navigation: navigating to fresh capture for session id=%d", id)
            navController.navigate("capture/$id") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            appViewModel.consumeRestart()
        }
    }
}
