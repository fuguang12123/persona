package com.example.persona.data.remote

import com.example.persona.data.model.BaseResponse
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

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
    val createdAt: String?,
    val msgType: Int? = 0,
    val mediaUrl: String? = null,
    val duration: Int? = 0
)

data class SendMessageRequest(
    val userId: Long,
    val personaId: Long,
    val content: String,
    // ✅ [New] 增加生图标记，默认为 false，不影响旧代码
    val isImageGen: Boolean = false
)

interface ChatService {
    @POST("/chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): BaseResponse<ChatMessageDto>

    @Multipart
    @POST("/chat/sendAudio")
    suspend fun sendAudio(
        @Part file: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("personaId") personaId: RequestBody,
        @Part("duration") duration: RequestBody
    ): BaseResponse<ChatMessageDto>

    @GET("/chat/history")
    suspend fun getHistory(
        @Query("userId") userId: Long,
        @Query("personaId") personaId: Long
    ): BaseResponse<List<ChatMessageDto>>

    @GET("/chat/conversations")
    suspend fun getConversations(@Query("userId") userId: Long): BaseResponse<List<ConversationDto>>
}