package com.example.persona.ui.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.remote.PostService // 需要这个来上传图片
import com.example.persona.data.repository.PersonaRepository
import com.example.persona.utils.UriUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import javax.inject.Inject

data class CreatePersonaUiState(
    val name: String = "",
    val description: String = "",
    val prompt: String = "",
    val avatarUrl: String = "",

    // [Mod] 标签改为 List 以便在 UI 上做成 Chip
    val tags: List<String> = emptyList(),

    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreatePersonaViewModel @Inject constructor(
    private val repository: PersonaRepository,
    private val postService: PostService, // 用于上传图片
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePersonaUiState())
    val uiState = _uiState.asStateFlow()

    private val editId: Long? = savedStateHandle.get<Long>("editId")?.takeIf { it != -1L }

    init {
        if (editId != null) {
            loadPersonaForEdit(editId)
        }
    }

    private fun loadPersonaForEdit(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isEditMode = true) }
            val persona = repository.getPersona(id)
            if (persona != null) {
                _uiState.update {
                    it.copy(
                        name = persona.name,
                        description = persona.description ?: "",
                        prompt = persona.promptTemplate ?: "",
                        avatarUrl = persona.avatarUrl ?: "",
                        // 解析逗号分隔的字符串回 List
                        tags = persona.personalityTags?.split(",", "，")?.filter { t -> t.isNotBlank() } ?: emptyList(),
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "加载智能体信息失败") }
            }
        }
    }

    // --- 表单操作 ---
    fun onNameChange(v: String) = _uiState.update { it.copy(name = v) }
    fun onDescChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onPromptChange(v: String) = _uiState.update { it.copy(prompt = v) }

    // [Mod] 标签操作
    fun addTag(tag: String) {
        if (tag.isNotBlank() && !_uiState.value.tags.contains(tag)) {
            _uiState.update { it.copy(tags = it.tags + tag) }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags - tag) }
    }

    // [Mod] 图片上传逻辑
    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val file = UriUtils.uriToFile(context, uri)
                if (file != null) {
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    val response = postService.uploadImage(body)
                    if (response.code == 200 && response.data != null) {
                        _uiState.update { it.copy(avatarUrl = response.data, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(error = "上传失败: ${response.message}", isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "上传出错: ${e.message}", isLoading = false) }
            }
        }
    }

    // [Mod] AI 生成：解析 JSON 并填充所有字段
    fun generateAiDescription() {
        val name = _uiState.value.name
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "请先输入名字") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // repository.generateDescription 现在返回的是 JSON String
            val jsonStr = repository.generateDescription(name)

            try {
                // 简单的 JSON 解析
                val json = JSONObject(jsonStr)
                val desc = json.optString("description")
                val prompt = json.optString("prompt")
                val tagsJson = json.optJSONArray("tags")

                val newTags = mutableListOf<String>()
                if (tagsJson != null) {
                    for (i in 0 until tagsJson.length()) {
                        newTags.add(tagsJson.getString(i))
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        description = desc,
                        prompt = prompt,
                        tags = newTags
                    )
                }
            } catch (e: Exception) {
                Log.e("CreatePersona", "JSON Parse Error", e)
                // 降级处理：如果解析失败，直接放入描述框
                _uiState.update { it.copy(isLoading = false, description = jsonStr) }
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "名字不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val persona = Persona(
                name = state.name,
                description = state.description,
                promptTemplate = state.prompt,
                avatarUrl = state.avatarUrl,
                // 将 List 转回逗号分隔的字符串存库
                personalityTags = state.tags.joinToString(","),
                isPublic = true
            )

            val success = if (state.isEditMode && editId != null) {
                repository.updatePersona(editId, persona.copy(id = editId))
            } else {
                repository.createPersona(persona)
            }

            if (success) {
                _uiState.update { it.copy(isSuccess = true, isLoading = false) }
            } else {
                _uiState.update { it.copy(error = "操作失败，请重试", isLoading = false) }
            }
        }
    }
}