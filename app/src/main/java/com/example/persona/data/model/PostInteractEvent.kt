package com.example.persona.data.model

// 用于在不同页面间传递帖子状态变化的事件
data class PostInteractEvent(
    val postId: Long,
    val isLiked: Boolean? = null,      // null 表示该状态未变
    val likesCount: Int? = null,
    val isBookmarked: Boolean? = null
)