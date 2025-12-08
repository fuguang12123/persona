package com.example.persona.data.repository

import android.util.Log
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.dao.UserMemoryDao
import com.example.persona.data.local.entity.ChatMessageEntity
import com.example.persona.data.local.entity.PersonaEntity
import com.example.persona.data.local.entity.UserMemoryEntity
import com.example.persona.data.remote.ChatMessageDto
import com.example.persona.data.remote.ChatService
import com.example.persona.data.remote.SendMessageRequest
import com.example.persona.data.service.LocalLLMService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @class com.example.persona.data.repository.ChatRepository
 * @description 聊天领域的**核心数据仓库**。
 *
 * ## 架构地位
 * 它是连接 Data Layer (数据库/网络) 与 Domain/UI Layer 的桥梁。
 * 严格遵循 **单一数据源 (SSOT)** 原则：
 * 1. **UI 只读数据库**：UI 层观察的 Flow 数据永远来自 Room 数据库，绝不直接使用网络请求的返回值。
 * 2. **网络只写数据库**：网络请求的结果用于写入或更新数据库，从而间接驱动 UI 刷新。
 *
 * ## 核心职责
 * 1. **双模式路由**：根据 `isPrivateMode` 开关，智能路由到云端 API 或 端侧 LocalLLM。
 * 2. **数据同步与去重**：实现复杂的 `mergeAndSyncMessages` 算法，确保云端历史与本地缓存的一致性。
 * 3. **乐观更新 (Optimistic UI)**：发送消息时先落库 "发送中" 状态，提升用户体验，再根据结果更新状态。
 * 4. **多模态支持**：统一处理文本、图片生图 (ImageGen) 和 语音文件的上传与发送。
 *
 * @see com.example.persona.data.local.dao.ChatDao
 * @see com.example.persona.ui.chat.ChatViewModel
 */
