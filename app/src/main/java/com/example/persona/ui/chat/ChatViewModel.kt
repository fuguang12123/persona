package com.example.persona.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.entity.UserMemoryEntity
import com.example.persona.data.model.ChatMessage
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
    private val audioRecorder: AudioRecorderManager,
    val audioPlayer: AudioPlayerManager,
    private val localLLMService: LocalLLMService
) : ViewModel() {

    // UI State
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var isSending by mutableStateOf(false)
    var personaName: String? by mutableStateOf("Chat")
    var personaAvatarUrl by mutableStateOf("")
    private var currentPersonaId: Long = 0
    var isRecording by mutableStateOf(false)
        private set

    var isPrivateMode by mutableStateOf(false)
    var memories: Flow<List<UserMemoryEntity>> = emptyFlow()

    private val typedMessageIds = mutableSetOf<Long>()
    private var isInitialLoad = true // 用于控制进入页面或切换模式时的动画行为

    private var messagesJob: Job? = null

    fun initChat(personaId: Long) {
        currentPersonaId = personaId
        // 初始化时，视为初始加载
        isInitialLoad = true
        loadMessages()

        viewModelScope.launch {
            memories = chatRepository.getMemoriesStream(personaId)
        }
        viewModelScope.launch { chatRepository.refreshHistory(personaId) }
        loadPersonaInfo()
    }

    // [Modified] 核心修改：只允许最新的 AI 回复触发打字机动画
    private fun loadMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesStream(currentPersonaId, isPrivateMode).collectLatest { entities ->

                // 1. 如果是初始加载（或刚切换模式），将当前所有“已完成”的消息标记为已打印，防止历史记录播放动画
                if (isInitialLoad) {
                    entities.forEach {
                        if (!it.isUser && it.status == 2) typedMessageIds.add(it.id)
                    }
                    isInitialLoad = false
                }

                // 2. 找出“最新”的一条 AI 回复（且状态为完成）
                // 私密模式 ID 为负数时间戳：数值越小越新 (如 -2000 比 -1000 新) -> minByOrNull
                // 云端模式 ID 为正数自增/时间戳：数值越大越新 -> maxByOrNull
                val latestAiMsg = if (isPrivateMode) {
                    entities.filter { !it.isUser && it.status == 2 }.minByOrNull { it.id }
                } else {
                    entities.filter { !it.isUser && it.status == 2 }.maxByOrNull { it.id }
                }

                // 3. 关键步骤：将所有“非最新”的 AI 消息强制标记为已打印
                // 这确保了即使数据流刷新，旧消息也不会重新触发动画
                entities.filter { !it.isUser && it.status == 2 }.forEach { entity ->
                    if (latestAiMsg == null || entity.id != latestAiMsg.id) {
                        typedMessageIds.add(entity.id)
                    }
                }

                // 4. 映射 UI 数据
                val newUiMessages = entities.map { entity ->
                    // 判断条件：是最新的一条 AI 消息 + 状态完成 + 还没打印过 + 内容不为空
                    val isLatestAi = (latestAiMsg != null && entity.id == latestAiMsg.id)
                    val needsTyping = isLatestAi
                            && !typedMessageIds.contains(entity.id)
                            && !entity.content.isNullOrEmpty()

                    // 如果需要打字机效果，初始显示内容为空，交由协程逐字更新
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

                // 5. 触发打字机协程
                // 只针对那个被筛选出来的、需要动画的消息启动协程
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
        // [Modified] 切换模式时重置为 true，这样该模式下的旧消息不会播放动画
        isInitialLoad = true
        loadMessages()

        if (isPrivateMode) {
            viewModelScope.launch { localLLMService.initModel() }
        }
    }

    private fun startTypewriter(msg: ChatMessage) {
        // 立即标记为已处理，防止重复触发
        typedMessageIds.add(msg.id)

        viewModelScope.launch {
            val fullText = msg.content ?: ""
            // 根据文本长度动态调整速度
            val delayTime = if (fullText.length > 50) 10L else 30L

            for (i in 1..fullText.length) {
                // 如果用户切走了或消息被删了（简单判断），停止更新
                // 实际项目中可以加更复杂的取消逻辑
                kotlinx.coroutines.delay(delayTime)
                updateMessageDisplayContent(msg.id, fullText.take(i))
            }
            // 确保最后显示完整
            updateMessageDisplayContent(msg.id, fullText)
        }
    }

    private fun updateMessageDisplayContent(msgId: Long, text: String) {
        // 使用 copy 更新状态，触发 Compose 重组
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