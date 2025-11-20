package com.example.persona.data.model

import com.google.gson.annotations.SerializedName

data class Persona(
    val id: Long,
    val name: String?,
    val description: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("personality_tags") val tags: String?

)

