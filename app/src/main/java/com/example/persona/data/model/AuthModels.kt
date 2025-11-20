package com.example.persona.data.model
import com.google.gson.annotations.SerializedName

// 1. 统一响应外壳 (匹配后端 Result<T>)
data class ApiResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?
)

// 2. 登录请求体
data class LoginRequest(
    val username: String,
    // 后端目前可能是明文，后续建议加密
    val password: String
)

// 3. 登录成功返回的数据
data class AuthData(
    @SerializedName("token") val token: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("username") val username: String
)