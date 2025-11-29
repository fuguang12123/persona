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

    // [New] 切换关注
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