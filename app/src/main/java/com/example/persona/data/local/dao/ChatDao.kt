package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.persona.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用于 UI 展示会话列表的视图对象
 */
data class ConversationView(
    val personaId: Long,
    val name: String,
    val avatarUrl: String,
    val lastMessage: String,
    val timestamp: String? // [关键修改] 数据库存的是 "2025-..." 字符串，所以这里用 String 接收
)

@Dao
interface ChatDao {
    // --- 基础功能 ---

    @Query("""
        SELECT * FROM chat_messages 
        WHERE user_id = :userId AND persona_id = :personaId 
        ORDER BY created_at ASC
    """)
    fun getChatHistory(userId: Long, personaId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE user_id = :userId AND persona_id = :personaId")
    suspend fun clearHistory(userId: Long, personaId: Long)

    // --- 会话列表 ---

    @Query("""
        SELECT 
            p.id as personaId, 
            p.name as name, 
            p.avatar_url as avatarUrl, 
            m.content as lastMessage, 
            m.created_at as timestamp  
        FROM personas p 
        INNER JOIN chat_messages m ON p.id = m.persona_id 
        WHERE m.user_id = :userId 
        AND m.created_at = (
            SELECT MAX(created_at) 
            FROM chat_messages 
            WHERE persona_id = p.id AND user_id = :userId
        )
        ORDER BY m.created_at DESC
    """)
    fun getConversations(userId: Long): Flow<List<ConversationView>>
}