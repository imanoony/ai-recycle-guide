package com.example.airecycleguide.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.airecycleguide.data.repository.AnalysisCallResult
import com.example.airecycleguide.data.repository.AnalysisRepository
import com.example.airecycleguide.ui.state.AnalysisFailure
import com.example.airecycleguide.ui.state.AnalysisOutcome
import com.example.airecycleguide.ui.state.AppUiState
import com.example.airecycleguide.ui.state.CameraPermissionState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecycleGuideViewModel(
    private val analysisRepository: AnalysisRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Camera)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _cameraPermission = MutableStateFlow(CameraPermissionState.NOT_REQUESTED)
    val cameraPermission: StateFlow<CameraPermissionState> = _cameraPermission.asStateFlow()

    private var analysisJob: Job? = null
    private var currentRegion: String? = null

    fun updateCameraPermission(state: CameraPermissionState) {
        _cameraPermission.value = state
    }

    fun beginAnalysis(photo: File, region: String? = null) {
        if (_uiState.value !is AppUiState.Camera || !photo.isFile) {
            photo.delete()
            return
        }
        cancelAnalysisJob()
        currentRegion = region
        _uiState.value = AppUiState.Analyzing(photo)
        requestAnalysis(photo, region)
    }

    fun retryAnalysis() {
        val result = _uiState.value as? AppUiState.Result ?: return
        val error = result.outcome as? AnalysisOutcome.Error ?: return
        if (!error.failure.retryable || !error.retryPhoto.isFile) return

        _uiState.value = AppUiState.Analyzing(error.retryPhoto)
        requestAnalysis(error.retryPhoto, currentRegion)
    }

    private fun requestAnalysis(photo: File, region: String?) {
        analysisJob = viewModelScope.launch {
            when (val result = analysisRepository.analyze(photo, region)) {
                is AnalysisCallResult.Success -> {
                    val current = _uiState.value as? AppUiState.Analyzing ?: return@launch
                    if (current.photo != photo) return@launch
                    photo.delete()
                    _uiState.value = AppUiState.Result(
                        AnalysisOutcome.Success(result.response),
                    )
                }

                is AnalysisCallResult.Failure -> {
                    val current = _uiState.value as? AppUiState.Analyzing ?: return@launch
                    if (current.photo != photo) return@launch
                    _uiState.value = AppUiState.Result(
                        AnalysisOutcome.Error(
                            failure = AnalysisFailure(
                                code = result.error.code,
                                message = result.error.message,
                                retryable = result.error.retryable,
                            ),
                            retryPhoto = photo,
                        ),
                    )
                }
            }
            analysisJob = null
        }
    }

    /** Returns true when the back action was consumed by an in-app state transition. */
    fun handleBack(): Boolean {
        if (_uiState.value is AppUiState.Camera) return false
        returnToCamera()
        return true
    }

    fun returnToCamera() {
        cancelAnalysisJob()
        deleteOwnedPhoto(_uiState.value)
        currentRegion = null
        _uiState.value = AppUiState.Camera
    }

    private fun cancelAnalysisJob() {
        analysisJob?.cancel()
        analysisJob = null
    }

    private fun deleteOwnedPhoto(state: AppUiState) {
        when (state) {
            is AppUiState.Analyzing -> state.photo.delete()
            is AppUiState.Result -> (state.outcome as? AnalysisOutcome.Error)?.retryPhoto?.delete()
            AppUiState.Camera -> Unit
        }
    }

    override fun onCleared() {
        cancelAnalysisJob()
        deleteOwnedPhoto(_uiState.value)
        super.onCleared()
    }

    companion object {
        fun factory(repository: AnalysisRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RecycleGuideViewModel::class.java)) {
                        return RecycleGuideViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
