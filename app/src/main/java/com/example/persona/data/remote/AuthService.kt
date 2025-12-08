package com.example.persona.data.remote

import com.example.persona.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

/**
 * @class com.example.persona.data.remote.AuthService
 * @description 认证与用户资料相关的 Retrofit 契约接口：登录、注册、Token 续期、验证码、个人资料查询/更新、密码修改与图片上传，另含我的列表数据。配合 `AuthInterceptor` 的 401 策略与 DataStore 的会话信息，实现从 Mock 到真实服务的认证闭环。
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see com.example.persona.data.remote.AuthInterceptor
 * @关联功能 REQ-C3 架构演进-认证
 */
interface AuthService {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<Map<String, Any>>>

    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<Map<String, Any>>>

    // ✅ [New] 自动续期接口
    @POST("/auth/refresh")
    suspend fun refreshToken(): Response<ApiResponse<Map<String, Any>>>

    @GET("/auth/captcha")
    suspend fun getCaptcha(): Response<ApiResponse<CaptchaDto>>

    @GET("/users/me")
    suspend fun getMyProfile(): Response<ApiResponse<UserDto>>

    @PUT("/users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiResponse<UserDto>>

    @POST("/users/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<String>>

    // ✅ [New] 上传图片 (复用后端 /upload/image 接口)
    @Multipart
    @POST("/upload/image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): Response<ApiResponse<String>>

    // Lists
    @GET("/users/me/personas")
    suspend fun getMyPersonas(): Response<ApiResponse<List<com.example.persona.data.model.Persona>>>

    @GET("/users/me/posts")
    suspend fun getMyPosts(): Response<ApiResponse<List<PostDto>>>

    @GET("/users/me/likes")
    suspend fun getMyLikes(): Response<ApiResponse<List<PostDto>>>

    @GET("/users/me/bookmarks")
    suspend fun getMyBookmarks(): Response<ApiResponse<List<PostDto>>>
}
