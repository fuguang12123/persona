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
    private val personaRepository: PersonaRepository
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

        // 1. 启动监听
        viewModelScope.launch {
            chatRepository.getMessagesStream(personaId).collect { entities ->
                // [Fix] 移除了 sortedBy，信任数据库的 DESC 排序
                // 直接映射为 UI 模型，无需重新排序，性能更好
                messages = entities.map { entity ->
                    ChatMessage(
                        id = entity.id,
                        role = entity.role,
                        content = entity.content
                    )
                }
            }
        }

        // 2. 触发刷新
        viewModelScope.launch {
            chatRepository.refreshHistory(personaId)
        }

        // 3. 加载角色信息
        loadPersonaInfo()
    }

    private fun loadPersonaInfo() {
        viewModelScope.launch {
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
            chatRepository.sendMessage(currentPersonaId, text)
            isSending = false
        }
    }
}