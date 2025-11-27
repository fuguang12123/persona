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
    private val api: ChatService,
    private val userPrefs: UserPreferencesRepository
) {
    // ... getMessagesStream, syncConversationList, refreshHistory 保持不变 ...
    suspend fun getMessagesStream(personaId: Long): Flow<List<ChatMessageEntity>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return chatDao.getChatHistory(userId, personaId)
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

    suspend fun refreshHistory(personaId: Long) {
        val userId = getCurrentUserId() ?: return
        try {
            val response = api.getHistory(userId, personaId)
            if (response.isSuccess() && response.data != null) {
                val serverMessages = response.data.map { it.toEntity() }
                val localMessages = chatDao.getChatHistory(userId, personaId).first()
                mergeAndSyncMessages(localMessages, serverMessages)
            }
        } catch (e: Exception) { Log.e("ChatRepo", "Refresh failed: ${e.message}") }
    }

    private suspend fun mergeAndSyncMessages(localMsgs: List<ChatMessageEntity>, serverMsgs: List<ChatMessageEntity>) {
        val serverIds = serverMsgs.map { it.id }.toSet()
        val localOnlyMsgs = localMsgs.filter { it.id !in serverIds }
        val toInsert = mutableListOf<ChatMessageEntity>()
        for (serverMsg in serverMsgs) {
            val duplicateLocal = if (serverMsg.role == "user") {
                localOnlyMsgs.find { local -> local.role == serverMsg.role && local.content == serverMsg.content && local.status != 3 }
            } else null
            if (duplicateLocal != null) {
                chatDao.deleteMessage(duplicateLocal)
                toInsert.add(serverMsg)
            } else { toInsert.add(serverMsg) }
        }
        if (toInsert.isNotEmpty()) chatDao.insertMessages(toInsert)
    }

    // ✅ [Fix] 增加 isImageGen 参数
    suspend fun sendMessage(personaId: Long, content: String, isImageGen: Boolean = false) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        val tempUserMsg = ChatMessageEntity(
            id = 0, userId = userId, personaId = personaId, role = "user",
            content = content, createdAt = timestamp,
            status = 0, msgType = 0
        )
        val localId = chatDao.insertMessage(tempUserMsg)

        try {
            // ✅ 传递参数
            val request = SendMessageRequest(userId, personaId, content, isImageGen)
            val response = api.sendMessage(request)

            if (response.isSuccess() && response.data != null) {
                val aiResponse = response.data.toEntity()
                chatDao.insertMessage(aiResponse)
                refreshHistory(personaId)
            } else {
                throw Exception("Server error")
            }
        } catch (e: Exception) {
            chatDao.updateMessage(tempUserMsg.copy(id = localId, status = 3))
        }
    }

    // sendAudioMessage 保持不变
    suspend fun sendAudioMessage(personaId: Long, audioFile: File, duration: Int) {
        val userId = getCurrentUserId() ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        val tempUserMsg = ChatMessageEntity(
            id = 0, userId = userId, personaId = personaId, role = "user",
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
            } else { throw Exception("Server error: ${response.message}") }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Send audio failed", e)
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