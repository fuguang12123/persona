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

/**
 * @class com.example.persona.data.remote.PostService
 * @description 面向动态流与互动的 Retrofit 契约接口，覆盖信息流、通知、点赞/收藏、评论、上传与 AI 生图等业务。其返回统一封装 `BaseResponse<T>`，便于上层以一致方式处理错误码与数据空值。与《最终作业.md》基础与进阶需求直接映射：社交广场（B2/B3）、多模态交互（C2）、富文本与流式（C1，配合 UI 实现）、从 Mock 到真实服务（C3）等。Repository 负责处理乐观更新与事件广播，确保用户交互流畅且可回滚。
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see com.example.persona.data.repository.PostRepository
 * @关联功能 REQ-B2/B3 社交广场；REQ-C2 多模态交互；REQ-C3 架构演进
 */
interface PostService {

    // 获取智能体广场 (对应 PersonaController)
    @GET("personas/feed")
    suspend fun getPersonas(): BaseResponse<List<PersonaDto>>

    // [Mod] 获取动态流 (增加 type 参数支持关注流)
    // 注意：路径修正为 "posts/feed" 以匹配后端 PostController 的 @RequestMapping("/posts") + @GetMapping("/feed")
    /**
     * 功能: 拉取动态信息流，支持类型切换（全部/关注），并分页加载。
     * 实现逻辑: GET `posts/feed`，携带 `X-User-Id` 与 `type/page/size`，上层落库并驱动 UI。
     * @param userId Long - 当前用户ID (@NonNull)
     * @param type String - 流类型，"all" 或 "followed" (@NonNull)
     * @param page Int - 页码 (@NonNull)
     * @param size Int - 每页大小，默认20
     * @return BaseResponse<List<PostDto>> - 列表数据
     * 关联功能: REQ-B3 社交广场-浏览与互动
     */
    @GET("posts/feed")
    suspend fun getFeedPosts(
        @Header("X-User-Id") userId: Long,
        @Query("type") type: String = "all", // "all" or "followed"
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): BaseResponse<List<PostDto>>

    // 获取通知列表
    @GET("posts/notifications")
    suspend fun getNotifications(
        @Header("X-User-Id") userId: Long
    ): BaseResponse<List<NotificationDto>>

    // [New] 获取未读通知数量
    @GET("posts/notifications/unread-count")
    suspend fun getUnreadCount(
        @Header("X-User-Id") userId: Long
    ): BaseResponse<Long>

    // [New] 标记所有通知为已读
    @POST("posts/notifications/read")
    suspend fun markNotificationsAsRead(
        @Header("X-User-Id") userId: Long
    ): BaseResponse<String>

    @GET("posts/{id}")
    suspend fun getPostDetail(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long
    ): BaseResponse<PostDetailDto>

    @POST("posts/{id}/like")
    suspend fun toggleLike(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long,
        @Query("isLiked") isLiked: Boolean // [Fix] 增加 isLiked 参数，告诉后端是点赞还是取消
    ): BaseResponse<String>

    @POST("posts/{id}/bookmark")
    suspend fun toggleBookmark(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long,
        @Query("isBookmarked") isBookmarked: Boolean
    ): BaseResponse<String>

    @POST("posts/{id}/comments")
    suspend fun addComment(
        @Header("X-User-Id") userId: Long,
        @Path("id") id: Long,
        @Body request: CommentRequest
    ): BaseResponse<CommentDto>

    @POST("personas/ai/image")
    suspend fun generateAiImage(@Body request: GenerateImageRequest): BaseResponse<String>

    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): BaseResponse<String>

    @POST("personas/{id}/posts")
    suspend fun createPost(
        @Header("X-User-Id") userId: Long,
        @Path("id") personaId: Long,
        @Body request: CreatePostRequest
    ): BaseResponse<PostDto>
//修改动态信息
    @POST("personas/ai/magic-edit")
    suspend fun magicEdit(@Body request: MagicEditRequest): BaseResponse<String>
}
