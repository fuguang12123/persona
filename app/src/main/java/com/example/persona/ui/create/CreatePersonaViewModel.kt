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

// 定义界面状态 (UiState)
// 这是一个数据类，包含了页面显示所需的所有信息。
// 这里的默认值对应了刚进页面时的空白状态。
data class CreatePersonaUiState(
    val name: String = "",
    val description: String = "",
    val prompt: String = "",
    val avatarUrl: String = "",
    val tags: List<String> = emptyList(),
    val isEditMode: Boolean = false, // 是否是编辑模式（修改旧的）
    val isLoading: Boolean = false,  // 是否正在加载（显示转圈）
    val isSuccess: Boolean = false,  // 是否操作成功（用于触发页面关闭）
    val error: String? = null        // 错误信息（用于弹 Toast）
)

// @HiltViewModel 表示这个类由 Hilt 自动注入依赖
@HiltViewModel
class CreatePersonaViewModel @Inject constructor(
    private val repository: PersonaRepository, // 数据仓库，用于存取数据
    private val postService: PostService,      // 网络服务，用于上传图片
    savedStateHandle: SavedStateHandle,        // 用于获取页面跳转传递的参数
    @ApplicationContext private val context: Context // Android 上下文，用于文件操作
) : ViewModel() {

    // _uiState 是可变的内部状态 (MutableStateFlow)
    private val _uiState = MutableStateFlow(CreatePersonaUiState())
    // uiState 是公开的只读状态 (StateFlow)，供 UI 层观察
    val uiState = _uiState.asStateFlow()

    // 尝试从导航参数中获取 "editId"，如果有值，说明是进来编辑旧数据的
    private val editId: Long? = savedStateHandle.get<Long>("editId")?.takeIf { it != -1L }

    // 初始化块：ViewModel 创建时自动执行
    init {
        if (editId != null) {
            loadPersonaForEdit(editId)
        }
    }

    // 从数据库加载旧数据以供编辑
    private fun loadPersonaForEdit(id: Long) {
        // viewModelScope.launch 开启一个协程（后台线程），避免卡死主界面
        viewModelScope.launch {
            // 先设置加载状态为 true
            _uiState.update { it.copy(isLoading = true, isEditMode = true) }

            // 调用仓库获取数据
            val persona = repository.getPersona(id)

            if (persona != null) {
                // 如果找到了，把数据填入 uiState
                _uiState.update {
                    it.copy(
                        name = persona.name,
                        description = persona.description ?: "",
                        prompt = persona.promptTemplate ?: "",
                        avatarUrl = persona.avatarUrl ?: "",
                        // 分割字符串标签为 List
                        tags = persona.personalityTags?.split(",", "，")?.filter { t -> t.isNotBlank() } ?: emptyList(),
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "加载智能体信息失败") }
            }
        }
    }

    // --- 下面是给 UI 调用的方法 ---

    // 用户在输入框打字时调用，更新 name 状态
    fun onNameChange(v: String) = _uiState.update { it.copy(name = v) }
    fun onDescChange(v: String) = _uiState.update { it.copy(description = v) }
    fun onPromptChange(v: String) = _uiState.update { it.copy(prompt = v) }

    // 添加标签
    fun addTag(tag: String) {
        if (tag.isNotBlank() && !_uiState.value.tags.contains(tag)) {
            // it.tags + tag 创建了一个包含新标签的新列表
            _uiState.update { it.copy(tags = it.tags + tag) }
        }
    }

    // 删除标签
    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags - tag) }
    }

    // 上传头像逻辑
    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 1. 把 URI (相册路径) 转为实际的文件对象
                val file = UriUtils.uriToFile(context, uri)
                if (file != null) {
                    // 2. 包装成 HTTP 请求需要的格式 (Multipart)
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    // 3. 调用网络接口上传
                    val response = postService.uploadImage(body)

                    if (response.code == 200 && response.data != null) {
                        // 4. 上传成功，拿到图片的 URL 并更新状态
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

    // [核心逻辑] AI 一键生成：调用 AI 生成人设 JSON 并自动填表
    fun generateAiDescription() {
        val name = _uiState.value.name
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "请先输入名字") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. 调用 Repository 让 AI 生成描述 (此时是个 JSON 字符串)
            val jsonStr = repository.generateDescription(name)

            // 2. 检查是否为空
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
                // 3. 清洗数据：防止 AI 返回 Markdown 格式代码块 (```json ... ```)
                val cleanJsonStr = jsonStr.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                // 4. 解析 JSON 字符串
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

                // 5. 成功！一次性把所有字段填入 UI
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        description = desc,
                        prompt = prompt,
                        tags = newTags,
                        error = null // 清除之前的错误
                    )
                }
                Log.d("CreatePersona", "AI Generation Success: $desc")

            } catch (e: Exception) {
                Log.e("CreatePersona", "JSON Parse Error: $jsonStr", e)
                // 6. 降级处理：如果 AI 返回的不是标准 JSON，解析失败了，
                // 我们不报错，而是把原始文字直接填入 "描述" 框，让用户自己改。
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

    // 提交按钮逻辑
    fun submit() {
        val state = _uiState.value
        // 简单校验
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "名字不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 构建数据对象
            val persona = Persona(
                name = state.name,
                description = state.description,
                promptTemplate = state.prompt,
                avatarUrl = state.avatarUrl,
                personalityTags = state.tags.joinToString(","), // 把 List 转成 "tag1,tag2" 字符串存库
                isPublic = true
            )

            // 根据是否是编辑模式调用不同接口
            val success = if (state.isEditMode && editId != null) {
                repository.updatePersona(editId, persona.copy(id = editId))
            } else {
                repository.createPersona(persona)
            }

            // 更新结果状态
            if (success) {
                _uiState.update { it.copy(isSuccess = true, isLoading = false) }
            } else {
                _uiState.update { it.copy(error = "操作失败，请重试", isLoading = false) }
            }
        }
    }
}