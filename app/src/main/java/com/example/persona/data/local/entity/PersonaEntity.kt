package com.example.persona.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 数据库实体 (Room Entity)
 * 用于实现"广场列表"的离线缓存
 */
@Entity(tableName = "personas")
data class PersonaEntity(
    // 这里不使用 autoGenerate，因为我们要严格对应服务器的 ID
    @PrimaryKey
    val id: Long,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    val name: String,

    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String?,

    val description: String?,

    @ColumnInfo(name = "personality_tags")
    val personalityTags: String?,

    @ColumnInfo(name = "is_public")
    val isPublic: Boolean
)