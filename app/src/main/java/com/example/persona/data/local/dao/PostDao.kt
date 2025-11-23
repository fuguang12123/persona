package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts WHERE ownerUserId = :userId ORDER BY createdAt DESC")
    fun getPostsStream(userId: String): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    // 点赞更新
    @Query("UPDATE posts SET isLiked = :isLiked, likeCount = :newCount WHERE id = :postId")
    suspend fun updateLikeStatus(postId: Long, isLiked: Boolean, newCount: Int)

    // [New] 收藏更新 (乐观更新)
    @Query("UPDATE posts SET isBookmarked = :isBookmarked WHERE id = :postId")
    suspend fun updateBookmarkStatus(postId: Long, isBookmarked: Boolean)
}