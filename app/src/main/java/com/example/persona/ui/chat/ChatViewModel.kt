package com.example.persona.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.ChatMessage
import com.example.persona.data.remote.PersonaService
import com.example.persona.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository, // ✅ 替换了 ChatService
    private val personaService: PersonaService  // 暂时保留，用于获取头像/名字
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
        // 只要 Repository 修改了数据库，这里就会自动收到最新的列表
        viewModelScope.launch {
            chatRepository.getMessagesStream(personaId).collect { entities ->
                // 将 Entity (数据库模型) 转换为 UI 模型
                messages = entities.map { entity ->
                    ChatMessage(
                        role = entity.role,
                        content = entity.content
                        // 如果 ChatMessage 有 id 或 timestamp，也可以在这里赋值
                        // id = entity.id
                    )
                }
            }
        }

        // 2. 触发刷新：从云端拉取最新历史 (静默更新)
        viewModelScope.launch {
            chatRepository.refreshHistory(personaId)
        }

        // 3. 加载角色信息 (保持原有逻辑)
        loadPersonaInfo()
    }

    private fun loadPersonaInfo() {
        viewModelScope.launch {
            try {
                val resp = personaService.getPersona(currentPersonaId)
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val persona = resp.body()!!.data!!
                    personaName = persona.name
                    // 获取头像 URL
                    personaAvatarUrl = if (!persona.avatarUrl.isNullOrBlank()) {
                        persona.avatarUrl
                    } else {
                        "https://api.dicebear.com/7.x/avataaars/png?seed=${persona.name}"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        isSending = true
        viewModelScope.launch {
            // Repository 会先插入本地(UI秒变)，再请求网络，再更新回复
            chatRepository.sendMessage(currentPersonaId, text)
            isSending = false
        }
    }
}