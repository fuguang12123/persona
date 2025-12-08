package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

/**
 * @class com.example.persona.data.local.dao.PostDao
 * @description 动态(Post) 的 Room DAO：负责首页动态流的 Flow 观察、详情订阅、批量插入与交互状态的局部更新（点赞/收藏）。配合 `PostRepository` 的乐观更新事件广播，UI 能够在网络返回前保持流畅交互；返回后与本地状态对齐。对应《最终作业.md》的社交广场浏览与互动路径。
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see com.example.persona.data.repository.PostRepository
 * @关联功能 REQ-B3 社交广场
 */
@Dao
interface PostDao {
    // 获取首页动态流
    @Query("SELECT * FROM posts WHERE ownerUserId = :userId ORDER BY createdAt DESC")
    fun getPostsStream(userId: String): Flow<List<PostEntity>>

    // [New] 详情页专用：观察单条动态的变化 (SSOT 核心)
    // 只要这张表里的这条数据变了 (比如点赞数+1)，详情页 UI 就会自动刷新
    @Query("SELECT * FROM posts WHERE id = :postId")
    fun getPostById(postId: Long): Flow<PostEntity?>

    // 插入或更新动态
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    // 点赞状态更新 (局部更新)
    @Query("UPDATE posts SET isLiked = :isLiked, likeCount = :newCount WHERE id = :postId")
    suspend fun updateLikeStatus(postId: Long, isLiked: Boolean, newCount: Int)

    // 收藏状态更新 (局部更新)
    @Query("UPDATE posts SET isBookmarked = :isBookmarked WHERE id = :postId")
    suspend fun updateBookmarkStatus(postId: Long, isBookmarked: Boolean)
}
