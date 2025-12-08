package com.example.persona.data.remote

import com.example.persona.data.model.BaseResponse
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import androidx.annotation.NonNull
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

/**
 * @class com.example.persona.data.remote.ChatService
 * @description 聊天相关的 Retrofit 契约接口：包含文本发送、语音发送、历史拉取与会话汇总。与 `ChatRepository` 协作，承担云端路径的消息交互；返回统一封装 `BaseResponse<T>`，便于上层处理错误码与空值。对应《最终作业.md》直接对话（B4）与端云协同（C4）。
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see com.example.persona.data.repository.ChatRepository
 * @关联功能 REQ-B4 直接对话；REQ-C4 端云协同
 */
interface ChatService {
    /**
     * 功能: 发送文本消息到云端，返回 AI 回复。
     * @param request SendMessageRequest - 用户ID、PersonaID、文本与生图标记 (@NonNull)
     * @return BaseResponse<ChatMessageDto>
     * 关联功能: REQ-B4 直接对话
     */
    @POST("/chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): BaseResponse<ChatMessageDto>

    /**
     * 功能: 发送语音消息，multipart 上传音频文件与元信息，返回解析后的消息。
     * @param file MultipartBody.Part - 音频文件
     * @param userId RequestBody - 用户ID
     * @param personaId RequestBody - PersonaID
     * @param duration RequestBody - 时长（秒）
     * @return BaseResponse<ChatMessageDto>
     * 关联功能: REQ-C2 多模态-语音
     */
    @Multipart
    @POST("/chat/sendAudio")
    suspend fun sendAudio(
        @Part file: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("personaId") personaId: RequestBody,
        @Part("duration") duration: RequestBody
    ): BaseResponse<ChatMessageDto>

    /**
     * 功能: 拉取云端历史消息；上层执行强力去重后入库。
     */
    @GET("/chat/history")
    suspend fun getHistory(
        @Query("userId") userId: Long,
        @Query("personaId") personaId: Long
    ): BaseResponse<List<ChatMessageDto>>

    /**
     * 功能: 获取会话汇总列表（最后一条消息与时间），用于首页展示。
     */
    @GET("/chat/conversations")
    suspend fun getConversations(@Query("userId") userId: Long): BaseResponse<List<ConversationDto>>
}
