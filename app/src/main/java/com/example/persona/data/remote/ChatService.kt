package com.example.persona.data.remote

import com.example.persona.data.model.BaseResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatService {

    // ✅ 修改 1: 返回 BaseResponse<ChatMessageDto>
    @POST("/chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): BaseResponse<ChatMessageDto>

    // ✅ 修改 2: 历史记录也是包在 BaseResponse 里的
    @GET("/chat/history")
    suspend fun getHistory(
        @Query("userId") userId: Long,
        @Query("personaId") personaId: Long
    ): BaseResponse<List<ChatMessageDto>>
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
    val createdAt: String?
)