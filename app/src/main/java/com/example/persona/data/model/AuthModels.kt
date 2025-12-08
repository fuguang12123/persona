package com.example.persona.data.model

/**
 * 统一的 API 响应包装类
 * @param T 响应数据的泛型类型
 * @param code 响应状态码,200表示成功
 * @param message 响应消息,通常用于错误提示
 * @param data 实际的响应数据
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null
)

/**
 * 登录请求数据类
 * @param username 用户名
 * @param password 密码
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 认证数据类,存储登录后返回的用户信息
 * @param token JWT令牌,用于后续请求的身份验证
 * @param userId 用户ID
 * @param username 用户名
 * @param avatarUrl 头像URL,可能为空
 */
data class AuthData(
    val token: String,
    val userId: String,
    val username: String,
    val avatarUrl: String?
)

/**
 * 注册请求数据类
 * @param username 用户名
 * @param password 密码
 * @param confirmPassword 确认密码
 * @param captchaUuid 验证码UUID,用于服务端验证
 * @param captchaCode 用户输入的验证码
 */
data class RegisterRequest(
    val username: String,
    val password: String,
    val confirmPassword: String,
    val captchaUuid: String,
    val captchaCode: String
)

/**
 * 验证码数据传输对象
 * @param uuid 验证码唯一标识符
 * @param image Base64编码的验证码图片
 */
data class CaptchaDto(
    val uuid: String,
    val image: String
)

/**
 * 用户信息数据传输对象
 * @param id 用户ID
 * @param username 用户名
 * @param nickname 昵称,可选
 * @param avatarUrl 头像URL,可选
 * @param backgroundImageUrl 背景图片URL,可选
 */
data class UserDto(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatarUrl: String?,
    val backgroundImageUrl: String?
)

/**
 * 更新用户资料请求类
 * 所有字段都是可选的,只更新提供的字段
 * @param nickname 新昵称
 * @param avatarUrl 新头像URL
 * @param backgroundImageUrl 新背景图片URL
 */
data class UpdateProfileRequest(
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val backgroundImageUrl: String? = null
)

/**
 * 修改密码请求类
 * @param oldPassword 旧密码
 * @param newPassword 新密码
 */
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)
