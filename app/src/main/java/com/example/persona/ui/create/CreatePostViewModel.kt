package com.example.persona.ui.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.remote.CreatePostRequest
import com.example.persona.data.remote.GenerateImageRequest
import com.example.persona.data.remote.PersonaDto
import com.example.persona.data.remote.PostService
import com.example.persona.data.repository.PostRepository
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
    val imageUrls: List<String> = emptyList(),

    val personas: List<PersonaDto> = emptyList(),
    val selectedPersonaId: Long? = null,
    val searchQuery: String = "",

    val isAiGenerated: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,

    val isMagicEditing: Boolean = false
)

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postService: PostService,
    private val postRepository: PostRepository,
    private val userPrefs: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    private var allPersonasCache: List<PersonaDto> = emptyList()
    private val preSelectedPersonaId: Long? = savedStateHandle.get<Long>("personaId")?.takeIf { it != -1L }

    init {
        fetchPersonas()
    }

    private fun fetchPersonas() {
        viewModelScope.launch {
            try {
                val response = postService.getPersonas()
                if (response.code == 200 && response.data != null) {
                    val list = response.data
                    allPersonasCache = list
                    val defaultId = preSelectedPersonaId ?: list.firstOrNull()?.id

                    _uiState.update {
                        it.copy(
                            personas = list,
                            selectedPersonaId = it.selectedPersonaId ?: defaultId

                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        val filteredList = if (query.isBlank()) {
            allPersonasCache
        } else {
            allPersonasCache.filter {
                it.name.contains(query, ignoreCase = true) ||
                        (it.description?.contains(query, ignoreCase = true) == true)
            }
        }
        _uiState.update { it.copy(personas = filteredList) }
    }

    fun selectPersona(id: Long) {
        _uiState.update { it.copy(selectedPersonaId = id) }
    }

    fun onContentChange(newContent: String) {
        _uiState.update { it.copy(content = newContent) }
    }

    fun generateAiImage() {
        val prompt = _uiState.value.content
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "请先输入一些描述文字") }
            return
        }
        if (_uiState.value.imageUrls.size >= 9) {
            _uiState.update { it.copy(error = "最多只能添加9张图片") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = postService.generateAiImage(GenerateImageRequest(prompt))
                if (response.code == 200 && response.data != null) {
                    _uiState.update {
                        it.copy(
                            imageUrls = it.imageUrls + response.data,
                            isAiGenerated = true,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = "生图失败: ${response.message}", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "网络错误: ${e.message}", isLoading = false) }
            }
        }
    }

    fun uploadLocalImages(uris: List<Uri>) {
        if (_uiState.value.imageUrls.size + uris.size > 9) {
            _uiState.update { it.copy(error = "最多只能选择9张图片") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val uploadedUrls = mutableListOf<String>()
            for (uri in uris) {
                try {
                    val file = UriUtils.uriToFile(context, uri)
                    if (file != null) {
                        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                        val response = postService.uploadImage(body)
                        if (response.code == 200 && response.data != null) {
                            uploadedUrls.add(response.data)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Upload", "Fail for $uri", e)
                }
            }
            if (uploadedUrls.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        imageUrls = it.imageUrls + uploadedUrls,
                        isAiGenerated = false,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "图片上传失败") }
            }
        }
    }

    fun removeImage(index: Int) {
        _uiState.update {
            val newList = it.imageUrls.toMutableList()
            if (index in newList.indices) {
                newList.removeAt(index)
            }
            it.copy(imageUrls = newList)
        }
    }

    fun createPost() {
        if (_uiState.value.content.isBlank() && _uiState.value.imageUrls.isEmpty()) {
            _uiState.update { it.copy(error = "内容不能为空") }
            return
        }
        val selectedId = _uiState.value.selectedPersonaId
        if (selectedId == null) {
            _uiState.update { it.copy(error = "请选择发布的智能体身份") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userIdStr = userPrefs.userId.first() ?: "10086"
                val userId = userIdStr.toLongOrNull() ?: 10086L
                val request = CreatePostRequest(
                    content = _uiState.value.content,
                    imageUrls = _uiState.value.imageUrls
                )
                val response = postService.createPost(
                    userId = userId,
                    personaId = selectedId,
                    request = request
                )
                if (response.code == 200 && response.data != null) {
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

    // [Mod] AI 润色功能：直接覆盖原文
    fun magicEdit() {
        val content = _uiState.value.content
        if (content.isBlank()) {
            _uiState.update { it.copy(error = "请先写点草稿吧~") }
            return
        }

        val selectedId = _uiState.value.selectedPersonaId
        val selectedPersona = allPersonasCache.find { it.id == selectedId }
        val personaName = selectedPersona?.name
        val description = selectedPersona?.description
        val tags = selectedPersona?.personalityTags

        viewModelScope.launch {
            _uiState.update { it.copy(isMagicEditing = true, error = null) }

            val result = postRepository.magicEdit(content, personaName, description, tags)

            if (result.isSuccess) {
                val aiResult = result.getOrNull()

                // [Fix] 简单清洗结果：移除可能存在的首尾引号（AI 有时会自作聪明加引号）
                val cleanedResult = aiResult?.trim()?.removePrefix("\"")?.removeSuffix("\"") ?: content

                _uiState.update {
                    it.copy(
                        content = cleanedResult, // 直接替换（覆盖）
                        isMagicEditing = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        error = "润色失败: ${result.exceptionOrNull()?.message}",
                        isMagicEditing = false
                    )
                }
            }
        }
    }
}