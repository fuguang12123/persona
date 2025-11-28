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

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val personaDao: PersonaDao,
    private val userMemoryDao: UserMemoryDao,
    private val api: ChatService,
    private val userPrefs: UserPreferencesRepository,
    private val localLLMService: LocalLLMService
) {
    suspend fun getMessagesStream(personaId: Long, isPrivateMode: Boolean): Flow<List<ChatMessageEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        val flow = if (isPrivateMode) {
            chatDao.getPrivateChatHistory(userId, personaId)
        } else {
            chatDao.getCloudChatHistory(userId, personaId)
        }

        // [Fix] 强制在内存中对结果进行排序，确保是最新的在前面 (DESC)
        // 配合 UI 的 reverseLayout = true，实现最新的在底部
        return flow.map { list ->
            list.sortedByDescending { it.createdAt }
        }
    }

    suspend fun getMemoriesStream(personaId: Long): Flow<List<UserMemoryEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return userMemoryDao.getAllMemories(userId, personaId)
    }

    suspend fun syncConversationList() {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getConversations(userId)
            if (response.isSuccess() && response.data != null) {
                val list = response.data
                val personas = list.map { dto -> PersonaEntity(dto.personaId, 0L, dto.name, dto.avatarUrl, "", "", true) }
                personaDao.insertAll(personas)
                list.forEach { dto -> refreshHistory(dto.personaId) }
            }
        } catch (e: Exception) { Log.e("ChatRepo", "Sync failed: ${e.message}") }
    }

    // [Fix] 修复云端消息重复显示的问题
    suspend fun refreshHistory(personaId: Long) {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getHistory(userId, personaId)
            if (response.isSuccess() && response.data != null) {
                val serverMessages = response.data.map { it.toEntity() }

                // 1. 获取当前本地的云端消息 (用于比对去重)
                val localMessages = chatDao.getCloudChatHistory(userId, personaId).first()

                // 2. 执行智能合并
                mergeAndSyncMessages(localMessages, serverMessages)
            }
        } catch (e: Exception) { Log.e("ChatRepo", "Refresh failed: ${e.message}") }
    }

    // [New] 核心去重逻辑
    private suspend fun mergeAndSyncMessages(localMsgs: List<ChatMessageEntity>, serverMsgs: List<ChatMessageEntity>) {
        val toInsert = mutableListOf<ChatMessageEntity>()
        val toDelete = mutableListOf<ChatMessageEntity>()

        // 记录已经匹配过的本地消息 ID，防止一对多误删
        val matchedLocalIds = mutableSetOf<Long>()

        for (serverMsg in serverMsgs) {
            // 检查本地是否已经有这条消息 (ID 完全一致)
            val isExactMatch = localMsgs.any { it.id == serverMsg.id }

            if (!isExactMatch) {
                // ID 不一致，说明可能是新消息，或者是我们本地刚发出去的 (ID=0) 尚未同步的消息
                if (serverMsg.role == "user") {
                    // 尝试寻找内容相同、但 ID 不同的本地消息 (这就是那个"重复的影子")
                    val duplicateLocal = localMsgs.firstOrNull { local ->
                        local.role == "user" &&
                                local.content == serverMsg.content &&
                                local.id != serverMsg.id && // ID 不同
                                local.id !in matchedLocalIds && // 还没被匹配过
                                local.status != 3 // 不是发送失败的消息
                    }

                    if (duplicateLocal != null) {
                        // 找到了！这个 local 就是我们要删掉的替身
                        toDelete.add(duplicateLocal)
                        matchedLocalIds.add(duplicateLocal.id)
                    }
                }
                // 无论如何，把服务器的真消息插进去
                toInsert.add(serverMsg)
            } else {
                // 已经有了，覆盖更新一下以防万一
                toInsert.add(serverMsg)
            }
        }

        // 批量执行数据库操作
        if (toDelete.isNotEmpty()) {
            toDelete.forEach { chatDao.deleteMessage(it) }
        }
        if (toInsert.isNotEmpty()) {
            chatDao.insertMessages(toInsert)
        }
    }

    suspend fun sendMessage(
        personaId: Long,
        content: String,
        isImageGen: Boolean = false,
        isPrivateMode: Boolean = false
    ) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        // 私密模式用负数ID，云端模式用0 (Room自增)
        val msgId = if (isPrivateMode) -System.currentTimeMillis() else 0L

        val tempUserMsg = ChatMessageEntity(
            id = msgId,
            userId = userId,
            personaId = personaId,
            role = "user",
            content = content,
            createdAt = timestamp,
            status = 2,
            msgType = 0
        )

        chatDao.insertMessage(tempUserMsg)

        if (isPrivateMode) {
            // ... 私密模式逻辑保持不变 ...
            val aiMsgId = -System.currentTimeMillis() - 1
            val tempAiMsg = ChatMessageEntity(
                id = aiMsgId, userId = userId, personaId = personaId, role = "assistant",
                content = "", createdAt = timestamp, status = 1
            )
            chatDao.insertMessage(tempAiMsg)

            val sb = StringBuilder()
            try {
                localLLMService.generateResponse(userId, personaId, content).collect { chunk ->
                    sb.append(chunk)
                    chatDao.updateMessage(tempAiMsg.copy(content = sb.toString(), status = 2))
                }
                CoroutineScope(Dispatchers.IO).launch {
                    localLLMService.summarizeAndSaveMemory(userId, personaId, "User: $content\nAI: $sb")
                }
            } catch (e: Exception) {
                chatDao.updateMessage(tempAiMsg.copy(content = "Error: ${e.message}", status = 3))
            }
        } else {
            // === ☁️ 云端模式 ===
            try {
                Log.d("ChatRepo", "Sending Cloud Msg: $content")
                val request = SendMessageRequest(userId, personaId, content, isImageGen)
                val response = api.sendMessage(request)

                if (response.isSuccess() && response.data != null) {
                    // 插入 AI 回复
                    chatDao.insertMessage(response.data.toEntity())
                    // 发送成功后，通常建议触发一次同步，确保 ID 对齐 (上面加了去重逻辑，所以这里安全了)
                    refreshHistory(personaId)
                } else {
                    Log.e("ChatRepo", "Cloud Error: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatRepo", "Cloud Exception", e)
            }
        }
    }

    // ... sendAudioMessage, getCurrentUserId 等保持不变 ...
    suspend fun sendAudioMessage(personaId: Long, audioFile: File, duration: Int) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        val tempUserMsg = ChatMessageEntity(
            userId = userId, personaId = personaId, role = "user",
            content = "[语音]", createdAt = timestamp, status = 0, msgType = 2, duration = duration, localFilePath = audioFile.absolutePath
        )
        val localId = chatDao.insertMessage(tempUserMsg)
        try {
            val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
            val userIdPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val personaIdPart = personaId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val durationPart = duration.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val response = api.sendAudio(filePart, userIdPart, personaIdPart, durationPart)
            if (response.isSuccess() && response.data != null) {
                chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 2))
                chatDao.insertMessage(response.data.toEntity())
                refreshHistory(personaId)
            } else { throw Exception("Server error") }
        } catch (e: Exception) {
            chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
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