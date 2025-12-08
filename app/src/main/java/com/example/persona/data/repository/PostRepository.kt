package com.example.persona.data.repository

import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.PostInteractEvent
import com.example.persona.data.remote.CommentDto
import com.example.persona.data.remote.CommentRequest
import com.example.persona.data.remote.CreatePostRequest
import com.example.persona.data.remote.GenerateImageRequest
import com.example.persona.data.remote.MagicEditRequest
import com.example.persona.data.remote.NotificationDto
import com.example.persona.data.remote.PostDetailDto
import com.example.persona.data.remote.PostDto
import com.example.persona.data.remote.PostService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val postService: PostService,
    private val userPrefs: UserPreferencesRepository
) {
    /**
     * @class com.example.persona.data.repository.PostRepository
     * @description 动态与互动领域仓库，负责信息流、通知、点赞/收藏、评论、图片上传与 AI 生图等核心业务通道。采用 `MutableSharedFlow` 实现乐观更新事件广播，提升交互响应性并支持回滚；与 Retrofit 契约协作，将后端数据映射为 Domain/DTO，配合 UI 的分页与渲染优化。对应《最终作业.md》的社交广场（B2/B3）与多模态（C2），同时体现从 Mock 到真实服务（C3）的架构弹性。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.data.remote.PostService
     * @关联功能 REQ-B2/B3 社交广场；REQ-C2 多模态；REQ-C3 架构演进
     */

    private val _postInteractEvents = MutableSharedFlow<PostInteractEvent>()
    val postInteractEvents: SharedFlow<PostInteractEvent> = _postInteractEvents.asSharedFlow()

    private suspend fun getUserIdLong(): Long {
        return userPrefs.userId.first()?.toLongOrNull() ?: 0L
    }

    // [Mod] 修复：增加 type 参数支持 (all/followed)
    /**
     * 功能: 拉取动态信息流，支持类型切换与分页；失败返回统一异常。
     * 关联功能: REQ-B3 社交广场-浏览与互动
     */
    suspend fun getFeedPosts(type: String = "all", page: Int = 1): Result<List<PostDto>> {
        return try {
            val userId = getUserIdLong()
            val response = postService.getFeedPosts(userId = userId, type = type, page = page)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNotifications(): Result<List<NotificationDto>> {
        return try {
            val userId = getUserIdLong()
            val response = postService.getNotifications(userId = userId)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUnreadCount(): Long {
        return try {
            val userId = getUserIdLong()
            val response = postService.getUnreadCount(userId = userId)
            if (response.code == 200) response.data ?: 0L else 0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun markNotificationsAsRead() {
        try {
            val userId = getUserIdLong()
            postService.markNotificationsAsRead(userId = userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getPostDetail(postId: Long): Result<PostDetailDto> {
        return try {
            val userId = getUserIdLong()
            val response = postService.getPostDetail(userId = userId, id = postId)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 功能: 点赞/取消点赞（乐观更新），先广播 UI 事件后请求后端，失败由 UI 回滚。
     * 关联功能: REQ-B3 社交广场-互动行为
     */
    suspend fun toggleLike(postId: Long, currentLiked: Boolean, currentCount: Int): Result<String> {
        return try {
            val userId = getUserIdLong()
            // 乐观更新：先通知 UI 变化
            val newLiked = !currentLiked
            val newCount = if (newLiked) currentCount + 1 else currentCount - 1
            _postInteractEvents.emit(
                PostInteractEvent(postId = postId, isLiked = newLiked, likesCount = newCount)
            )

            val response = postService.toggleLike(userId = userId, id = postId, isLiked = newLiked)
            if (response.code == 200) {
                Result.success(response.data ?: "Success")
            } else {
                // 失败回滚逻辑通常由 UI 处理，或者再次 emit 原状态
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 功能: 收藏/取消收藏（乐观更新），同点赞逻辑。
     * 关联功能: REQ-B3 社交广场-互动行为
     */
    suspend fun toggleBookmark(postId: Long, currentBookmarked: Boolean): Result<String> {
        return try {
            val userId = getUserIdLong()
            val newBookmarked = !currentBookmarked
            _postInteractEvents.emit(
                PostInteractEvent(postId = postId, isBookmarked = newBookmarked)
            )

            val response = postService.toggleBookmark(userId = userId, id = postId, isBookmarked = newBookmarked)
            if (response.code == 200) {
                Result.success(response.data ?: "Success")
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 功能: 发表评论或回复；成功返回服务器生成的评论 DTO。
     * 关联功能: REQ-B3 社交广场-互动行为
     */
    suspend fun addComment(postId: Long, content: String, parentId: Long?): Result<CommentDto> {
        return try {
            val request = CommentRequest(content = content, parentId = parentId)
            val userId = getUserIdLong()
            val response = postService.addComment(userId = userId, id = postId, request = request)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // [Restored] 恢复：AI 润色功能
    /**
     * 功能: AI 润色文本，生成更符合人设的表达；返回润色结果字符串。
     * 关联功能: REQ-C2 多模态交互-文本生成
     */
    suspend fun magicEdit(content: String, personaName: String?, description: String?, tags: String?): Result<String> {
        return try {
            val request = MagicEditRequest(content, personaName, description, tags)
            val response = postService.magicEdit(request)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // [Restored] 恢复：创建帖子
    /**
     * 功能: 创建帖子（图文），返回创建成功的 Post DTO。
     * 关联功能: REQ-B2 社交广场-动态发布
     */
    suspend fun createPost(personaId: Long, content: String, imageUrls: List<String>): Result<PostDto> {
        return try {
            val userId = getUserIdLong()
            val request = CreatePostRequest(content = content, imageUrls = imageUrls)
            val response = postService.createPost(userId = userId, personaId = personaId, request = request)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // [Restored] 恢复：上传图片
    /**
     * 功能: 上传图片，返回图片 URL 字符串。
     * 关联功能: REQ-C2 多模态交互-图片上传
     */
    suspend fun uploadImage(file: MultipartBody.Part): Result<String> {
        return try {
            val response = postService.uploadImage(file)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // [Restored] 恢复：AI 生图
    /**
     * 功能: AI 生图，根据提示词生成图片并返回 URL。
     * 关联功能: REQ-C2 多模态交互-文生图
     */
    suspend fun generateAiImage(prompt: String): Result<String> {
        return try {
            val request = GenerateImageRequest(prompt)
            val response = postService.generateAiImage(request)
            if (response.code == 200 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
