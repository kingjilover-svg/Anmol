package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.GeminiAiRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.ImageCreation
import com.example.domain.ImageModelOption
import com.example.domain.StudioAspectRatio
import com.example.util.AttachmentProcessor
import com.example.util.ExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data object Loading : ImageStudioUiState
    data class Success(val creation: ImageCreation, val bitmap: Bitmap) : ImageStudioUiState
    data class Error(val message: String) : ImageStudioUiState
}

class ImageStudioViewModel(
    private val uid: String?,
    private val repository: WorkspaceRepository,
    private val aiRepository: GeminiAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    val galleryHistory: StateFlow<List<ImageCreation>> = repository.observeStudioImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _aspectRatio = MutableStateFlow(StudioAspectRatio.SQUARE_1_1)
    val aspectRatio: StateFlow<StudioAspectRatio> = _aspectRatio.asStateFlow()

    private val _selectedModel = MutableStateFlow(ImageModelOption.NANO_BANANA_21)
    val selectedModel: StateFlow<ImageModelOption> = _selectedModel.asStateFlow()

    private val _sourceBitmapForEdit = MutableStateFlow<Bitmap?>(null)
    val sourceBitmapForEdit: StateFlow<Bitmap?> = _sourceBitmapForEdit.asStateFlow()

    fun updatePrompt(value: String) {
        _prompt.value = value
    }

    fun selectAspectRatio(ratio: StudioAspectRatio) {
        _aspectRatio.value = ratio
    }

    fun selectModel(model: ImageModelOption) {
        _selectedModel.value = model
    }

    fun setEditSourceFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = AttachmentProcessor.decodeDownsampledBitmap(context, uri, 1024)
            _sourceBitmapForEdit.value = bmp
        }
    }

    fun setEditSourceFromHistory(creation: ImageCreation) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(creation.localImagePath)
            if (bmp != null) {
                _sourceBitmapForEdit.value = bmp
                _prompt.value = creation.prompt
            }
        }
    }

    fun clearEditSource() {
        _sourceBitmapForEdit.value = null
    }

    fun openFromHistory(creation: ImageCreation) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(creation.localImagePath)
            if (bmp != null) {
                _uiState.value = ImageStudioUiState.Success(creation, bmp)
                _prompt.value = creation.prompt
            }
        }
    }

    fun generateOrEdit(context: Context) {
        val currentPrompt = _prompt.value.trim()
        if (currentPrompt.isBlank()) return
        val sourceBmp = _sourceBitmapForEdit.value
        val model = _selectedModel.value
        val ratio = _aspectRatio.value

        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading
            val result = aiRepository.generateStudioImage(
                prompt = currentPrompt,
                aspectRatio = ratio,
                modelId = model.modelId,
                sourceBitmap = sourceBmp
            )
            result.onSuccess { bitmap ->
                val savedPath = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "studio_images").apply { mkdirs() }
                    val file = File(dir, "studio_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                    }
                    file.absolutePath
                }
                val creation = ImageCreation(
                    id = UUID.randomUUID().toString().replace("-", ""),
                    prompt = currentPrompt,
                    modelId = model.modelId,
                    aspectRatio = ratio.label,
                    isEdit = sourceBmp != null,
                    localImagePath = savedPath,
                    createdAtMillis = System.currentTimeMillis()
                )
                repository.saveStudioImage(creation)
                repository.incrementUsage(uid, isImage = true)
                _uiState.value = ImageStudioUiState.Success(creation, bitmap)
            }.onFailure { err ->
                _uiState.value = ImageStudioUiState.Error(
                    err.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun shareCreation(context: Context, creation: ImageCreation) {
        val file = File(creation.localImagePath)
        if (file.exists()) {
            ExportManager.shareFile(context, file, "image/png", "Share Image Studio Creation")
        }
    }

    fun deleteCreation(creation: ImageCreation) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(creation.localImagePath).delete() }
            repository.deleteStudioImage(creation.id)
            val current = _uiState.value
            if (current is ImageStudioUiState.Success && current.creation.id == creation.id) {
                _uiState.value = ImageStudioUiState.Idle
            }
        }
    }

    companion object {
        fun provideFactory(
            uid: String?,
            repository: WorkspaceRepository,
            aiRepository: GeminiAiRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImageStudioViewModel(uid, repository, aiRepository) }
        }
    }
}
