package com.example.persona.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["user_id", "persona_id"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "persona_id")
    val personaId: Long,

    val role: String, // "user" or "assistant"

    val content: String, // Text content or ASR result

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    // [New] 多模态字段
    @ColumnInfo(name = "msg_type")
    val msgType: Int = 0, // 0:Text, 1:Image, 2:Audio

    @ColumnInfo(name = "media_url")
    val mediaUrl: String? = null,

    val duration: Int = 0,

    // [New] 本地状态字段 (用于 SSOT 逻辑)
    // 0:SENDING, 1:GENERATING, 2:SUCCESS, 3:FAILED
    val status: Int = 2,

    @ColumnInfo(name = "local_file_path")
    val localFilePath: String? = null
) {
    val isUser: Boolean
        get() = role == "user"
}