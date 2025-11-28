package com.example.persona.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 共生记忆实体：存储端侧 AI 对用户的观察和记忆
 * [Update] 新增 user_id 字段，确保记忆跟随账号隔离
 */
@Entity(
    tableName = "user_memories",
    // 联合索引：加速查询特定用户与特定 Persona 的记忆
    indices = [Index(value = ["user_id", "persona_id"])]
)
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: Long, // [New] 绑定当前登录用户

    @ColumnInfo(name = "persona_id")
    val personaId: Long, // 记忆属于哪个 Persona 与用户的交互

    val content: String, // 记忆内容

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)