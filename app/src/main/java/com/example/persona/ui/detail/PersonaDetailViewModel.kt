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
    val tags: List<String> = emptyList(),
    val isOwner: Boolean = false,
    val creatorName: String = "Unknown",

    // [New] 是否已关注
    val isFollowed: Boolean = false
)

@HiltViewModel
class PersonaDetailViewModel @Inject constructor(
    private val repository: PersonaRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    /**
     * @class com.example.persona.ui.detail.PersonaDetailViewModel
     * @description Persona 详情页的状态管理：加载详情、解析标签、判断是否为本人、查询并切换关注状态（乐观更新）。通过 Repository 写入/查询数据，配合 DataStore 获取当前用户信息，实现 UI 的实时更新与交互一致性。对应《最终作业.md》社交广场与直接对话入口的资料展示与关注交互。
     * @author Persona Team <persona@project.local>
     * @since 2025-11-30
     * @see com.example.persona.data.repository.PersonaRepository
     * @关联功能 REQ-B3 资料与关注；REQ-B4 直接对话入口
     */

    private val _uiState = MutableStateFlow(PersonaDetailUiState())
    val uiState = _uiState.asStateFlow()

    fun loadPersona(personaId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. 获取详情
            val persona = repository.getPersona(personaId)
            // 2. [New] 获取关注状态
            val isFollowed = repository.getFollowStatus(personaId)

            if (persona != null) {
                val currentUserIdStr = userPrefs.userId.first()
                val currentUserId = currentUserIdStr?.toLongOrNull() ?: -1L
                val isOwner = (persona.userId == currentUserId)
                val rawTags = persona.personalityTags ?: ""
                val tagList = rawTags.split(",", "，", " ").filter { it.isNotBlank() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        persona = persona,
                        isOwner = isOwner,
                        tags = tagList,
                        creatorName = "User ${persona.userId}",
                        isFollowed = isFollowed // 更新状态
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "未找到该智能体")
                }
            }
        }
    }


    fun toggleFollow(personaId: Long) {
        val old = _uiState.value.isFollowed
        _uiState.update { it.copy(isFollowed = !old) } // 乐观更新
        viewModelScope.launch {
            val success = repository.toggleFollow(personaId)
            if (!success) {
                _uiState.update { it.copy(isFollowed = old) } // 失败回滚
            }
        }
    }
}
