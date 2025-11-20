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
    val id: Long = 0, // 对应 BIGINT AUTO_INCREMENT

    @ColumnInfo(name = "user_id")
    val userId: Long, // 对应 user_id

    @ColumnInfo(name = "persona_id")
    val personaId: Long, // 对应 persona_id

    val role: String, // 对应 "user" 或 "assistant"

    val content: String, // 对应 TEXT

    @ColumnInfo(name = "created_at")
    val createdAt: String // 对应 DATETIME，建议存 ISO-8601 字符串
) {
    // 辅助属性：用于 UI 判断显示在左侧还是右侧
    val isUser: Boolean
        get() = role == "user"
}