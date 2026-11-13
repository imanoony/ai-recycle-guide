package com.example.airecycleguide.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Starts the camera only while the camera screen is active and its owner is foregrounded. */
@Composable
fun CameraSessionLifecycleEffect(
    enabled: Boolean,
    onSessionStart: () -> Unit,
    onSessionStop: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnSessionStart = rememberUpdatedState(onSessionStart)
    val latestOnSessionStop = rememberUpdatedState(onSessionStop)

    DisposableEffect(lifecycleOwner, enabled) {
        var sessionActive = false

        fun startIfNeeded() {
            if (
                enabled &&
                !sessionActive &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                sessionActive = true
                latestOnSessionStart.value()
            }
        }

        fun stopIfNeeded() {
            if (sessionActive) {
                sessionActive = false
                latestOnSessionStop.value()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startIfNeeded()
                Lifecycle.Event.ON_STOP -> stopIfNeeded()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        startIfNeeded()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopIfNeeded()
        }
    }
}
