package com.scan2anki.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.scan2anki.ankidroid.ANKI_READ_WRITE_PERMISSION

/**
 * Returns a callback that checks AnkiDroid's read/write permission and, if it's missing,
 * launches the system permission prompt instead of just failing. [onGranted] runs either
 * immediately (already granted) or after the user allows the prompt; [onDenied] runs if the
 * user declines it.
 */
@Composable
fun rememberAnkiPermissionAction(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onGranted() else onDenied() }
    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            ANKI_READ_WRITE_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) onGranted() else launcher.launch(ANKI_READ_WRITE_PERMISSION)
    }
}
