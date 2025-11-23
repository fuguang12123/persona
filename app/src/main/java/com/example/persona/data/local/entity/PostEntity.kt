package com.example.persona.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.persona.data.local.converter.PostTypeConverters

@Entity(tableName = "posts")
@TypeConverters(PostTypeConverters::class)
data class PostEntity(
    @PrimaryKey
    val id: Long,

    // [New] 动态的真实发布者 ID (区别于 ownerUserId)
    // 对应后端的 user_id
    val authorUserId: Long,

    val personaId: String,
    val content: String,
    val imageUrls: List<String>,

    val likeCount: Int,
    val isLiked: Boolean,

    // 本地交互状态
    val isBookmarked: Boolean = false,

    val createdAt: Long,

    // 冗余快照 (用于 UI 快速展示)
    val authorName: String,
    val authorAvatar: String,

    // [Cache Key] 这条数据属于哪个当前登录用户可见
    // (用于实现多账号切换时的数据隔离)
    val ownerUserId: String
)