package com.example.persona.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 对应后端的 ChatController / ChatService
 */
interface ChatService {

    @POST("/chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): ChatMessageDto

    @GET("/chat/history")
    suspend fun getHistory(
        @Query("userId") userId: Long,
        @Query("personaId") personaId: Long
    ): List<ChatMessageDto>
}

data class SendMessageRequest(
    val userId: Long,
    val personaId: Long,
    val content: String
)

data class ChatMessageDto(
    val id: Long,
    val userId: Long,
    val personaId: Long,
    val role: String,
    val content: String,
    val createdAt: String? // 后端 LocalDateTime 转 JSON 通常是 String
)