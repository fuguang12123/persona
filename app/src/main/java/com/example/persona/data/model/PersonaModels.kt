package com.example.persona.data.model
import com.google.gson.annotations.SerializedName

/**
 * 接收后端推荐接口的 DTO
 * 对应 JSON 结构:
 * {
 * "id": 123,
 * "name": "...",
 * "avatarUrl": "...",
 * "tags": ["...", "..."],
 * "reason": "...",
 * "matchScore": 92
 * }
 */
data class PersonaRecommendationDto(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val tags: List<String>?,   // JSON 中的 tags 数组
    val reason: String?,       // 推荐理由
    val matchScore: Int?       // 匹配分数
)

/**
 * 领域模型 (Domain Model)
 * 用于 Retrofit 网络响应解析和 UI 层展示
 */
data class Persona(
    val id: Long = 0,

    @SerializedName("user_id", alternate = ["userId"])
    val userId: Long? = null,

    val name: String = "",

    @SerializedName("avatar_url", alternate = ["avatarUrl"])
    val avatarUrl: String? = null,

    val description: String? = null,

    @SerializedName("personality_tags", alternate = ["personalityTags"])
    val personalityTags: String? = null,

    @SerializedName("prompt_template", alternate = ["promptTemplate"])
    val promptTemplate: String? = null,

    @SerializedName("is_public", alternate = ["isPublic"])
    val isPublic: Boolean? = true,

    // --- 新增推荐相关字段 (Nullable, 仅推荐列表有值) ---
    val matchScore: Int? = null,
    val reason: String? = null,

    // 为了 UI 方便，我们在 Mapping 时把 List<String> 存到这里
    // 因为 Room 实体存的是 String，而 DTO 给的是 List，这里做个中间层
    val tagsList: List<String> = emptyList()
)