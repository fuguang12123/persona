package com.example.persona.data.model
import com.google.gson.annotations.SerializedName

/**
 * 领域模型 (Domain Model)
 * 用于 Retrofit 网络响应解析和 UI 层展示
 */
data class Persona(
    val id: Long = 0,

    // 后端返回的是 user_id，转为驼峰
    @SerializedName("user_id")
    val userId: Long? = null,

    val name: String = "",

    // 后端返回的是 avatar_url
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,

    val description: String? = null,

    // 后端返回的是 personality_tags
    @SerializedName("personality_tags")
    val personalityTags: String? = null,

    // 系统提示词 (创建时用到，广场列表可能不返回)
    @SerializedName("prompt_template")
    val promptTemplate: String? = null,

    // 后端返回的是 is_public (0/1 或 boolean)
    @SerializedName("is_public")
    val isPublic: Boolean? = true
)

