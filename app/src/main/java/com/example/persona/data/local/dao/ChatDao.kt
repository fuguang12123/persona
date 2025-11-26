package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
    val timestamp: String?
)

@Dao
interface ChatDao {
    // --- 基础功能 ---

    // [Fix] 改为 DESC (最新的在上面)，配合 UI 的 reverseLayout = true 使用
    // 这样最新的消息永远在 List 的第 0 位 (屏幕底部)
    @Query("""
        SELECT * FROM chat_messages 
        WHERE user_id = :userId AND persona_id = :personaId 
        ORDER BY created_at DESC
    """)
    fun getChatHistory(userId: Long, personaId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE user_id = :userId AND persona_id = :personaId")
    suspend fun clearHistory(userId: Long, personaId: Long)

    // [New] 事务处理：原子性地替换消息，防止 UI 闪烁
    // 这在一个事务中完成，Flow 只会通知一次变更，用户不会看到消息消失再出现
    @Transaction
    suspend fun replaceLocalWithServerMessage(localMsg: ChatMessageEntity, serverMsg: ChatMessageEntity) {
        deleteMessage(localMsg)
        insertMessage(serverMsg)
    }

    // --- 会话列表 ---

    // [Fix] 优化查询逻辑 (保持之前的优化)
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
        AND m.id = (
            SELECT id 
            FROM chat_messages 
            WHERE persona_id = p.id AND user_id = :userId
            ORDER BY created_at DESC, id DESC
            LIMIT 1
        )
        ORDER BY m.created_at DESC
    """)
    fun getConversations(userId: Long): Flow<List<ConversationView>>
}