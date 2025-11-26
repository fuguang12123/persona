package com.example.persona.data.remote

import com.example.persona.data.model.BaseResponse
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// [New] 会话列表 DTO
data class ConversationDto(
    val personaId: Long,
    val name: String,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    val lastMessage: String?,
    val timestamp: String?
)

data class ChatMessageDto(
    val id: Long,
    val userId: Long,
    val personaId: Long,
    val role: String,
    val content: String,
    val createdAt: String?
)

data class SendMessageRequest(
    val userId: Long,
    val personaId: Long,
    val content: String
)

interface ChatService {
    @POST("/chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): BaseResponse<ChatMessageDto>

    @GET("/chat/history")
    suspend fun getHistory(
        @Query("userId") userId: Long,
        @Query("personaId") personaId: Long
    ): BaseResponse<List<ChatMessageDto>>

    // [New] 对应的后端接口
    @GET("/chat/conversations")
    suspend fun getConversations(@Query("userId") userId: Long): BaseResponse<List<ConversationDto>>
}