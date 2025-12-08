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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

/**
 * @class com.example.persona.ui.chat.ChatViewModel
 * @description 聊天功能的核心 ViewModel。
 *
 * ## 核心架构思想：单一数据源 (SSOT) + 响应式编程 (Reactive)
 *
 * 这个 ViewModel 不会手动去 "Add" 或 "Remove" 消息列表里的数据。
 * 相反，它建立了一个从 Room 数据库到 UI 的**永久性数据管道 (Flow)**。
 *
 * **数据流向 (Unidirectional Data Flow):**
 * 1. **Write (写入)**: UI 事件 -> 调用 Repository -> 写入 Room 数据库 (Insert/Update)。
 * 2. **Read (读取)**: Room 数据库变动 -> 触发 Flow -> ViewModel 收到新 List -> 更新 Compose State -> UI 自动刷新。
 *
 * ## 主要职责
 * 1. **状态容器**: 持有聊天界面所需的所有状态 (消息列表、输入框状态、录音状态等)。
 * 2. **数据转换**: 将数据库实体 (Entity) 转换为 UI 模型 (ChatMessage)，并在此过程中注入打字机动画逻辑。
 * 3. **业务调度**: 协调语音录制、音频播放、私密/云端模式切换等副作用。
 *
 * @see com.example.persona.data.repository.ChatRepository
 * @see com.example.persona.ui.chat.ChatScreen
 */
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

    // ============================================================================================
    // UI State (Compose 状态变量)
    // ============================================================================================

    /**
     * **消息列表 (核心数据源)**
     * 使用 mutableStateOf 而不是 LiveData，以便 Compose 可以直接通过快照系统智能重组。
     * 当这个变量被赋值时，ChatScreen 会自动刷新。
     */
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())

    /**
     * **发送中状态**
     * 用于控制发送按钮的可用性，以及显示加载 Loading 动画。
     * 在发送网络请求或处理音频时置为 true。
     */
    var isSending by mutableStateOf(false)

    // --- 角色与用户信息 ---
    var personaName: String? by mutableStateOf("Chat")
    var personaAvatarUrl by mutableStateOf("")
    var userAvatarUrl by mutableStateOf("")
    var currentUserName by mutableStateOf("User")

    /**
     * **当前会话 ID**
     * 并非 State，因为它的改变不直接驱动 UI 刷新，而是驱动数据流的重新订阅 (initChat)。
     */
    private var currentPersonaId: Long = 0

    // ============================================================================================
    // 模式控制 (Mode Control)
    // ============================================================================================

    /**
     * **模式开关**
     * - `true`: **端侧私密模式** (Local LLM)。数据存储在本地，不上传云端，消息 ID 为负数。
     * - `false`: **云端模式** (Server API)。数据与服务器同步，消息 ID 为正数。
     * 切换此状态会触发整个数据管道的重置 (See: togglePrivateMode)。
     */
    var isPrivateMode by mutableStateOf(false)

    /**
     * **共生记忆流**
     * 仅在私密模式下有效。实时观察本地向量数据库中提取出的用户画像/记忆。
     */
    var memories: Flow<List<UserMemoryEntity>> = emptyFlow()

    // ============================================================================================
    // 语音交互 (Voice Interaction)
    // ============================================================================================

    /**
     * **录音状态**
     * 控制 UI 是否显示全屏的录音波形遮罩 (Overlay)。
     * `private set` 确保状态只能由 ViewModel 内部逻辑修改，保证状态安全性。
     */
    var isRecording by mutableStateOf(false)
        private set

    /**
     * **取消录音判定**
     * 当用户手指滑出录音按钮范围时置为 true，UI 根据此状态显示 "松开取消" 的提示。
     */
    var isVoiceCancelling by mutableStateOf(false)

    /**
     * **实时音量振幅 (0f ~ 1f)**
     * 直接透传 Recorder 的 Flow，用于驱动录音波形动画的起伏。
     */
    val voiceAmplitude = audioRecorder.amplitude

    // ============================================================================================
    // 分页与加载控制 (Pagination & Loading)
    // ============================================================================================

    /**
     * **分页限制 (Reactive Limit)**
     * 使用 `MutableStateFlow` 而不是普通变量。
     * **妙处**: 当我们修改这个值 (如 `value += 20`)，`loadMessages` 中的 `flatMapLatest` 操作符
     * 会自动感知，并重新向数据库请求最新的 N 条数据，无需手动再次调用查询方法。
     */
    private val _messageLimit = MutableStateFlow(20)

    // ============================================================================================
    // 打字机动画状态 (Typewriter State)
    // ============================================================================================

    /**
     * **[黑名单] 已展示过动画的消息 ID**
     * 记录所有已经完成打字机动画的消息。
     * 作用：防止用户滚动列表或刷新数据时，历史消息重复播放打字动画。
     */
    private val typedMessageIds = mutableSetOf<Long>()

    /**
     * **[进行中] 正在播放动画的消息 ID**
     * 记录当前正在协程中逐字更新的消息。
     * 作用：
     * 1. 避免 `loadMessages` 刷新数据时，用数据库的全文覆盖掉正在打字的残缺文本。
     * 2. 防止同一个消息被启动两个打字机协程。
     * 使用 `synchronizedSet` 保证多协程并发安全。
     */
    private val animatingMessageIds = Collections.synchronizedSet(mutableSetOf<Long>())

    /**
     * **初次加载标记**
     * 用于在进入页面瞬间，将数据库里已有的所有 AI 消息直接加入黑名单，跳过动画。
     */
    private var isInitialLoad = true

    /**
     * **页面初始化时间戳**
     * 辅助判断：只有比这个时间晚创建的消息，才被认为是“新收到”的消息，才有资格播放动画。
     */
    private val viewInitTime = System.currentTimeMillis()

    /**
     * **消息流协程句柄**
     * 它是数据管道的“总开关”。
     * 每次切换模式或重置会话时，必须先通过它 `cancel()` 掉旧的管道，防止数据错乱。
     */
    private var messagesJob: Job? = null

    init {
        // 初始化时，从 DataStore 加载用户的头像和昵称
        loadUserProfile()
        // 尝试从网络同步最新的用户资料
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

    /**
     * ============================================================================================
     * 核心业务逻辑区域
     * ============================================================================================
     */

    /**
     * **初始化会话**
     * 进入聊天页面时调用。
     * 1. 重置分页 (`limit=20`)。
     * 2. 启动数据监听管道 (`loadMessages`)。
     * 3. 如果是云端模式，静默触发一次 `refreshHistory` (全量同步)，修正本地数据偏差。
     */
    fun initChat(personaId: Long) {
        currentPersonaId = personaId
        isInitialLoad = true
        _messageLimit.value = 20

        // 启动监听
        loadMessages()

        // 监听记忆
        viewModelScope.launch { memories = chatRepository.getMemoriesStream(personaId) }

        // 云端模式下，进入即刷新 (Full Sync)
        if (!isPrivateMode) {
            viewModelScope.launch { chatRepository.refreshHistory(personaId) }
        }
        loadPersonaInfo()
    }

    /**
     * **加载更多历史消息**
     * 这是一个非常轻量的操作：只修改 `_messageLimit` 的值。
     * 剩下的工作由 `flatMapLatest` 自动完成 (自动断开旧流，连接新流)。
     */
    fun loadMoreMessages() {
        if (messages.size < _messageLimit.value) return // 到底了
        _messageLimit.value += 20
    }

    /**
     * **[核心引擎] 加载并监听消息流**
     * * 这里实现了 SSOT (单一数据源) 模式。
     * 我们不直接操作 `messages` 列表，而是通过观察 Repository 返回的 Flow 来被动更新。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadMessages() {
        // 1. [防冲突] 取消上一次的监听任务。
        // 这对于 "切换私密模式" 尤为重要，确保云端流和本地流不会同时向 UI 输送数据。
        messagesJob?.cancel()

        messagesJob = viewModelScope.launch {
            // 2. [动态流切换]
            // flatMapLatest 监听 _messageLimit 的变化。
            // 当 limit 变了，它会取消上一条 getMessagesStream，并用新的 limit 重新查询。
            _messageLimit.flatMapLatest { limit ->
                chatRepository.getMessagesStream(currentPersonaId, isPrivateMode, limit)
            }.collectLatest { entities -> // 3. [数据收集] 只要数据库变动，这里就会执行

                // --- A. 初始化逻辑 ---
                if (isInitialLoad) {
                    // 刚进页面时，不要播放动画。把所有已完成的消息拉黑。
                    entities.forEach {
                        if (!it.isUser && it.status == 2) typedMessageIds.add(it.id)
                    }
                    isInitialLoad = false
                }

                // --- B. 寻找打字机动画的"候选人" ---
                // 私密模式(负ID)越小越新；云端模式(正ID)越大越新。
                val latestAiMsg = if (isPrivateMode) {
                    entities.filter { !it.isUser && it.status == 2 }.minByOrNull { it.id }
                } else {
                    entities.filter { !it.isUser && it.status == 2 }.maxByOrNull { it.id }
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

                // --- C. 构建 UI 模型 (Entity -> ChatMessage) ---
                val newUiMessages = entities.map { entity ->
                    // 判定1: 是否是最新的 AI 消息
                    val isLatestAi = (latestAiMsg != null && entity.id == latestAiMsg.id)
                    // 判定2: 是否是新收到的 (比页面打开时间晚)
                    val isNewMessage = isMessageNewerThanInit(entity.createdAt, dateFormat)

                    // **综合判定: 是否需要打字机动画**
                    val needsTyping = isLatestAi
                            && !typedMessageIds.contains(entity.id) // 没打过
                            && !entity.content.isNullOrEmpty()      // 有内容
                            && isNewMessage

                    // 获取当前 UI 上这条消息正在显示的内容 (用于保持动画进度)
                    val currentDisplayContent = messages.find { it.id == entity.id }?.displayContent
                    val isAnimating = animatingMessageIds.contains(entity.id)

                    // **核心状态映射逻辑**
                    val displayContent = if (isAnimating) {
                        // Case 1: 正在动画中 -> 必须保持现状！
                        // 即使数据库里已经是全文了，UI 上也要显示"打了一半"的文本，等待动画协程去更新它。
                        currentDisplayContent ?: ""
                    } else if (needsTyping) {
                        // Case 2: 需要动画但还没开始 -> 先置空。
                        // 这会制造一个"空白气泡"，随后下方的 startTypewriter 会接手填充它。
                        ""
                    } else {
                        // Case 3: 不需要动画/动画已结束 -> 直接显示数据库里的全文。
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
                        displayContent = displayContent // 使用计算后的显示内容
                    )
                }

                // --- D. 刷新 UI ---
                messages = newUiMessages

                // --- E. 触发副作用 (启动打字机) ---
                // 查找那个被我们置为空字符串的消息，启动动画协程
                newUiMessages.find {
                    it.id == latestAiMsg?.id
                            && !typedMessageIds.contains(it.id)
                            && !it.content.isNullOrEmpty()
                            && it.displayContent.isNullOrEmpty() // 对应上面的 "" 赋值
                }?.let { msgToAnimate ->
                    startTypewriter(msgToAnimate)
                }
            }
        }
    }

    private fun isMessageNewerThanInit(timeStr: String, parser: SimpleDateFormat): Boolean {
        return try {
            val msgTime = parser.parse(timeStr)?.time ?: 0L
            msgTime > (viewInitTime - 2000) // 2秒容错
        } catch (e: Exception) {
            false
        }
    }

    /**
     * **切换模式 (私密 <-> 云端)**
     * 1. 翻转 boolean 状态。
     * 2. 重置分页和加载标记。
     * 3. **重启数据管道** (`loadMessages`) -> 这会调用 Repository 不同的查询方法。
     * 4. 触发各自的初始化逻辑 (加载本地模型 或 刷新云端历史)。
     */
    fun togglePrivateMode() {
        isPrivateMode = !isPrivateMode
        isInitialLoad = true
        _messageLimit.value = 20
        loadMessages() // 重启管道

        if (isPrivateMode) {
            // 私密模式：初始化本地 LLM
            viewModelScope.launch { localLLMService.initModel() }
        } else {
            // 云端模式：拉取服务器历史
            viewModelScope.launch { chatRepository.refreshHistory(currentPersonaId) }
        }
    }

    /**
     * **[独立协程] 打字机动画引擎**
     * 这是一个 "Fire-and-forget" 的任务，独立于数据管道运行。
     * 它通过不断修改 `messages` 列表中的 `displayContent` 字段来实现动画。
     */
    private fun startTypewriter(msg: ChatMessage) {
        // 1. 标记状态：防止重复触发 & 保护动画不被数据刷新打断
        typedMessageIds.add(msg.id)
        animatingMessageIds.add(msg.id)

        viewModelScope.launch {
            try {
                val fullText = msg.content ?: ""
                // 2. 动态速度控制：长文(>50字)快一点(10ms)，短文慢一点(30ms)拟人化
                val delayTime = if (fullText.length > 50) 10L else 30L

                // 3. 逐字输出循环
                for (i in 1..fullText.length) {
                    // 安全检查：如果中途被移除(如清空会话)，立即停止
                    if (!animatingMessageIds.contains(msg.id)) break

                    kotlinx.coroutines.delay(delayTime)
                    // 更新 UI 显示前 i 个字
                    updateMessageDisplayContent(msg.id, fullText.take(i))
                }
                // 4. 兜底：确保最后显示完整文本
                updateMessageDisplayContent(msg.id, fullText)
            } finally {
                // 5. 清理状态：移除动画标记。
                // 这一步至关重要！一旦移除，loadMessages 里的逻辑就会接管，直接显示数据库里的全文。
                animatingMessageIds.remove(msg.id)
            }
        }
    }

    /**
     * **更新单条消息的显示内容**
     * 由于 Compose 观察的是 List 的引用变化，我们需要创建一个新的 List 副本。
     */
    private fun updateMessageDisplayContent(msgId: Long, text: String) {
        messages = messages.map { if (it.id == msgId) it.copy(displayContent = text) else it }
    }

    private fun loadPersonaInfo() {
        viewModelScope.launch {
            val persona = personaRepository.getPersona(currentPersonaId)
            if (persona != null) { personaName = persona.name; personaAvatarUrl = persona.avatarUrl ?: "" }
        }
    }

    // ============================================================================================
    // 用户交互动作 (Actions)
    // ============================================================================================

    /**
     * **发送文本消息**
     * 这里的逻辑非常简单，因为它是 SSOT 模式：
     * 1. 告诉 Repository 发消息。
     * 2. Repository 负责写入数据库。
     * 3. Room 负责触发 Flow。
     * 4. `loadMessages` 负责更新 UI。
     * ViewModel 不需要手动 `messages.add(newMessage)`。
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        isSending = true

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(currentPersonaId, text, false, isPrivateMode)
            } finally {
                isSending = false
            }
        }
    }

    fun sendImageGenRequest(text: String) {
        if (text.isBlank()) return
        isSending = true

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(currentPersonaId, text, true, false)
            } finally {
                isSending = false
            }
        }
    }

    // --- 语音录制流程 ---

    fun startRecording(): Boolean {
        isVoiceCancelling = false
        val success = audioRecorder.startRecording()
        if (success) isRecording = true
        return success
    }

    fun stopRecording() {
        isRecording = false
        isVoiceCancelling = false
        viewModelScope.launch {
            // 停止录音并获取文件
            val result = audioRecorder.stopRecording()
            if (result != null) {
                val (file, duration) = result
                // 只有时长有效才发送，避免误触
                if (duration > 0) {
                    isSending = true
                    try {
                        // 发送音频文件到后端
                        chatRepository.sendAudioMessage(currentPersonaId, file, duration)
                    } finally { isSending = false }
                }
            }
        }
    }

    fun cancelRecording() {
        isRecording = false
        isVoiceCancelling = false
        // 直接丢弃录音文件，不进行任何网络请求
        audioRecorder.cancelRecording()
    }

    fun playAudio(url: String) { audioPlayer.play(url) }
}