package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.persona.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // 获取特定用户与特定分身的聊天记录
    @Query("""
        SELECT * FROM chat_messages 
        WHERE user_id = :userId AND persona_id = :personaId 
        ORDER BY id ASC
    """)
    fun getChatHistory(userId: Long, personaId: Long): Flow<List<ChatMessageEntity>>

    // ✅ 修改 1: 让插入操作返回新生成的 Row ID (Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    // 批量插入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    // ✅ 修改 2: 新增更新方法，用于标记发送失败
    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    // 清空某段对话
    @Query("DELETE FROM chat_messages WHERE user_id = :userId AND persona_id = :personaId")
    suspend fun clearHistory(userId: Long, personaId: Long)
}