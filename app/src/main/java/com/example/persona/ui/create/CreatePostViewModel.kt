package com.example.persona.ui.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.remote.CreatePostRequest
import com.example.persona.data.remote.GenerateImageRequest
import com.example.persona.data.remote.PostService
import com.example.persona.data.repository.PostRepository // [New] 引入 Repository
import com.example.persona.utils.UriUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import javax.inject.Inject

data class CreatePostUiState(
    val content: String = "",
    val imageUrl: String? = null,
    val isAiGenerated: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postService: PostService,
    private val postRepository: PostRepository, // [New] 注入 Repository 用于本地存储
    private val userPrefs: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    fun onContentChange(newContent: String) {
        _uiState.update { it.copy(content = newContent) }
    }

    fun generateAiImage() {
        val prompt = _uiState.value.content
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "请先输入一些描述文字") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = postService.generateAiImage(GenerateImageRequest(prompt))
                if (response.code == 200 && response.data != null) {
                    _uiState.update {
                        it.copy(imageUrl = response.data, isAiGenerated = true, isLoading = false)
                    }
                } else {
                    _uiState.update { it.copy(error = "生图失败: ${response.message}", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "网络错误: ${e.message}", isLoading = false) }
            }
        }
    }

    fun uploadLocalImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val file = UriUtils.uriToFile(context, uri)
                if (file == null) {
                    _uiState.update { it.copy(error = "无法读取图片", isLoading = false) }
                    return@launch
                }
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val response = postService.uploadImage(body)
                if (response.code == 200 && response.data != null) {
                    _uiState.update {
                        it.copy(imageUrl = response.data, isAiGenerated = false, isLoading = false)
                    }
                } else {
                    _uiState.update { it.copy(error = "上传失败: ${response.message}", isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("Upload", "Fail", e)
                _uiState.update { it.copy(error = "上传异常: ${e.message}", isLoading = false) }
            }
        }
    }

    fun removeImage() {
        _uiState.update { it.copy(imageUrl = null) }
    }

    // 发布动态
    fun createPost(personaId: Long) {
        if (_uiState.value.content.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userIdStr = userPrefs.userId.first() ?: "10086"
                val userId = userIdStr.toLongOrNull() ?: 10086L

                val request = CreatePostRequest(
                    content = _uiState.value.content,
                    imageUrls = _uiState.value.imageUrl?.let { listOf(it) } ?: emptyList()
                )

                // 1. 调用后端 API
                val response = postService.createPost(
                    userId = userId,
                    personaId = personaId,
                    request = request
                )

                if (response.code == 200 && response.data != null) {
                    // 2. [New] 成功后，手动将新帖子写入 Room
                    // 这样 PostFeedScreen 的 Flow 就会立即收到新数据，实现"自动刷新"
                    postRepository.saveRemotePost(response.data)

                    _uiState.update { it.copy(isSuccess = true, isLoading = false) }
                } else {
                    _uiState.update { it.copy(error = "发布失败: ${response.message}", isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("CreatePost", "Error", e)
                _uiState.update { it.copy(error = "发布异常: ${e.message}", isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}