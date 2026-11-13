package com.example.airecycleguide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.airecycleguide.ui.camera.CameraSessionLifecycleEffect
import com.example.airecycleguide.ui.permissions.CameraPermissionActions
import com.example.airecycleguide.ui.state.CameraPermissionState
import java.io.File
import java.util.UUID

@Composable
fun CameraScreen(
    permissionActions: CameraPermissionActions,
    onPhotoCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var sessionActive by remember { mutableStateOf(false) }
    var captureInProgress by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun takePhoto() {
        if (captureInProgress || !sessionActive) return
        captureInProgress = true
        cameraError = null

        val photo = File(context.cacheDir, "waste-${UUID.randomUUID()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photo).build()
        try {
            cameraController.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        captureInProgress = false
                        if (sessionActive && photo.isFile && photo.length() > 0L) {
                            latestOnPhotoCaptured(photo)
                        } else {
                            photo.delete()
                        }
                    }

                    override fun onError(error: ImageCaptureException) {
                        captureInProgress = false
                        photo.delete()
                        cameraError = "사진을 촬영하지 못했습니다. 다시 시도해 주세요."
                    }
                },
            )
        } catch (_: IllegalStateException) {
            captureInProgress = false
            photo.delete()
            cameraError = "카메라가 준비되지 않았습니다. 다시 시도해 주세요."
        }
    }

    CameraSessionLifecycleEffect(
        enabled = permissionActions.state == CameraPermissionState.GRANTED,
        onSessionStart = {
            try {
                cameraController.bindToLifecycle(lifecycleOwner)
                sessionActive = true
                cameraError = null
            } catch (_: Exception) {
                sessionActive = false
                cameraError = "카메라를 시작할 수 없습니다."
            }
        },
        onSessionStop = {
            sessionActive = false
            captureInProgress = false
            cameraController.unbind()
        },
    )

    LaunchedEffect(cameraError) {
        cameraError?.let { message ->
            snackbarHostState.showSnackbar(message)
            cameraError = null
        }
    }

    when (permissionActions.state) {
        CameraPermissionState.GRANTED -> {
            val previewView = remember(context, cameraController) {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cameraController
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { previewView },
                    update = { it.controller = cameraController },
                    modifier = Modifier.fillMaxSize(),
                )

                ShutterButton(
                    enabled = sessionActive && !captureInProgress,
                    onClick = ::takePhoto,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(bottom = 32.dp),
                )

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 24.dp),
                )
            }
        }

        CameraPermissionState.DENIED -> PermissionMessage(
            message = "카메라 권한이 필요합니다.",
            actionLabel = "권한 허용",
            onAction = permissionActions::request,
        )

        CameraPermissionState.PERMANENTLY_DENIED -> PermissionMessage(
            message = "설정에서 카메라 권한을 허용해 주세요.",
            actionLabel = "설정 열기",
            onAction = {
                val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                ContextCompat.startActivity(context, settingsIntent, null)
            },
        )

        CameraPermissionState.NOT_REQUESTED -> Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
        )
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(76.dp)
            .background(Color.White, CircleShape)
            .semantics { contentDescription = "사진 촬영" },
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .border(width = 2.dp, color = Color.DarkGray, shape = CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun PermissionMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = message, color = Color.White)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
