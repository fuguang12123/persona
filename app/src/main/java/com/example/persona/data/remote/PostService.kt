package com.example.persona.data.remote

import com.example.persona.data.local.entity.PostEntity
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

    @GET("/feed/posts")
    suspend fun getFeedPosts(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): BaseResponse<List<PostDto>>

    @POST("/personas/ai/image")
    suspend fun generateAiImage(@Body request: GenerateImageRequest): BaseResponse<String>

    // [New] 图片上传接口
    @Multipart
    @POST("/upload/image")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part
    ): BaseResponse<String>

    @POST("/personas/{id}/posts")
    suspend fun createPost(
        @Header("X-User-Id") userId: Long,
        @Path("id") personaId: Long,
        @Body request: CreatePostRequest
    ): BaseResponse<PostDto> // 后端现在返回 Map，Gson 可以兼容解析为 PostDto，只要字段匹配
}

// ... DTOs 保持不变 ...
data class GenerateImageRequest(val prompt: String)
data class CreatePostRequest(val content: String, val imageUrls: List<String>)
data class PostDto(
    val id: Long,
    val userId: Long?,
    val personaId: String,
    val content: String,
    val imageUrls: List<String>?,
    val likes: Int,
    val createdAt: Long,
    val authorName: String?,
    val authorAvatar: String?,
    val isLiked: Boolean
) {
    fun toEntity(ownerUserId: String): PostEntity {
        return PostEntity(
            id = id,
            authorUserId = userId ?: 0L,
            personaId = personaId,
            content = content,
            imageUrls = imageUrls ?: emptyList(),
            likeCount = likes,
            isLiked = isLiked,
            createdAt = createdAt,
            authorName = authorName ?: "未知用户",
            authorAvatar = authorAvatar ?: "https://api.dicebear.com/7.x/initials/png?seed=unknown",
            ownerUserId = ownerUserId
        )
    }
}