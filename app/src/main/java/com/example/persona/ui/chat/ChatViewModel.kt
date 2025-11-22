package com.example.persona.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.ChatMessage
import com.example.persona.data.repository.ChatRepository
import com.example.persona.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository // ✅ 修正：使用 Repository
) : ViewModel() {

    // UI State
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var isSending by mutableStateOf(false)

    var personaName: String? by mutableStateOf("Chat")
        private set

    var personaAvatarUrl by mutableStateOf("")
        private set

    private var currentPersonaId: Long = 0

    fun initChat(personaId: Long) {
        currentPersonaId = personaId

        // 1. 启动监听：观察本地数据库 (SSOT 核心)
        viewModelScope.launch {
            chatRepository.getMessagesStream(personaId).collect { entities ->
                messages = entities.map { entity ->
                    ChatMessage(
                        role = entity.role,
                        content = entity.content
                    )
                }
            }
        }

        // 2. 触发刷新：从云端拉取最新历史 (静默更新)
        viewModelScope.launch {
            chatRepository.refreshHistory(personaId)
        }

        // 3. 加载角色信息
        loadPersonaInfo()
    }

    private fun loadPersonaInfo() {
        viewModelScope.launch {
            // ✅ 修正：Repository 已经处理了 BaseResponse，直接返回 Persona? 对象
            // 这里不需要 .body() 或 .isSuccess()
            val persona = personaRepository.getPersona(currentPersonaId)

            if (persona != null) {
                personaName = persona.name
                personaAvatarUrl = if (!persona.avatarUrl.isNullOrBlank()) {
                    persona.avatarUrl
                } else {
                    "https://api.dicebear.com/7.x/avataaars/png?seed=${persona.name}"
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        isSending = true
        viewModelScope.launch {
            // 仓库层处理了乐观更新，UI 会自动刷新
            chatRepository.sendMessage(currentPersonaId, text)
            isSending = false
        }
    }
}