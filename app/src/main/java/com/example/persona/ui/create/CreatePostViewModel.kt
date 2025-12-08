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

// 定义页面状态：包含 UI 需要显示的所有数据
data class CreatePostUiState(
    val content: String = "",              // 输入框的内容
    val imageUrls: List<String> = emptyList(), // 已添加的图片 URL 列表

    val personas: List<PersonaDto> = emptyList(), // 可选的智能体列表 (用于横向滚动条)
    val selectedPersonaId: Long? = null,          // 当前选中的智能体 ID
    val searchQuery: String = "",                 // 搜索框的文字

    val isAiGenerated: Boolean = false,    // 标记是否使用了 AI 功能
    val isLoading: Boolean = false,        // 是否正在加载 (API 请求中)
    val error: String? = null,             // 错误信息 (用于弹 Toast)
    val isSuccess: Boolean = false,        // 是否发布成功 (用于关闭页面)

    val isMagicEditing: Boolean = false    // 专门的状态：是否正在进行 "AI 润色"
)

// @HiltViewModel: 标记这是一个 Hilt 管理的 ViewModel
@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postService: PostService,     // 网络服务：发帖、生图、上传
    private val postRepository: PostRepository, // 仓库：处理 AI 润色等复杂逻辑
    private val userPrefs: UserPreferencesRepository, // 获取当前用户 ID
    savedStateHandle: SavedStateHandle,       // 获取页面跳转传来的参数
    @ApplicationContext private val context: Context // 用于处理文件 URI
) : ViewModel() {

    // 1. 状态管理核心
    // _uiState: 内部可变状态 (MutableStateFlow)，只有 ViewModel 能改
    private val _uiState = MutableStateFlow(CreatePostUiState())
    // uiState: 外部只读状态 (StateFlow)，UI 只能看不能改
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    // 缓存：保存从服务器拉取的完整列表，用于本地搜索过滤
    private var allPersonasCache: List<PersonaDto> = emptyList()
    // 尝试获取传参：如果是从 "详情页" 跳转过来的，会带一个 personaId
    private val preSelectedPersonaId: Long? = savedStateHandle.get<Long>("personaId")?.takeIf { it != -1L }

    // 初始化块：ViewModel 创建时立即执行
    init {
        fetchPersonas()
    }

    // 拉取所有可选的智能体
    private fun fetchPersonas() {
        viewModelScope.launch {
            try {
                val response = postService.getPersonas()
                if (response.code == 200 && response.data != null) {
                    val list = response.data
                    // 1. 存入内存缓存，方便后续搜索过滤
                    allPersonasCache = list
                    // 2. 决定默认选中谁：如果有传参ID就用传参的，否则默认选中第一个
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

    // 本地搜索逻辑
    // 当搜索框文字变化时调用
    fun onSearchQueryChange(query: String) {
        // 1. 更新搜索框文字状态
        _uiState.update { it.copy(searchQuery = query) }

        // 2. 在内存中过滤 (Local Filter)，不发网络请求，速度快
        val filteredList = if (query.isBlank()) {
            allPersonasCache // 如果搜空的，显示全部
        } else {
            allPersonasCache.filter {
                // 名字包含 OR 描述包含 (忽略大小写)
                it.name.contains(query, ignoreCase = true) ||
                        (it.description?.contains(query, ignoreCase = true) == true)
            }
        }
        // 3. 更新列表显示
        _uiState.update { it.copy(personas = filteredList) }
    }

    // 选中某个智能体
    fun selectPersona(id: Long) {
        _uiState.update { it.copy(selectedPersonaId = id) }
    }

    // 输入框内容变化
    fun onContentChange(newContent: String) {
        _uiState.update { it.copy(content = newContent) }
    }

    // AI 生图逻辑
    fun generateAiImage() {
        val prompt = _uiState.value.content
        // 校验：必须有文字才能生图
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
                // 调用后端接口
                val response = postService.generateAiImage(GenerateImageRequest(prompt))
                if (response.code == 200 && response.data != null) {
                    // 成功：把返回的图片 URL 追加到列表中
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

    // 上传本地图片逻辑
    fun uploadLocalImages(uris: List<Uri>) {
        // 校验数量限制
        if (_uiState.value.imageUrls.size + uris.size > 9) {
            _uiState.update { it.copy(error = "最多只能选择9张图片") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val uploadedUrls = mutableListOf<String>()

            // 遍历所有选中的图片，逐个上传
            for (uri in uris) {
                try {
                    // 1. Uri 转 File (利用 context 读取流)
                    val file = UriUtils.uriToFile(context, uri)
                    if (file != null) {
                        // 2. 封装成 Multipart 格式
                        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                        // 3. 上传
                        val response = postService.uploadImage(body)
                        if (response.code == 200 && response.data != null) {
                            uploadedUrls.add(response.data) // 收集成功的 URL
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Upload", "Fail for $uri", e)
                }
            }

            // 如果至少有一张传成功了
            if (uploadedUrls.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        imageUrls = it.imageUrls + uploadedUrls, // 追加到列表
                        isAiGenerated = false,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "图片上传失败") }
            }
        }
    }

    // 删除图片
    fun removeImage(index: Int) {
        _uiState.update {
            val newList = it.imageUrls.toMutableList()
            if (index in newList.indices) {
                newList.removeAt(index)
            }
            it.copy(imageUrls = newList)
        }
    }

    // 发布动态
    fun createPost() {
        // 校验
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
                // 获取当前用户 ID (模拟登录)
                val userIdStr = userPrefs.userId.first() ?: "10086"
                val userId = userIdStr.toLongOrNull() ?: 10086L

                // 构造请求体
                val request = CreatePostRequest(
                    content = _uiState.value.content,
                    imageUrls = _uiState.value.imageUrls
                )

                // 调用发布接口
                val response = postService.createPost(
                    userId = userId,
                    personaId = selectedId,
                    request = request
                )

                if (response.code == 200 && response.data != null) {
                    // 成功：设置标记，UI 会监听到并关闭页面
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

    // 清除错误信息 (UI 弹完 Toast 后调用)
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // -------------------------------------------------------------
    // 核心亮点：AI 魔法润色
    // -------------------------------------------------------------
    fun magicEdit() {
        val content = _uiState.value.content
        if (content.isBlank()) {
            _uiState.update { it.copy(error = "请先写点草稿吧~") }
            return
        }

        // 1. 获取当前选中的智能体信息 (用于给 AI 提供上下文)
        val selectedId = _uiState.value.selectedPersonaId
        val selectedPersona = allPersonasCache.find { it.id == selectedId }
        val personaName = selectedPersona?.name
        val description = selectedPersona?.description
        val tags = selectedPersona?.personalityTags

        viewModelScope.launch {
            // 设置专门的 "润色中" 状态，UI 会显示魔法棒转圈圈
            _uiState.update { it.copy(isMagicEditing = true, error = null) }

            // 2. 调用 Repository 封装好的 AI 润色逻辑
            val result = postRepository.magicEdit(content, personaName, description, tags)

            if (result.isSuccess) {
                val aiResult = result.getOrNull()

                // 3. [清洗数据]：AI 有时会自作聪明加引号，这里把它去掉
                val cleanedResult = aiResult?.trim()?.removePrefix("\"")?.removeSuffix("\"") ?: content

                // 4. 直接覆盖输入框内容
                _uiState.update {
                    it.copy(
                        content = cleanedResult,
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