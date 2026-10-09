package com.scan2anki.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import com.scan2anki.ui.navigation.AppNav
import com.scan2anki.ui.theme.Scan2AnkiTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Exposed for tests that need to assert on navigation/back-stack state (e.g. AppNavTest),
    // since AppNav owns its NavController internally. Not used by any production code path.
    var navController: NavHostController? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("MainActivity.onCreate (restoring=%b)", savedInstanceState != null)
        enableEdgeToEdge()
        setContent {
            Scan2AnkiTheme {
                AppNav(onNavControllerReady = { navController = it })
            }
        }
    }
}