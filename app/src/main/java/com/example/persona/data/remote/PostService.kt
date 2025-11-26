package com.example.persona.data.remote

import com.example.persona.data.model.BaseResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface PostService {

    @GET("/personas/feed")
    suspend fun getPersonas(): BaseResponse<List<PersonaDto>>

    @GET("/feed/posts")
    suspend fun getFeedPosts(
        @Header("X-User-Id") userId: Long,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): BaseResponse<List<PostDto>>

    // 获取通知列表
    @GET("/posts/notifications")
    suspend fun getNotifications(
        @Header("X-User-Id") userId: Long
    ): BaseResponse<List<NotificationDto>>

    // [New] 获取未读通知数量
    @GET("/posts/notifications/unread-count")
    suspend fun getUnreadCount(
        @Header("X-User-Id") userId: Long
    ): BaseResponse<Long>

    // [New] 标记所有通知为已读
    @POST("/posts/notifications/read")
    suspend fun markNotificationsAsRead(
        @Header("X-User-Id") userId: Long
    ): BaseResponse<String>

    @GET("/posts/{id}")
    suspend fun getPostDetail(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long
    ): BaseResponse<PostDetailDto>

    @POST("/posts/{id}/like")
    suspend fun toggleLike(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long
    ): BaseResponse<String>

    @POST("/posts/{id}/bookmark")
    suspend fun toggleBookmark(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long
    ): BaseResponse<String>

    @POST("/posts/{id}/comments")
    suspend fun addComment(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long,
        @Body request: CommentRequest
    ): BaseResponse<CommentDto>

    @POST("/personas/ai/image")
    suspend fun generateAiImage(@Body request: GenerateImageRequest): BaseResponse<String>

    @Multipart
    @POST("/upload/image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): BaseResponse<String>

    @POST("/personas/{id}/posts")
    suspend fun createPost(
        @Header("X-User-Id") userId: Long,
        @Path("id") personaId: Long,
        @Body request: CreatePostRequest
    ): BaseResponse<PostDto>

    @POST("/personas/ai/magic-edit")
    suspend fun magicEdit(@Body request: MagicEditRequest): BaseResponse<String>
}