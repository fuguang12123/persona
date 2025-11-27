package com.example.persona.data.model

// [Update] 这里的 ChatMessage 是给 UI 用的
data class ChatMessage(
    val id: Long = 0,
    val role: String,
    val content: String? = "",

    // [New] 多模态 UI 字段
    val msgType: Int = 0, // 0:Text, 1:Image, 2:Audio
    val mediaUrl: String? = null,
    val duration: Int = 0,

    // [New] 本地状态 (用于控制转圈、重试按钮)
    val status: Int = 2, // 2=SUCCESS
    val localFilePath: String? = null,

    // [New] 伪流式显示的内容 (打字机效果用)
    val displayContent: String? = content
)

data class SendMessageRequest(
    val userId: Long,
    val personaId: Long,
    val content: String
)