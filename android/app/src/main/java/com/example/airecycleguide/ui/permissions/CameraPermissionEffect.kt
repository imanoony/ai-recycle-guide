package com.example.airecycleguide.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.airecycleguide.ui.state.CameraPermissionState

class CameraPermissionActions internal constructor(
    val state: CameraPermissionState,
    private val requestAction: () -> Unit,
) {
    fun request() = requestAction()
}

/** Requests camera permission on first use and reports denial versus permanent denial. */
@Composable
fun rememberCameraPermissionActions(
    onStateChanged: (CameraPermissionState) -> Unit,
): CameraPermissionActions {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnStateChanged = rememberUpdatedState(onStateChanged)
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    var permissionState by remember {
        mutableStateOf(resolvePermissionState(context, requestedBefore))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        requestedBefore = true
        permissionState = resolvePermissionState(context, requestedBefore)
    }

    val requestPermission = {
        requestedBefore = true
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, context, requestedBefore) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = resolvePermissionState(context, requestedBefore)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (permissionState == CameraPermissionState.NOT_REQUESTED) {
            requestPermission()
        }
    }

    LaunchedEffect(permissionState) {
        latestOnStateChanged.value(permissionState)
    }

    return CameraPermissionActions(
        state = permissionState,
        requestAction = requestPermission,
    )
}

private fun resolvePermissionState(
    context: Context,
    requestedBefore: Boolean,
): CameraPermissionState {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (granted) return CameraPermissionState.GRANTED
    if (!requestedBefore) return CameraPermissionState.NOT_REQUESTED

    val activity = context.findActivity()
    val shouldShowRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } ?: false

    return if (shouldShowRationale) {
        CameraPermissionState.DENIED
    } else {
        CameraPermissionState.PERMANENTLY_DENIED
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
