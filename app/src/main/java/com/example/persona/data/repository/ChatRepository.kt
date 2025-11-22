package com.example.persona.data.repository

import android.util.Log
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.entity.ChatMessageEntity
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
    private val api: ChatService,
    private val userPrefs: UserPreferencesRepository
) {

    suspend fun getMessagesStream(personaId: Long): Flow<List<ChatMessageEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return chatDao.getChatHistory(userId, personaId)
    }

    suspend fun refreshHistory(personaId: Long) {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getHistory(userId, personaId)
            if (response.isSuccess() && response.data != null) {
                val entities = response.data.map { it.toEntity() }
                chatDao.insertMessages(entities)
            } else {
                Log.e("ChatRepo", "Refresh error: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Refresh failed: ${e.message}")
        }
    }

    suspend fun sendMessage(personaId: Long, content: String) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        val tempUserMsg = ChatMessageEntity(
            id = 0,
            userId = userId,
            personaId = personaId,
            role = "user",
            content = content,
            createdAt = timestamp
        )
        val localId = chatDao.insertMessage(tempUserMsg)

        try {
            val request = SendMessageRequest(userId, personaId, content)

            // 接收响应并拆包
            val response = api.sendMessage(request)

            if (response.isSuccess() && response.data != null) {
                // 成功拿到数据，存入数据库
                chatDao.insertMessage(response.data.toEntity())
            } else {
                // 业务逻辑失败 (比如后端报错)
                throw Exception("Server error: ${response.message}")
            }

        } catch (e: Exception) {
            Log.e("ChatRepo", "Send failed: ${e.message}")
            // 失败时更新本地消息状态
            val failedMsg = tempUserMsg.copy(
                id = localId,
                content = "❌ [发送失败] $content"
            )
            chatDao.updateMessage(failedMsg)
        }
    }

    private suspend fun getCurrentUserId(): Long? {
        val idStr = userPrefs.userId.first()
        return idStr?.toLongOrNull()
    }

    private fun ChatMessageDto.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = this.id,
            userId = this.userId,
            personaId = this.personaId,
            role = this.role,
            content = this.content,
            createdAt = this.createdAt ?: ""
        )
    }
}