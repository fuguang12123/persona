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

/**
 * ChatDao: 负责聊天消息的数据库访问操作
 */
@Dao
interface ChatDao {
    // --- 基础消息查询功能 ---

    /**
     * 获取指定用户的聊天历史记录（包含所有类型消息）
     * [Update] 增加 :limit 参数，用于支持分页加载，只加载最近的 N 条消息
     * 使用 ORDER BY created_at DESC 确保返回的是最新的消息在列表前面（配合 UI 的 reverseLayout）
     */
    @Query("""
        SELECT * FROM chat_messages 
        WHERE user_id = :userId AND persona_id = :personaId 
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getChatHistory(userId: Long, personaId: Long, limit: Int): Flow<List<ChatMessageEntity>>

    /**
     * [Update] 获取云端模式的消息 (ID >= 0)
     * 排除私密模式产生的本地消息（ID < 0），仅返回同步自云端或在云端模式发送的消息
     * 支持分页参数 limit
     */
    @Query("""
        SELECT * FROM chat_messages 
        WHERE user_id = :userId AND persona_id = :personaId AND id >= 0
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getCloudChatHistory(userId: Long, personaId: Long, limit: Int): Flow<List<ChatMessageEntity>>

    /**
     * [Update] 获取私密模式的消息 (ID < 0)
     * 仅返回在端侧私密模式下产生的本地消息，不包含云端数据
     * 支持分页参数 limit
     */
    @Query("""
        SELECT * FROM chat_messages 
        WHERE user_id = :userId AND persona_id = :personaId AND id < 0
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getPrivateChatHistory(userId: Long, personaId: Long, limit: Int): Flow<List<ChatMessageEntity>>

    // --- 消息增删改查 ---

    /**
     * 插入单条消息
     * 如果 ID 冲突则替换（REPLACE 策略）
     * 返回插入后的行 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    /**
     * 批量插入消息
     * 用于同步历史记录等批量操作
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /**
     * 更新消息内容或状态
     */
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    /**
     * 删除单条消息
     */
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    /**
     * 清空指定会话的所有历史记录
     */
    @Query("DELETE FROM chat_messages WHERE user_id = :userId AND persona_id = :personaId")
    suspend fun clearHistory(userId: Long, personaId: Long)

    // --- 复杂事务操作 ---

    /**
     * 原子操作：用服务器返回的正式消息替换本地的临时消息
     * 场景：消息发送成功后，将本地生成的临时消息（ID可能不准）删除，插入服务器返回的带正确 ID 的消息
     * 使用 @Transaction 确保原子性，防止数据不一致
     */
    @Transaction
    suspend fun replaceLocalWithServerMessage(localMsg: ChatMessageEntity, serverMsg: ChatMessageEntity) {
        deleteMessage(localMsg)
        insertMessage(serverMsg)
    }

    /**
     * 原子操作：同步消息列表（批量删除 + 批量插入）
     * 场景：从服务器拉取最新历史记录后，执行去重逻辑，需要删除本地重复/过期的消息并插入新消息
     * 使用 @Transaction 确保操作的原子性，避免 UI 闪烁或数据不一致
     */
    @Transaction
    suspend fun syncMessages(toDelete: List<ChatMessageEntity>, toInsert: List<ChatMessageEntity>) {
        toDelete.forEach { deleteMessage(it) }
        insertMessages(toInsert)
    }

    // --- 会话列表查询 ---

    /**
     * 获取会话列表视图
     * 连接 Personas 表和 ChatMessages 表，查询每个角色的最新一条消息
     * 用于在主页展示最近的聊天列表
     */
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