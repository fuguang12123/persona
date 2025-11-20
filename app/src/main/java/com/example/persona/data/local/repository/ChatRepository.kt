package com.example.persona.data.repository

import android.util.Log
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.entity.ChatMessageEntity
import com.example.persona.data.remote.ChatMessageDto
import com.example.persona.data.remote.ChatService
import com.example.persona.data.remote.SendMessageRequest
import kotlinx.coroutines.flow.Flow
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

    // 1. SSOT 核心: UI 永远只观察本地数据库
    suspend fun getMessagesStream(personaId: Long): Flow<List<ChatMessageEntity>> {
        val userId = getCurrentUserId() ?: return kotlinx.coroutines.flow.emptyFlow()
        return chatDao.getChatHistory(userId, personaId)
    }

    // 2. 刷新逻辑
    suspend fun refreshHistory(personaId: Long) {
        val userId = getCurrentUserId() ?: return
        try {
            val remoteHistory = api.getHistory(userId, personaId)
            val entities = remoteHistory.map { it.toEntity() }
            chatDao.insertMessages(entities)
        } catch (e: Exception) {
            Log.e("ChatRepo", "Refresh failed: ${e.message}")
        }
    }

    // 3. 发送逻辑: 乐观更新 + 失败回滚
    suspend fun sendMessage(personaId: Long, content: String) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        // Step 1: 乐观更新
        val tempUserMsg = ChatMessageEntity(
            id = 0, // 临时 ID，让 Room 自增
            userId = userId,
            personaId = personaId,
            role = "user",
            content = content,
            createdAt = timestamp
        )
        // ✅ 关键点: 拿到 Room 刚刚生成的真实 ID
        val localId = chatDao.insertMessage(tempUserMsg)

        try {
            // Step 2: 调用真实 API
            val request = SendMessageRequest(userId, personaId, content)
            val aiResponseDto = api.sendMessage(request)

            // Step 3: 把 AI 的回复存入本地
            chatDao.insertMessage(aiResponseDto.toEntity())

        } catch (e: Exception) {
            Log.e("ChatRepo", "Send failed: ${e.message}")

            // ✅ 实现 TODO: 更新状态为失败
            // 我们利用 localId 找到刚才那条消息，修改它的内容前缀
            // (注: 进阶做法是在 Entity 加 status 字段，这里用最快的方式修改内容)
            val failedMsg = tempUserMsg.copy(
                id = localId, // 必须指明 ID，否则会变成插入新行
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