package com.scan2anki.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scan2anki.R
import com.scan2anki.vm.CaptureViewModel
import java.io.File
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    sessionId: Long,
    onDone: (Long) -> Unit,
    onSettings: () -> Unit = {},
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cameraPermissionRequiredMsg = stringResource(R.string.error_camera_permission_required)
    val cameraUnavailableMsg = stringResource(R.string.error_camera_unavailable)
    val captureFailedMsg = stringResource(R.string.error_capture_failed)
    LaunchedEffect(sessionId) { viewModel.init(sessionId) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.done) {
        if (state.done) {
            onDone(state.sessionId)
            viewModel.consumeDone()
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.addPageFromUri(it) } }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Timber.d("Camera permission result: granted=%b", granted)
        hasPermission = granted
        if (!granted) scope.launch { snackbarHostState.showSnackbar(cameraPermissionRequiredMsg) }
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                Timber.d("Camera provider initialized")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize camera provider")
                viewModel.reportError(e.message ?: cameraUnavailableMsg)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    LaunchedEffect(previewView, hasPermission, cameraProvider) {
        val view = previewView ?: return@LaunchedEffect
        val provider = cameraProvider ?: return@LaunchedEffect
        if (!hasPermission) return@LaunchedEffect
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }
        try {
            provider.unbindAll()
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            camera = boundCamera
            hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit()
            isTorchOn = false
            Timber.d("Camera bound to lifecycle (hasFlashUnit=%b)", hasFlashUnit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind camera to lifecycle")
            viewModel.reportError(e.message ?: cameraUnavailableMsg)
            camera = null
            hasFlashUnit = false
        }
    }

    DisposableEffect(camera) {
        val boundTorchCamera = camera
        val torchObserver = Observer<Int> { state ->
            isTorchOn = state == TorchState.ON
        }
        boundTorchCamera?.cameraInfo?.torchState?.observeForever(torchObserver)
        onDispose {
            boundTorchCamera?.cameraInfo?.torchState?.removeObserver(torchObserver)
            boundTorchCamera?.cameraControl?.enableTorch(false)
            isTorchOn = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                actions = {
                    if (hasFlashUnit) {
                        IconButton(
                            onClick = {
                                val next = !isTorchOn
                                Timber.d("CaptureScreen: toggling torch to %b", next)
                                camera?.cameraControl?.enableTorch(next)
                            },
                            enabled = hasPermission,
                        ) {
                            Icon(
                                if (isTorchOn) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                                contentDescription = stringResource(
                                    if (isTorchOn) R.string.cd_flashlight_off else R.string.cd_flashlight_on,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also { previewView = it }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .aspectRatio(3f / 4f),
                )
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.pages, key = { it.id }) { page ->
                    Box(modifier = Modifier.size(64.dp)) {
                        AsyncImage(
                            model = File(page.imagePath),
                            contentDescription = stringResource(R.string.page_number, page.order + 1),
                            modifier = Modifier.fillMaxSize(),
                        )
                        IconButton(
                            onClick = {
                                Timber.d("CaptureScreen: delete page id=%d", page.id)
                                viewModel.deletePage(page.id)
                            },
                            enabled = !state.isProcessing,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.delete_page, page.order + 1),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    Timber.i("Camera capture saved to %s", file.absolutePath)
                                    viewModel.addPageFromUri(Uri.fromFile(file))
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    Timber.e(exception, "Camera capture failed")
                                    viewModel.reportError(exception.message ?: captureFailedMsg)
                                }
                            },
                        )
                    },
                    enabled = hasPermission && !state.isProcessing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(stringResource(R.string.action_capture), modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = {
                        Timber.d("CaptureScreen: opening gallery picker")
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !state.isProcessing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.action_gallery), modifier = Modifier.padding(start = 4.dp))
                }
            }
            Button(
                onClick = { viewModel.doneAddingPages() },
                enabled = state.pages.isNotEmpty() && !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (state.isProcessing) R.string.processing else R.string.action_done_review_pairs,
                    ),
                )
            }
        }
    }
}