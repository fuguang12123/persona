package com.example.persona.ui.chat

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.entity.UserMemoryEntity
import com.example.persona.data.model.ChatMessage
import com.example.persona.data.remote.AuthService
import com.example.persona.data.repository.ChatRepository
import com.example.persona.data.repository.PersonaRepository
import com.example.persona.data.service.LocalLLMService
import com.example.persona.utils.AudioPlayerManager
import com.example.persona.utils.AudioRecorderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authService: AuthService, // ✅ [New] 注入 AuthService 以获取网络数据
    private val audioRecorder: AudioRecorderManager,
    val audioPlayer: AudioPlayerManager,
    private val localLLMService: LocalLLMService
) : ViewModel() {

    // UI State
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var isSending by mutableStateOf(false)

    // AI 信息
    var personaName: String? by mutableStateOf("Chat")
    var personaAvatarUrl by mutableStateOf("")

    // 用户信息
    var userAvatarUrl by mutableStateOf("")
    var currentUserName by mutableStateOf("User")

    private var currentPersonaId: Long = 0
    var isRecording by mutableStateOf(false)
        private set

    var isPrivateMode by mutableStateOf(false)
    var memories: Flow<List<UserMemoryEntity>> = emptyFlow()

    private val typedMessageIds = mutableSetOf<Long>()
    private var isInitialLoad = true

    private var messagesJob: Job? = null

    init {
        // 1. 监听本地缓存 (UI 响应源)
        loadUserProfile()

        // 2. [New] 异步从后端拉取最新数据并存入本地 (数据同步源)
        fetchRemoteUserProfile()
    }

    private fun loadUserProfile() {
        // 监听头像
        viewModelScope.launch {
            userPreferencesRepository.avatarUrl.collect { url ->
                // ✅ Log 移到这里，这才是真正读到数据的时候
                Log.d("ChatViewModel", "Flow update avatar: $url")
                userAvatarUrl = url ?: ""
            }
        }
        // 监听昵称
        viewModelScope.launch {
            userPreferencesRepository.userName.collect { name ->
                currentUserName = name ?: "User"
            }
        }
    }

    /**
     * [New] 从服务器获取个人信息并更新到本地存储
     */
    private fun fetchRemoteUserProfile() {
        viewModelScope.launch {
            try {
                val response = authService.getMyProfile()
                if (response.isSuccessful && response.body()?.code == 200) {
                    val userDto = response.body()?.data
                    if (userDto != null) {
                        Log.d("ChatViewModel", "Remote fetch success: ${userDto.avatarUrl}")
                        // ✅ 将网络数据写入 UserPreferencesRepository
                        // 写入后，上面的 loadUserProfile 中的 Flow 会自动触发，更新 UI
                        userPreferencesRepository.saveUserInfo(
                            avatar = userDto.avatarUrl,
                            name = userDto.nickname
                        )
                    }
                } else {
                    Log.w("ChatViewModel", "Remote fetch failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Remote fetch error", e)
            }
        }
    }

    fun initChat(personaId: Long) {
        currentPersonaId = personaId
        isInitialLoad = true
        loadMessages()

        viewModelScope.launch {
            memories = chatRepository.getMemoriesStream(personaId)
        }
        viewModelScope.launch { chatRepository.refreshHistory(personaId) }
        loadPersonaInfo()
    }

    // ... (以下所有方法保持不变) ...

    private fun loadMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesStream(currentPersonaId, isPrivateMode).collectLatest { entities ->
                if (isInitialLoad) {
                    entities.forEach {
                        if (!it.isUser && it.status == 2) typedMessageIds.add(it.id)
                    }
                    isInitialLoad = false
                }

                val latestAiMsg = if (isPrivateMode) {
                    entities.filter { !it.isUser && it.status == 2 }.minByOrNull { it.id }
                } else {
                    entities.filter { !it.isUser && it.status == 2 }.maxByOrNull { it.id }
                }

                entities.filter { !it.isUser && it.status == 2 }.forEach { entity ->
                    if (latestAiMsg == null || entity.id != latestAiMsg.id) {
                        typedMessageIds.add(entity.id)
                    }
                }

                val newUiMessages = entities.map { entity ->
                    val isLatestAi = (latestAiMsg != null && entity.id == latestAiMsg.id)
                    val needsTyping = isLatestAi
                            && !typedMessageIds.contains(entity.id)
                            && !entity.content.isNullOrEmpty()

                    val displayContent = if (needsTyping) "" else entity.content

                    ChatMessage(
                        id = entity.id,
                        role = entity.role,
                        content = entity.content,
                        msgType = entity.msgType,
                        mediaUrl = entity.mediaUrl,
                        duration = entity.duration,
                        status = entity.status,
                        localFilePath = entity.localFilePath,
                        displayContent = displayContent
                    )
                }

                messages = newUiMessages

                newUiMessages.find {
                    it.id == latestAiMsg?.id
                            && !typedMessageIds.contains(it.id)
                            && !it.content.isNullOrEmpty()
                }?.let { msgToAnimate ->
                    startTypewriter(msgToAnimate)
                }
            }
        }
    }

    fun togglePrivateMode() {
        isPrivateMode = !isPrivateMode
        isInitialLoad = true
        loadMessages()

        if (isPrivateMode) {
            viewModelScope.launch { localLLMService.initModel() }
        }
    }

    private fun startTypewriter(msg: ChatMessage) {
        typedMessageIds.add(msg.id)
        viewModelScope.launch {
            val fullText = msg.content ?: ""
            val delayTime = if (fullText.length > 50) 10L else 30L

            for (i in 1..fullText.length) {
                kotlinx.coroutines.delay(delayTime)
                updateMessageDisplayContent(msg.id, fullText.take(i))
            }
            updateMessageDisplayContent(msg.id, fullText)
        }
    }

    private fun updateMessageDisplayContent(msgId: Long, text: String) {
        messages = messages.map {
            if (it.id == msgId) it.copy(displayContent = text) else it
        }
    }

    private fun loadPersonaInfo() {
        viewModelScope.launch {
            val persona = personaRepository.getPersona(currentPersonaId)
            if (persona != null) { personaName = persona.name; personaAvatarUrl = persona.avatarUrl ?: "" }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        isSending = true
        viewModelScope.launch {
            chatRepository.sendMessage(currentPersonaId, text, false, isPrivateMode)
            isSending = false
        }
    }

    fun sendImageGenRequest(text: String) {
        if (text.isBlank()) return
        isSending = true
        viewModelScope.launch {
            chatRepository.sendMessage(currentPersonaId, text, true, false)
            isSending = false
        }
    }

    fun startRecording(): Boolean {
        val success = audioRecorder.startRecording()
        if (success) isRecording = true
        return success
    }

    fun stopRecording() {
        isRecording = false
        viewModelScope.launch {
            val result = audioRecorder.stopRecording()
            if (result != null) {
                val (file, duration) = result
                if (duration >= 1) {
                    isSending = true
                    try {
                        chatRepository.sendAudioMessage(currentPersonaId, file, duration)
                    } finally { isSending = false }
                }
            }
        }
    }

    fun cancelRecording() { isRecording = false; audioRecorder.cancelRecording() }
    fun playAudio(url: String) { audioPlayer.play(url) }
}