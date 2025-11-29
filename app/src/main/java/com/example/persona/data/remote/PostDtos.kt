package com.example.persona.data.remote

import com.example.persona.data.local.entity.PostEntity
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale

// === 请求体 ===
data class GenerateImageRequest(val prompt: String)
data class CreatePostRequest(val content: String, val imageUrls: List<String>)

// [Mod] 升级版润色请求体
data class MagicEditRequest(
    val content: String,
    val personaName: String?,
    val description: String? = null,
    val tags: String? = null
)

// === 智能体简单信息 DTO ===
data class PersonaDto(
    val id: Long,
    val name: String,
    val description: String?,
    @SerializedName("avatar_url") val avatar: String?,
    @SerializedName("personality_tags", alternate = ["personalityTags"])
    val personalityTags: String? = null
)

// === [New] 通知 DTO ===
data class NotificationDto(
    val id: Long,
    val type: Int, // 1=Like, 2=Comment
    val postId: Long,
    val isRead: Boolean,
    val createdAt: Long,
    val senderName: String?,
    val senderAvatar: String?
)

// === 响应体 (超级容错版) ===
data class PostDto(
    val id: Long,

    // [New] 新增 userId 字段，用于跳转到用户或区分是否是自己
    val userId: Long?,

    val personaId: String,
    val content: String,

    @SerializedName("imageUrls") private val _imageUrls: Any?,

    val likes: Int,

    @SerializedName("createdAt") private val _createdAt: String?,

    val authorName: String?,
    val authorAvatar: String?,
    val isLiked: Boolean,

    // [New] 新增 isBookmarked 字段，用于列表页显示星星
    val isBookmarked: Boolean = false
) {
    val imageUrls: List<String>
        get() {
            return when (_imageUrls) {
                is List<*> -> _imageUrls.filterIsInstance<String>()
                is String -> {
                    try {
                        val clean = _imageUrls.trim().removePrefix("[").removeSuffix("]")
                        if (clean.isBlank()) emptyList()
                        else clean.split(",").map {
                            it.trim().removePrefix("\"").removeSuffix("\"").replace("\\/", "/")
                        }
                    } catch (e: Exception) { emptyList() }
                }
                else -> emptyList()
            }
        }

    val createdAt: Long
        get() {
            if (_createdAt == null) return 0L
            // 如果是纯数字字符串，直接转 Long
            _createdAt.toLongOrNull()?.let { return it }
            // 否则尝试解析 ISO 时间格式
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                return isoFormat.parse(_createdAt)?.time ?: 0L
            } catch (e: Exception) {
                return 0L
            }
        }

    // 转为本地实体 (用于缓存)
    fun toEntity(ownerUserId: String): PostEntity {
        return PostEntity(
            id = id,
            authorUserId = userId ?: 0L,
            personaId = personaId,
            content = content,
            imageUrls = imageUrls,
            likeCount = likes,
            isLiked = isLiked,
            createdAt = createdAt,
            authorName = authorName ?: "未知用户",
            authorAvatar = authorAvatar ?: "https://api.dicebear.com/7.x/avataaars/png?seed=unknown",
            ownerUserId = ownerUserId,
            isBookmarked = isBookmarked
        )
    }
}