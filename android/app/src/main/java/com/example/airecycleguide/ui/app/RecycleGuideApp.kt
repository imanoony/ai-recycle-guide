package com.example.airecycleguide.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airecycleguide.ui.permissions.rememberCameraPermissionActions
import com.example.airecycleguide.ui.screens.CameraScreen
import com.example.airecycleguide.ui.state.AppUiState
import com.example.airecycleguide.ui.viewmodel.RecycleGuideViewModel

@Composable
fun RecycleGuideApp(viewModel: RecycleGuideViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionActions = rememberCameraPermissionActions(viewModel::updateCameraPermission)

    BackHandler(enabled = uiState !is AppUiState.Camera) {
        viewModel.handleBack()
    }

    when (uiState) {
        AppUiState.Camera -> CameraScreen(
            permissionActions = permissionActions,
            onPhotoCaptured = viewModel::beginAnalysis,
        )

        is AppUiState.Analyzing -> Placeholder("촬영한 사진을 다음 단계에서 분석 화면에 표시합니다.")
        is AppUiState.Result -> Placeholder("결과 화면은 다음 단계에서 구현합니다.")
    }
}

@Composable
private fun Placeholder(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message)
    }
}
