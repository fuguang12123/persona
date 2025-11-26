package com.example.persona.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 详情页的顶层数据结构
 * 对应后端返回的 PostDetailVo
 */
data class PostDetailDto(
    val post: PostDto,          // 动态本体 (复用已有的 PostDto)
    val authorName: String?,    // 发布者名字 (Persona)
    val authorAvatar: String?,  // 发布者头像 (Persona)
    val isLiked: Boolean,       // 当前用户是否点赞
    val isBookmarked: Boolean,  // 当前用户是否收藏
    val comments: List<CommentDto> // 评论列表
)

/**
 * 单条评论的数据结构
 */
data class CommentDto(
    val id: Long,

    // 支持下划线(DB查询) 和 驼峰(Entity返回)
    @SerializedName("user_id", alternate = ["userId"])
    val userId: Long,

    @SerializedName("user_name", alternate = ["userName"])
    val userName: String?,

    @SerializedName("user_avatar", alternate = ["userAvatar"])
    val userAvatar: String?,

    val content: String,

    // 关键修复：添加 alternate，同时匹配 root_parent_id 和 rootParentId
    @SerializedName("parent_id", alternate = ["parentId"])
    val parentId: Long?,

    @SerializedName("root_parent_id", alternate = ["rootParentId"])
    val rootParentId: Long?,

    @SerializedName("reply_to_user_id", alternate = ["replyToUserId"])
    val replyToUserId: Long?,

    @SerializedName("created_at", alternate = ["createdAt"])
    val createdAt: String?
)

/**
 * 发送评论时的请求体
 */
data class CommentRequest(
    val content: String,
    val parentId: Long? = null,
    val replyToUserId: Long? = null
)