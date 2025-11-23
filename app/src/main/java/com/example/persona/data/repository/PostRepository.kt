package com.example.persona.data.repository

import android.util.Log
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.PostDao
import com.example.persona.data.local.entity.PostEntity
import com.example.persona.data.remote.PostDto
import com.example.persona.data.remote.PostService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val postDao: PostDao,
    private val postService: PostService,
    private val userPrefs: UserPreferencesRepository
) {

    fun getFeedStream(): Flow<List<PostEntity>> = flow {
        val userId = getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        postDao.getPostsStream(userId).collect { posts ->
            emit(posts)
        }
    }

    suspend fun refreshFeed(page: Int = 1) {
        val userId = getCurrentUserId() ?: return
        try {
            val response = postService.getFeedPosts(page = page, size = 20)
            if (response.code == 200 && response.data != null) {
                val remotePosts = response.data
                val entities = remotePosts.map { it.toEntity(ownerUserId = userId) }
                postDao.insertAll(entities)
                Log.d("PostRepo", "Synced ${entities.size} posts from server")
            } else {
                throw Exception(response.message)
            }
        } catch (e: Exception) {
            Log.e("PostRepo", "Network error during refresh", e)
            throw e
        }
    }

    // ✅ [New] 保存单条远程数据到本地 (用于发布成功后的立即刷新)
    suspend fun saveRemotePost(postDto: PostDto) {
        val userId = getCurrentUserId() ?: return
        val entity = postDto.toEntity(ownerUserId = userId)
        // 插入到 Room，PostFeedScreen 的 Flow 会自动感应并刷新 UI
        postDao.insertAll(listOf(entity))
        Log.d("PostRepo", "Saved new post ${postDto.id} to local DB")
    }

    suspend fun toggleLike(post: PostEntity) {
        val newLikedState = !post.isLiked
        val newCount = if (newLikedState) post.likeCount + 1 else post.likeCount - 1
        postDao.updateLikeStatus(post.id, newLikedState, newCount)
    }

    suspend fun updateBookmarkStatus(postId: Long, isBookmarked: Boolean) {
        postDao.updateBookmarkStatus(postId, isBookmarked)
    }

    private suspend fun getCurrentUserId(): String? {
        return userPrefs.userId.first()
    }
}