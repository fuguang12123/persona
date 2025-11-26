package com.example.persona.data.model
import com.google.gson.annotations.SerializedName

/**
 * 领域模型 (Domain Model)
 * 用于 Retrofit 网络响应解析和 UI 层展示
 */
data class Persona(
    val id: Long = 0,

    // [Fix] 添加 alternate 兼容后端返回的 "userId" (驼峰)
    @SerializedName("user_id", alternate = ["userId"])
    val userId: Long? = null,

    val name: String = "",

    // [Fix] 添加 alternate 兼容 "avatarUrl"
    @SerializedName("avatar_url", alternate = ["avatarUrl"])
    val avatarUrl: String? = null,

    val description: String? = null,

    // [Fix] 添加 alternate 兼容 "personalityTags"
    @SerializedName("personality_tags", alternate = ["personalityTags"])
    val personalityTags: String? = null,

    // [Fix] 添加 alternate 兼容 "promptTemplate"
    @SerializedName("prompt_template", alternate = ["promptTemplate"])
    val promptTemplate: String? = null,

    // [Fix] 添加 alternate 兼容 "isPublic"
    @SerializedName("is_public", alternate = ["isPublic"])
    val isPublic: Boolean? = true
)