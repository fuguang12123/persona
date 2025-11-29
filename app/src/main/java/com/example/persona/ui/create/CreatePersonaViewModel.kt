package com.example.persona.ui.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.remote.PostService
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
    val tags: List<String> = emptyList(),
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreatePersonaViewModel @Inject constructor(
    private val repository: PersonaRepository,
    private val postService: PostService,
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

    fun addTag(tag: String) {
        if (tag.isNotBlank() && !_uiState.value.tags.contains(tag)) {
            _uiState.update { it.copy(tags = it.tags + tag) }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags - tag) }
    }

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

            // 1. 调用 Repository (后端现在会根据名字生成完整的 JSON)
            val jsonStr = repository.generateDescription(name)

            // 2. 空值检查
            if (jsonStr.isNullOrBlank()) {
                Log.e("CreatePersona", "AI response is empty")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "生成失败：AI 未返回数据，请检查网络"
                    )
                }
                return@launch
            }

            try {
                // 3. 清洗 Markdown (防止 AI 返回 ```json)
                val cleanJsonStr = jsonStr.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                // 4. 解析 JSON
                val json = JSONObject(cleanJsonStr)
                val desc = json.optString("description")
                val prompt = json.optString("prompt")
                val tagsJson = json.optJSONArray("tags")

                val newTags = mutableListOf<String>()
                if (tagsJson != null) {
                    for (i in 0 until tagsJson.length()) {
                        newTags.add(tagsJson.getString(i))
                    }
                }

                // 5. 成功！一次性填入所有字段
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        description = desc,
                        prompt = prompt,
                        tags = newTags,
                        error = null // 清除错误
                    )
                }
                Log.d("CreatePersona", "AI Generation Success: $desc")

            } catch (e: Exception) {
                Log.e("CreatePersona", "JSON Parse Error: $jsonStr", e)
                // 6. 降级处理：如果万一解析失败，把原始内容填进去
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        description = jsonStr,
                        error = "格式解析部分失败，已填入原始内容"
                    )
                }
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