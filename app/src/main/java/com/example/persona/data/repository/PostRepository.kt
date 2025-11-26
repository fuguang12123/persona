package com.example.persona.data.repository

import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.PostInteractEvent
import com.example.persona.data.remote.CommentDto
import com.example.persona.data.remote.CommentRequest
import com.example.persona.data.remote.MagicEditRequest
import com.example.persona.data.remote.NotificationDto
import com.example.persona.data.remote.PostDetailDto
import com.example.persona.data.remote.PostDto
import com.example.persona.data.remote.PostService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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

    suspend fun getFeedPosts(page: Int = 1): Result<List<PostDto>> {
        return try {
            val userId = getUserIdLong()
            val response = postService.getFeedPosts(userId = userId, page = page)
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

    // [New] 获取未读数量
    suspend fun getUnreadCount(): Long {
        return try {
            val userId = getUserIdLong()
            val response = postService.getUnreadCount(userId = userId)
            if (response.code == 200) response.data ?: 0L else 0L
        } catch (e: Exception) {
            0L
        }
    }

    // [New] 标记为已读
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
            val response = postService.toggleLike(userId = userId, id = postId)
            if (response.code == 200) {
                val newLiked = !currentLiked
                val newCount = if (newLiked) currentCount + 1 else currentCount - 1
                _postInteractEvents.emit(
                    PostInteractEvent(postId = postId, isLiked = newLiked, likesCount = newCount)
                )
                Result.success(response.data ?: "Success")
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(postId: Long, currentBookmarked: Boolean): Result<String> {
        return try {
            val userId = getUserIdLong()
            val response = postService.toggleBookmark(userId = userId, id = postId)
            if (response.code == 200) {
                val newBookmarked = !currentBookmarked
                _postInteractEvents.emit(
                    PostInteractEvent(postId = postId, isBookmarked = newBookmarked)
                )
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
}