package com.example.persona.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.ChatMessage
import com.example.persona.data.repository.ChatRepository
import com.example.persona.data.repository.PersonaRepository
import com.example.persona.utils.AudioPlayerManager
import com.example.persona.utils.AudioRecorderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val audioRecorder: AudioRecorderManager,
    val audioPlayer: AudioPlayerManager
) : ViewModel() {

    // UI State
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var isSending by mutableStateOf(false)
    var personaName: String? by mutableStateOf("Chat")
    var personaAvatarUrl by mutableStateOf("")
    private var currentPersonaId: Long = 0
    var isRecording by mutableStateOf(false)
        private set
    private val typedMessageIds = mutableSetOf<Long>()
    private var isInitialLoad = true

    fun initChat(personaId: Long) {
        currentPersonaId = personaId
        viewModelScope.launch {
            chatRepository.getMessagesStream(personaId).collectLatest { entities ->
                if (isInitialLoad) {
                    entities.forEach { if (!it.isUser && it.status == 2) typedMessageIds.add(it.id) }
                    isInitialLoad = false
                }
                val newUiMessages = entities.map { entity ->
                    val needsTyping = !entity.isUser && entity.status == 2 && !typedMessageIds.contains(entity.id) && !entity.content.isNullOrEmpty()
                    val displayContent = if (needsTyping) "" else entity.content
                    ChatMessage(entity.id, entity.role, entity.content, entity.msgType, entity.mediaUrl, entity.duration, entity.status, entity.localFilePath, displayContent)
                }
                messages = newUiMessages
                newUiMessages.filter { !it.role.equals("user") && it.status == 2 && !typedMessageIds.contains(it.id) && !it.content.isNullOrEmpty() }
                    .forEach { startTypewriter(it) }
            }
        }
        viewModelScope.launch { chatRepository.refreshHistory(personaId) }
        loadPersonaInfo()
    }

    private fun startTypewriter(msg: ChatMessage) {
        typedMessageIds.add(msg.id)
        viewModelScope.launch {
            val fullText = msg.content ?: ""
            val delayTime = if (fullText.length > 50) 20L else 50L
            for (i in 1..fullText.length) {
                delay(delayTime)
                updateMessageDisplayContent(msg.id, fullText.take(i))
            }
            updateMessageDisplayContent(msg.id, fullText)
        }
    }

    private fun updateMessageDisplayContent(msgId: Long, text: String) {
        messages = messages.map { if (it.id == msgId) it.copy(displayContent = text) else it }
    }

    private fun loadPersonaInfo() {
        viewModelScope.launch {
            val persona = personaRepository.getPersona(currentPersonaId)
            if (persona != null) { personaName = persona.name; personaAvatarUrl = persona.avatarUrl ?: "" }
        }
    }

    // 文本发送
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        isSending = true
        viewModelScope.launch {
            chatRepository.sendMessage(currentPersonaId, text, false)
            isSending = false
        }
    }

    // 生图请求发送
    fun sendImageGenRequest(text: String) {
        if (text.isBlank()) return
        isSending = true
        viewModelScope.launch {
            chatRepository.sendMessage(currentPersonaId, text, true)
            isSending = false
        }
    }

    fun startRecording(): Boolean {
        val success = audioRecorder.startRecording()
        if (success) isRecording = true
        return success
    }

    // ✅ [Fix] 语音发送时也需要切换 isSending 状态，以触发 UI 加载动画
    fun stopRecording() {
        isRecording = false
        viewModelScope.launch {
            val result = audioRecorder.stopRecording()
            if (result != null) {
                val (file, duration) = result
                if (duration >= 1) {
                    isSending = true // 开始加载
                    try {
                        chatRepository.sendAudioMessage(currentPersonaId, file, duration)
                    } finally {
                        isSending = false // 结束加载
                    }
                }
            }
        }
    }

    fun cancelRecording() {
        isRecording = false
        audioRecorder.cancelRecording()
    }

    fun playAudio(urlOrPath: String) {
        audioPlayer.play(urlOrPath)
    }
}