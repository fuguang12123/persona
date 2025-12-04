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

    suspend fun getMessagesStream(personaId: Long, isPrivateMode: Boolean, limit: Int): Flow<List<ChatMessageEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        val flow = if (isPrivateMode) {
            chatDao.getPrivateChatHistory(userId, personaId, limit)
        } else {
            chatDao.getCloudChatHistory(userId, personaId, limit)
        }
        return flow.map { list -> list.sortedByDescending { it.createdAt } }
    }

    suspend fun getMemoriesStream(personaId: Long): Flow<List<UserMemoryEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return userMemoryDao.getAllMemories(userId, personaId)
    }

    suspend fun refreshHistory(personaId: Long) {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getHistory(userId, personaId)
            if (response.isSuccess() && response.data != null) {
                val serverMessages = response.data.map { it.toEntity() }
                // 扩大本地查找范围，确保能清理掉所有陈旧的重复数据
                val localMessages = chatDao.getCloudChatHistory(userId, personaId, 2000).first()
                mergeAndSyncMessages(localMessages, serverMessages)
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Refresh failed: ${e.message}")
        }
    }

    /**
     * ✅ [Final Fix] 强力去重与同步
     * 策略：服务器数据是 Authority。凡是内容匹配的本地数据（无论时间是否对齐），一律视为缓存替身进行删除。
     */
    private suspend fun mergeAndSyncMessages(localMsgs: List<ChatMessageEntity>, serverMsgs: List<ChatMessageEntity>) {
        val toInsert = mutableListOf<ChatMessageEntity>()
        val toDelete = mutableListOf<ChatMessageEntity>()
        val matchedLocalIds = mutableSetOf<Long>()

        for (serverMsg in serverMsgs) {
            // 1. ID 精确匹配 (最理想情况)
            val exactMatch = localMsgs.find { it.id == serverMsg.id }

            if (exactMatch != null) {
                // 更新服务器最新数据，但保留本地的文件路径
                val mergedMsg = serverMsg.copy(
                    localFilePath = exactMatch.localFilePath ?: serverMsg.localFilePath,
                    status = 2
                )
                toInsert.add(mergedMsg)
                matchedLocalIds.add(exactMatch.id)
                continue
            }

            // 2. 模糊匹配 (清理本地缓存/影子消息)
            // ✅ 改动：使用 filter 找出 *所有* 匹配的本地垃圾数据
            val shadows = localMsgs.filter { local ->
                local.id !in matchedLocalIds &&
                        local.role == serverMsg.role &&
                        local.msgType == serverMsg.msgType &&
                        local.status != 3 && // 不删发送失败的消息
                        (
                                // A. 文本消息：忽略时间，只看内容！
                                // 这能解决时区不一致导致的重复问题 (例如本地是UTC+8，服务器是UTC)
                                (local.msgType == 0 && local.content == serverMsg.content && local.content.isNotEmpty()) ||

                                        // B. 图片/语音：必须依赖时间 (因为内容通常一样或为空)
                                        // 保持 120秒 容差
                                        (local.msgType != 0 && isTimeClose(local.createdAt, serverMsg.createdAt))
                                )
            }

            if (shadows.isNotEmpty()) {
                // 找到了一个或多个替身，全部删除！
                toDelete.addAll(shadows)
                shadows.forEach { matchedLocalIds.add(it.id) }

                // 尝试继承第一个替身的本地路径 (如果有)
                val existingPath = shadows.firstNotNullOfOrNull { it.localFilePath }

                // 插入服务器数据
                val mergedMsg = serverMsg.copy(
                    localFilePath = existingPath ?: serverMsg.localFilePath,
                    status = 2
                )
                toInsert.add(mergedMsg)
            } else {
                // 纯新消息
                toInsert.add(serverMsg)
            }
        }

        // 提交事务：删旧 + 插新
        if (toDelete.isNotEmpty() || toInsert.isNotEmpty()) {
            chatDao.syncMessages(toDelete, toInsert)
        }
    }

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

    suspend fun sendMessage(personaId: Long, content: String, isImageGen: Boolean = false, isPrivateMode: Boolean = false) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        val tempUserMsg = ChatMessageEntity(userId = userId, personaId = personaId, role = "user", content = content, createdAt = timestamp, status = 0, msgType = 0)

        val localId = if (isPrivateMode) {
            chatDao.insertMessage(tempUserMsg.copy(id = -System.currentTimeMillis()))
        } else {
            chatDao.insertMessage(tempUserMsg)
        }

        if (isPrivateMode) {
            val aiMsgId = -System.currentTimeMillis() - 1
            val tempAiMsg = ChatMessageEntity(id = aiMsgId, userId = userId, personaId = personaId, role = "assistant", content = "", createdAt = timestamp, status = 1)
            chatDao.insertMessage(tempAiMsg)
            val sb = StringBuilder()
            try {
                localLLMService.generateResponse(userId, personaId, content).collect { chunk ->
                    sb.append(chunk)
                    chatDao.updateMessage(tempAiMsg.copy(content = sb.toString(), status = 2))
                }
                CoroutineScope(Dispatchers.IO).launch { localLLMService.summarizeAndSaveMemory(userId, personaId, "User: $content\nAI: $sb") }
            } catch (e: Exception) {
                chatDao.updateMessage(tempAiMsg.copy(content = "Error: ${e.message}", status = 3))
            }
        } else {
            try {
                val request = SendMessageRequest(userId, personaId, content, isImageGen)
                val response = api.sendMessage(request)

                if (response.isSuccess() && response.data != null) {
                    val aiMsgDto = response.data
                    chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 2))

                    val aiMsgEntity = aiMsgDto.toEntity()
                    // 插入时做一次简单查重
                    val exists = chatDao.getCloudChatHistory(userId, personaId, 100).first().any { it.id == aiMsgEntity.id }
                    if (!exists) {
                        chatDao.insertMessage(aiMsgEntity)
                    }
                } else {
                    chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
                }
            } catch (e: Exception) {
                chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
            }
        }
    }

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
                val serverMsgEntity = response.data.toEntity().copy(
                    localFilePath = audioFile.absolutePath,
                    status = 2
                )
                chatDao.replaceLocalWithServerMessage(
                    localMsg = tempUserMsg.copy(id = localId),
                    serverMsg = serverMsgEntity
                )
                refreshHistory(personaId)
            } else {
                throw Exception("Server error")
            }
        } catch (e: Exception) {
            chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
        }
    }

    suspend fun syncConversationList() {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getConversations(userId)
            if (response.isSuccess() && response.data != null) {
                val list = response.data
                val personas = list.map { dto ->
                    PersonaEntity(dto.personaId, 0L, dto.name, dto.avatarUrl, "", "", true)
                }
                personaDao.insertAll(personas)
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