package com.example.persona.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.Persona
import com.example.persona.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaDetailUiState(
    val isLoading: Boolean = true,
    val persona: Persona? = null,
    val error: String? = null,

    // UI 辅助字段
    val tags: List<String> = emptyList(), // 解析后的性格标签列表
    val isOwner: Boolean = false,         // 是否是创建者 (控制编辑按钮显示)
    val creatorName: String = "Unknown"   // 创建者显示名
)

@HiltViewModel
class PersonaDetailViewModel @Inject constructor(
    private val repository: PersonaRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaDetailUiState())
    val uiState = _uiState.asStateFlow()

    fun loadPersona(personaId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. 获取智能体详情
            val persona = repository.getPersona(personaId)

            if (persona != null) {
                // 2. 获取当前登录用户 ID，判断权限
                // userPrefs.userId 是 Flow<String?>，需要转为 Long
                val currentUserIdStr = userPrefs.userId.first()
                val currentUserId = currentUserIdStr?.toLongOrNull() ?: -1L

                // 核心逻辑：如果智能体的 userId 等于当前登录用户 ID，则是号主
                val isOwner = (persona.userId == currentUserId)

                // 3. 解析标签 (兼容中文逗号、英文逗号、空格分隔)
                val rawTags = persona.personalityTags ?: ""
                val tagList = rawTags.split(",", "，", " ").filter { it.isNotBlank() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        persona = persona,
                        isOwner = isOwner, // 只有为 true 时，UI 才显示编辑按钮
                        tags = tagList,
                        creatorName = "User ${persona.userId}" // 暂时显示 ID
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "未找到该智能体")
                }
            }
        }
    }
}