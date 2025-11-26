package com.example.persona.data.model

// 统一的 API 响应包装类
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null
)

// 登录相关
data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthData(
    val token: String,
    val userId: String,
    val username: String,
    val avatarUrl: String?
)

// 注册相关
data class RegisterRequest(
    val username: String,
    val password: String,
    val confirmPassword: String,
    val captchaUuid: String,
    val captchaCode: String
)

// 验证码
data class CaptchaDto(
    val uuid: String,
    val image: String
)

// 用户资料
data class UserDto(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
    val backgroundImageUrl: String?
)

data class UpdateProfileRequest(
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val backgroundImageUrl: String? = null
)

// 修改密码
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)