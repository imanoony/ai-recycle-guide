package com.example.airecycleguide.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airecycleguide.BuildConfig
import com.example.airecycleguide.data.repository.AnalysisRepository
import com.example.airecycleguide.ui.permissions.rememberCameraPermissionActions
import com.example.airecycleguide.ui.screens.AnalysisResultScreen
import com.example.airecycleguide.ui.screens.AnalyzingScreen
import com.example.airecycleguide.ui.screens.CameraScreen
import com.example.airecycleguide.ui.state.AppUiState
import com.example.airecycleguide.ui.viewmodel.RecycleGuideViewModel

@Composable
fun RecycleGuideApp() {
    val appContext = LocalContext.current.applicationContext
    val viewModelFactory = remember(appContext) {
        RecycleGuideViewModel.factory(
            AnalysisRepository(appContext, BuildConfig.API_BASE_URL),
        )
    }
    val viewModel: RecycleGuideViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionActions = rememberCameraPermissionActions(viewModel::updateCameraPermission)

    BackHandler(enabled = uiState !is AppUiState.Camera) {
        viewModel.handleBack()
    }

    when (val state = uiState) {
        AppUiState.Camera -> CameraScreen(
            permissionActions = permissionActions,
            onPhotoCaptured = viewModel::beginAnalysis,
        )

        is AppUiState.Analyzing -> AnalyzingScreen(photo = state.photo)

        is AppUiState.Result -> AnalysisResultScreen(
            outcome = state.outcome,
            onRetry = viewModel::retryAnalysis,
            onReturnHome = viewModel::returnToCamera,
        )
    }
}