@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val personaDao: PersonaDao,
    private val userMemoryDao: UserMemoryDao,
    private val api: ChatService,
    private val userPrefs: UserPreferencesRepository,
    private val localLLMService: LocalLLMService
) {

    // ============================================================================================
    // Read Path (读取路径) - SSOT 的体现
    // ============================================================================================

    /**
     * **获取消息数据流 (SSOT 核心)**
     *
     * UI 调用此方法后获得一个 Flow。Repository 内部根据模式决定监听数据库的哪一部分。
     * - **云端模式**: 监听 ID >= 0 的数据。
     * - **私密模式**: 监听 ID < 0 的数据 (本地生成的数据使用负数 ID 以示区分)。
     *
     * @param limit 分页限制，由 ViewModel 动态控制。
     * @return Flow 管道，只要数据库变动，管道就会吐出最新的排序好的列表。
     */
    suspend fun getMessagesStream(personaId: Long, isPrivateMode: Boolean, limit: Int): Flow<List<ChatMessageEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()

        // 路由逻辑：选择不同的 DAO 查询方法
        val flow = if (isPrivateMode) {
            chatDao.getPrivateChatHistory(userId, personaId, limit)
        } else {
            chatDao.getCloudChatHistory(userId, personaId, limit)
        }

        // 数据加工：确保按时间倒序排列 (最新的在前面)，适配 LazyColumn(reverseLayout=true)
        return flow.map { list -> list.sortedByDescending { it.createdAt } }
    }

    /**
     * 获取记忆流 (仅私密模式)
     * 监听本地向量数据库提取出的用户画像/记忆。
     */
    suspend fun getMemoriesStream(personaId: Long): Flow<List<UserMemoryEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return userMemoryDao.getAllMemories(userId, personaId)
    }

    // ============================================================================================
    // Write Path (写入路径) - 数据同步
    // ============================================================================================

    /**
     * **全量刷新历史 (Full Sync)**
     * * 通常在进入聊天页面或下拉刷新时调用。
     * 策略：以**服务器数据为权威 (Authority)**，清洗本地数据。
     */
    suspend fun refreshHistory(personaId: Long) {
        val userId = getCurrentUserId() ?: return
        try {
            // 1. 发起网络请求
            val response = api.getHistory(userId, personaId)

            if (response.isSuccess() && response.data != null) {
                val serverMessages = response.data.map { it.toEntity() }

                // 2. 获取本地大范围历史数据 (2000条)，用于比对
                // 必须拿足够多的本地数据，否则可能导致本地有旧数据没被清理掉，形成重复
                val localMessages = chatDao.getCloudChatHistory(userId, personaId, 2000).first()

                // 3. 执行强力去重与合并算法
                mergeAndSyncMessages(localMessages, serverMessages)
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Refresh failed: ${e.message}")
        }
    }

    /**
     * **[核心算法] 强力去重与同步**
     * * 解决痛点：
     * 1. **重复消息**: 网络波动可能导致客户端重试发送，服务器存了两条，或者本地 ID 与服务器 ID 不一致。
     * 2. **影子消息**: 本地"发送中"的临时消息可能因为没有及时收到回执而滞留。
     * 3. **时钟偏差**: 本地时间和服务器时间可能不一致。
     *
     * 逻辑：
     * 1. **精确匹配**: ID 相同 -> 是同一条消息，保留本地的文件路径 (避免重复下载)，状态更新为 server 状态。
     * 2. **模糊匹配**:
     * - 文本: 内容相同即视为相同 (忽略微小的时间差异)。
     * - 多媒体: 时间在 2分钟容差内即视为相同。
     * -> 匹配上的本地消息视为"影子/缓存"，**统统删除**，用服务器消息替换。
     * 3. **事务提交**: 使用 Room 的 `@Transaction` 原子性地执行 `delete` 和 `insert`。
     */
    private suspend fun mergeAndSyncMessages(localMsgs: List<ChatMessageEntity>, serverMsgs: List<ChatMessageEntity>) {
        val toInsert = mutableListOf<ChatMessageEntity>()
        val toDelete = mutableListOf<ChatMessageEntity>()
        val matchedLocalIds = mutableSetOf<Long>() // 记录已经匹配上的本地 ID

        for (serverMsg in serverMsgs) {
            // --- 策略 A: ID 精确匹配 (最理想情况) ---
            val exactMatch = localMsgs.find { it.id == serverMsg.id }

            if (exactMatch != null) {
                // 合并逻辑：使用服务器的数据作为基准，但保留本地已经下载好的文件路径
                val mergedMsg = serverMsg.copy(
                    localFilePath = exactMatch.localFilePath ?: serverMsg.localFilePath,
                    status = 2 // 强制确认为成功状态
                )
                toInsert.add(mergedMsg)
                matchedLocalIds.add(exactMatch.id)
                continue
            }

            // --- 策略 B: 模糊匹配 (清理本地影子消息) ---
            // 找出所有内容相同、类型相同，且不是我们刚才精确匹配过的本地消息
            val shadows = localMsgs.filter { local ->
                local.id !in matchedLocalIds &&
                        local.role == serverMsg.role &&
                        local.msgType == serverMsg.msgType &&
                        local.status != 3 && // 保护：不要误删了用户"发送失败"正准备重试的消息
                        (
                                // B1. 文本消息：只看内容！忽略时间。
                                // 解决场景：本地发完是 UTC+8 时间，服务器存的是 UTC 时间，时间戳对不上的问题。
                                (local.msgType == 0 && local.content == serverMsg.content && local.content.isNotEmpty()) ||

                                        // B2. 图片/语音：内容通常为空或无法比较，只能依赖时间。
                                        // 设定 120秒 的宽容度。
                                        (local.msgType != 0 && isTimeClose(local.createdAt, serverMsg.createdAt))
                                )
            }

            if (shadows.isNotEmpty()) {
                // 找到了替身！说明这条服务器消息其实就是我们本地发的那条。
                // 动作：把本地的影子删掉，把服务器的正式版存进去。
                toDelete.addAll(shadows)
                shadows.forEach { matchedLocalIds.add(it.id) }

                // 继承资产：如果本地影子有文件路径（比如刚录完的音），继承给新消息，省去下载。
                val existingPath = shadows.firstNotNullOfOrNull { it.localFilePath }

                val mergedMsg = serverMsg.copy(
                    localFilePath = existingPath ?: serverMsg.localFilePath,
                    status = 2
                )
                toInsert.add(mergedMsg)
            } else {
                // --- 策略 C: 纯新消息 ---
                toInsert.add(serverMsg)
            }
        }

        // 4. 原子提交：只要有变动，就通过 DAO 事务写入
        if (toDelete.isNotEmpty() || toInsert.isNotEmpty()) {
            chatDao.syncMessages(toDelete, toInsert)
        }
    }

    /**
     * 辅助方法：判断两个时间字符串是否在 120秒 误差范围内
     */
    private fun isTimeClose(time1: String, time2: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val t1 = format.parse(time1)?.time ?: 0L
            val t2 = format.parse(time2)?.time ?: 0L
            kotlin.math.abs(t1 - t2) < 120 * 1000 // 2分钟容差
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================================================
    // User Actions (发送逻辑)
    // ============================================================================================

    /**
     * **通用发送方法 (文本/生图)**
     * * 统一了 私密模式(Private) 和 云端模式(Cloud) 的发送入口。
     * * @param isImageGen true=生图请求, false=普通对话
     * @param isPrivateMode true=端侧模型推理, false=请求后端 API
     */
    suspend fun sendMessage(personaId: Long, content: String, isImageGen: Boolean = false, isPrivateMode: Boolean = false) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        // 1. 乐观更新 (Optimistic Update)
        // 在请求发出前，先在本地插入一条 "发送中(status=0)" 的消息。
        // 这会让 UI 立即显示气泡，给用户极佳的响应速度感。
        val tempUserMsg = ChatMessageEntity(userId = userId, personaId = personaId, role = "user", content = content, createdAt = timestamp, status = 0, msgType = 0)

        // 私密模式用负数时间戳做 ID，云端模式让 Room 自动生成 ID (或者用临时 ID)
        val localId = if (isPrivateMode) {
            chatDao.insertMessage(tempUserMsg.copy(id = -System.currentTimeMillis()))
        } else {
            chatDao.insertMessage(tempUserMsg)
        }

        // --- 分支 A: 私密模式 (Local LLM) ---
        if (isPrivateMode) {
            // 插入一条空的 AI 回复占位符 (status=1 正在生成)
            val aiMsgId = -System.currentTimeMillis() - 1
            val tempAiMsg = ChatMessageEntity(id = aiMsgId, userId = userId, personaId = personaId, role = "assistant", content = "", createdAt = timestamp, status = 1)
            chatDao.insertMessage(tempAiMsg)

            val sb = StringBuilder()
            try {
                // 调用本地模型流式生成
                localLLMService.generateResponse(userId, personaId, content).collect { chunk ->
                    sb.append(chunk)
                    // 实时更新数据库 -> 触发 Flow -> UI 打字机效果 (虽然 UI 层也有打字机，但这里保证了数据层是渐进的)
                    chatDao.updateMessage(tempAiMsg.copy(content = sb.toString(), status = 2))
                }
                // 生成结束后，异步总结记忆
                CoroutineScope(Dispatchers.IO).launch { localLLMService.summarizeAndSaveMemory(userId, personaId, "User: $content\nAI: $sb") }
            } catch (e: Exception) {
                chatDao.updateMessage(tempAiMsg.copy(content = "Error: ${e.message}", status = 3))
            }
        }
        // --- 分支 B: 云端模式 (Server API) ---
        else {
            try {
                val request = SendMessageRequest(userId, personaId, content, isImageGen)
                val response = api.sendMessage(request)

                if (response.isSuccess() && response.data != null) {
                    val aiMsgDto = response.data

                    // 2. 请求成功：把本地那条"发送中"的消息改为"成功(status=2)"
                    chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 2))

                    // 3. 插入 AI 的回复
                    val aiMsgEntity = aiMsgDto.toEntity()

                    // 简单查重：防止服务器返回太慢，导致本地 refreshHistory 已经先把这条消息插进去了
                    val exists = chatDao.getCloudChatHistory(userId, personaId, 100).first().any { it.id == aiMsgEntity.id }
                    if (!exists) {
                        chatDao.insertMessage(aiMsgEntity)
                    }
                } else {
                    // 失败处理：状态置为 3 (红色感叹号)
                    chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
                }
            } catch (e: Exception) {
                chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
            }
        }
    }

    /**
     * **发送语音消息**
     * * 这是一个典型的 "临时 ID -> 正式 ID" 替换场景。
     */
    suspend fun sendAudioMessage(personaId: Long, audioFile: File, duration: Int) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        // 1. 插入本地临时消息 (立即上屏)
        val tempUserMsg = ChatMessageEntity(
            userId = userId, personaId = personaId, role = "user",
            content = "[语音]", createdAt = timestamp, status = 0, msgType = 2, duration = duration, localFilePath = audioFile.absolutePath
        )
        val localId = chatDao.insertMessage(tempUserMsg)

        try {
            // 构建 Multipart 请求体
            val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
            val userIdPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val personaIdPart = personaId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val durationPart = duration.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            // 2. 上传文件
            val response = api.sendAudio(filePart, userIdPart, personaIdPart, durationPart)

            if (response.isSuccess() && response.data != null) {
                val serverMsgEntity = response.data.toEntity().copy(
                    localFilePath = audioFile.absolutePath, // 关键：把本地文件路径赋给服务器消息
                    status = 2
                )

                // 3. 原子替换 (Atomic Replacement)
                // 使用事务：删掉 ID=localId 的临时消息，插入 ID=serverMsgEntity.id 的正式消息
                // 这样既修正了 ID，又保留了文件路径，且 UI 不会闪烁。
                chatDao.replaceLocalWithServerMessage(
                    localMsg = tempUserMsg.copy(id = localId),
                    serverMsg = serverMsgEntity
                )
                // 顺便刷新一下历史，看看 AI 有没有回复
                refreshHistory(personaId)
            } else {
                throw Exception("Server error")
            }
        } catch (e: Exception) {
            chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
        }
    }

    /**
     * **同步会话列表**
     * 用于首页展示。拉取所有聊过天的 Persona 及其最后一条消息。
     */
    suspend fun syncConversationList() {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getConversations(userId)
            if (response.isSuccess() && response.data != null) {
                val list = response.data
                // 更新 Personas 表的基本信息
                val personas = list.map { dto ->
                    PersonaEntity(dto.personaId, 0L, dto.name, dto.avatarUrl, "", "", true)
                }
                personaDao.insertAll(personas)

                // 激进策略：为列表里的每个人都触发一次历史刷新，保证点进去就是最新的
                list.forEach { dto -> refreshHistory(dto.personaId) }
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Sync failed: ${e.message}")
        }
    }

    private suspend fun getCurrentUserId(): Long? {
        val idStr = userPrefs.userId.first()
        return idStr?.toLongOrNull()
    }

    private fun ChatMessageDto.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = this.id, userId = this.userId, personaId = this.personaId, role = this.role, content = this.content,
            createdAt = this.createdAt ?: "", msgType = this.msgType ?: 0, mediaUrl = this.mediaUrl, duration = this.duration ?: 0, status = 2
        )
    }
}