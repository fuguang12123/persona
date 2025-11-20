package com.example.persona.data.model

data class ChatMessage(
    val id: Long = 0,
    val role: String,
    // ✅ 修改点：改为 String? (可空)
    val content: String? = ""
)

// SendMessageRequest 不需要改，因为发送时肯定有内容
data class SendMessageRequest(
    val userId: Long,
    val personaId: Long,
    val content: String
)