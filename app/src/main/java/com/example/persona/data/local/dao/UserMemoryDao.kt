package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.data.local.entity.UserMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity)

    // [Update] RAG 核心：增加 user_id 过滤，只查当前用户的记忆
    @Query("SELECT * FROM user_memories WHERE user_id = :userId AND persona_id = :personaId ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentMemories(userId: Long, personaId: Long, limit: Int = 10): List<UserMemoryEntity>

    // [New] UI 展示用：获取该用户的所有记忆 (Flow 实时更新)
    @Query("SELECT * FROM user_memories WHERE user_id = :userId AND persona_id = :personaId ORDER BY created_at DESC")
    fun getAllMemories(userId: Long, personaId: Long): Flow<List<UserMemoryEntity>>
}