package com.example.persona.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey
    val id: Long, // 这里不自增，因为 ID 是从服务器拉下来的，保持一致

    @ColumnInfo(name = "user_id")
    val userId: Long, // 归属用户

    val name: String,

    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String?,

    val description: String?,

    @ColumnInfo(name = "personality_tags")
    val personalityTags: String?, // 对应逗号分隔的字符串

    @ColumnInfo(name = "is_public")
    val isPublic: Boolean = true
)