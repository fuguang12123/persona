package com.example.persona.data.repository

import android.util.Log
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.entity.ChatMessageEntity
import com.example.persona.data.local.entity.PersonaEntity
import com.example.persona.data.remote.ChatMessageDto
import com.example.persona.data.remote.ChatService
import com.example.persona.data.remote.SendMessageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val personaDao: PersonaDao,
    private val api: ChatService,
    private val userPrefs: UserPreferencesRepository
) {

    suspend fun getMessagesStream(personaId: Long): Flow<List<ChatMessageEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        // 直接返回 DAO 的数据，DAO 已经按 DESC 排序
        return chatDao.getChatHistory(userId, personaId)
    }

    suspend fun syncConversationList() {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getConversations(userId)

            if (response.isSuccess() && response.data != null) {
                val list = response.data

                // 1. 保存 Persona 信息
                val personas = list.map { dto ->
                    PersonaEntity(
                        id = dto.personaId,
                        userId = 0L,
                        name = dto.name,
                        avatarUrl = dto.avatarUrl,
                        description = "",
                        personalityTags = "",
                        isPublic = true
                    )
                }
                personaDao.insertAll(personas)

                // 2. 刷新每个活跃会话的历史记录
                list.forEach { dto ->
                    refreshHistory(dto.personaId)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Sync failed: ${e.message}")
        }
    }

    suspend fun refreshHistory(personaId: Long) {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getHistory(userId, personaId)
            if (response.isSuccess() && response.data != null) {
                val entities = response.data.map { it.toEntity() }
                chatDao.insertMessages(entities)
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Refresh failed: ${e.message}")
        }
    }

    suspend fun sendMessage(personaId: Long, content: String) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        // 1. 插入本地临时消息 (用于 UI 立即显示)
        val tempUserMsg = ChatMessageEntity(
            id = 0, userId = userId, personaId = personaId, role = "user",
            content = content, createdAt = timestamp
        )
        // 获取插入后的 ID，用于后续删除
        val localId = chatDao.insertMessage(tempUserMsg)

        try {
            val request = SendMessageRequest(userId, personaId, content)
            val response = api.sendMessage(request)
            if (response.isSuccess() && response.data != null) {
                val serverEntity = response.data.toEntity()

                // [Fix] 修复消息被替换的问题
                // 必须检查返回消息的角色
                if (serverEntity.role == "user") {
                    // 情况 A: 服务器返回的是用户消息的确认 -> 执行替换 (更新 ID)
                    val msgToDelete = tempUserMsg.copy(id = localId)
                    chatDao.replaceLocalWithServerMessage(msgToDelete, serverEntity)
                } else {
                    // 情况 B: 服务器返回的是 AI 的回复 -> 直接插入回复
                    // 保留本地的用户消息 (tempUserMsg)，不要删除它，否则用户的问题会消失
                    chatDao.insertMessage(serverEntity)
                }
            } else {
                throw Exception("Server error")
            }
        } catch (e: Exception) {
            // 发送失败，更新本地消息状态 (例如添加错误标记)
            chatDao.updateMessage(tempUserMsg.copy(id = localId, content = "❌ $content"))
        }
    }

    private suspend fun getCurrentUserId(): Long? {
        val idStr = userPrefs.userId.first()
        return idStr?.toLongOrNull()
    }

    private fun ChatMessageDto.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = this.id, userId = this.userId, personaId = this.personaId,
            role = this.role, content = this.content, createdAt = this.createdAt ?: ""
        )
    }
}