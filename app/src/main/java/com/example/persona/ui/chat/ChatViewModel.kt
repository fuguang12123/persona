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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

// [New] 定义 UI 事件
sealed interface ChatUiEvent {
    data object ScrollToBottom : ChatUiEvent
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authService: AuthService,
    private val audioRecorder: AudioRecorderManager,
    val audioPlayer: AudioPlayerManager,
    private val localLLMService: LocalLLMService
) : ViewModel() {

    // --- UI State ---
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var isSending by mutableStateOf(false)
    var personaName: String? by mutableStateOf("Chat")
    var personaAvatarUrl by mutableStateOf("")
    var userAvatarUrl by mutableStateOf("")
    var currentUserName by mutableStateOf("User")
    private var currentPersonaId: Long = 0
    var isRecording by mutableStateOf(false)
        private set
    var isPrivateMode by mutableStateOf(false)
    var memories: Flow<List<UserMemoryEntity>> = emptyFlow()

    // --- UI Events ---
    // [New] 使用 Channel 发送一次性 UI 事件 (如滚动)
    private val _uiEvent = Channel<ChatUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    // --- Pagination ---
    private val _messageLimit = MutableStateFlow(20)

    // --- Typewriter State ---
    private val typedMessageIds = mutableSetOf<Long>()
    private val animatingMessageIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private var isInitialLoad = true

    // 记录 ViewModel 初始化时间戳
    private val viewInitTime = System.currentTimeMillis()

    private var messagesJob: Job? = null

    init {
        loadUserProfile()
        fetchRemoteUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userPreferencesRepository.avatarUrl.collect { url -> userAvatarUrl = url ?: "" }
        }
        viewModelScope.launch {
            userPreferencesRepository.userName.collect { name -> currentUserName = name ?: "User" }
        }
    }

    private fun fetchRemoteUserProfile() {
        viewModelScope.launch {
            try {
                val response = authService.getMyProfile()
                if (response.isSuccessful && response.body()?.code == 200) {
                    val userDto = response.body()?.data
                    if (userDto != null) {
                        userPreferencesRepository.saveUserInfo(avatar = userDto.avatarUrl, name = userDto.nickname)
                    }
                }
            } catch (e: Exception) { Log.e("ChatViewModel", "Remote fetch error", e) }
        }
    }

    fun initChat(personaId: Long) {
        currentPersonaId = personaId
        isInitialLoad = true
        _messageLimit.value = 20
        loadMessages()
        viewModelScope.launch { memories = chatRepository.getMemoriesStream(personaId) }
        if (!isPrivateMode) {
            viewModelScope.launch { chatRepository.refreshHistory(personaId) }
        }
        loadPersonaInfo()
    }

    fun loadMoreMessages() {
        if (messages.size < _messageLimit.value) return
        _messageLimit.value += 20
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            _messageLimit.flatMapLatest { limit ->
                chatRepository.getMessagesStream(currentPersonaId, isPrivateMode, limit)
            }.collectLatest { entities ->

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

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

                val newUiMessages = entities.map { entity ->
                    val isLatestAi = (latestAiMsg != null && entity.id == latestAiMsg.id)
                    val isNewMessage = isMessageNewerThanInit(entity.createdAt, dateFormat)

                    val needsTyping = isLatestAi
                            && !typedMessageIds.contains(entity.id)
                            && !entity.content.isNullOrEmpty()
                            && isNewMessage

                    val currentDisplayContent = messages.find { it.id == entity.id }?.displayContent
                    val isAnimating = animatingMessageIds.contains(entity.id)

                    val displayContent = if (isAnimating) {
                        currentDisplayContent ?: ""
                    } else if (needsTyping) {
                        ""
                    } else {
                        entity.content
                    }

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
                            && it.displayContent.isNullOrEmpty()
                }?.let { msgToAnimate ->
                    startTypewriter(msgToAnimate)
                }
            }
        }
    }

    private fun isMessageNewerThanInit(timeStr: String, parser: SimpleDateFormat): Boolean {
        return try {
            val msgTime = parser.parse(timeStr)?.time ?: 0L
            msgTime > (viewInitTime - 2000)
        } catch (e: Exception) {
            false
        }
    }

    fun togglePrivateMode() {
        isPrivateMode = !isPrivateMode
        isInitialLoad = true
        _messageLimit.value = 20
        loadMessages()
        if (isPrivateMode) {
            viewModelScope.launch { localLLMService.initModel() }
        } else {
            viewModelScope.launch { chatRepository.refreshHistory(currentPersonaId) }
        }
    }

    private fun startTypewriter(msg: ChatMessage) {
        typedMessageIds.add(msg.id)
        animatingMessageIds.add(msg.id)

        // [New] AI 开始回复时，强制滚动到底部
        viewModelScope.launch { _uiEvent.send(ChatUiEvent.ScrollToBottom) }

        viewModelScope.launch {
            try {
                val fullText = msg.content ?: ""
                val delayTime = if (fullText.length > 50) 10L else 30L
                for (i in 1..fullText.length) {
                    if (!animatingMessageIds.contains(msg.id)) break
                    kotlinx.coroutines.delay(delayTime)
                    updateMessageDisplayContent(msg.id, fullText.take(i))
                }
                updateMessageDisplayContent(msg.id, fullText)
            } finally {
                animatingMessageIds.remove(msg.id)
            }
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

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        isSending = true

        // [New] 用户点击发送时，立即滚动到底部
        viewModelScope.launch { _uiEvent.send(ChatUiEvent.ScrollToBottom) }

        viewModelScope.launch {
            chatRepository.sendMessage(currentPersonaId, text, false, isPrivateMode)
            isSending = false
        }
    }

    fun sendImageGenRequest(text: String) {
        if (text.isBlank()) return
        isSending = true

        // [New] 滚动
        viewModelScope.launch { _uiEvent.send(ChatUiEvent.ScrollToBottom) }

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
                    // [New] 语音发送时滚动
                    _uiEvent.send(ChatUiEvent.ScrollToBottom)

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