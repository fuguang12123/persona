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

    private val _postInteractEvents = MutableSharedFlow<PostInteractEvent>()
    val postInteractEvents: SharedFlow<PostInteractEvent> = _postInteractEvents.asSharedFlow()

    private suspend fun getUserIdLong(): Long {
        return userPrefs.userId.first()?.toLongOrNull() ?: 0L
    }

    // [Mod] 修复：增加 type 参数支持 (all/followed)
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