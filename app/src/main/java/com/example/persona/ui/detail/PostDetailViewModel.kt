package com.example.persona.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.remote.CommentDto
import com.example.persona.data.remote.PostDto
import com.example.persona.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// 评论分组数据结构
data class CommentGroup(
    val root: CommentDto,
    val replies: List<CommentDto> = emptyList()
)

data class PostDetailUiState(
    val isLoading: Boolean = true,
    val isCommentsLoading: Boolean = true,
    val error: String? = null,
    val post: PostDto? = null,
    val isLiked: Boolean = false,
    val likeCount: Int = 0,
    val isBookmarked: Boolean = false,

    val commentGroups: List<CommentGroup> = emptyList(),
    val authorName: String? = null,
    val authorAvatar: String? = null
)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var rawComments: List<CommentDto> = emptyList()

    fun loadPost(postId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = repository.getPostDetail(postId)

            result.onSuccess { detail ->
                rawComments = detail.comments
                val groups = groupComments(rawComments)

                _uiState.update { it.copy(
                    isLoading = false,
                    post = detail.post,
                    isLiked = detail.isLiked,
                    likeCount = detail.post.likes,
                    isBookmarked = detail.isBookmarked,
                    commentGroups = groups,
                    authorName = detail.authorName,
                    authorAvatar = detail.authorAvatar,
                    isCommentsLoading = false
                )}
            }.onFailure { e ->
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message,
                    isCommentsLoading = false
                ) }
            }
        }
    }

    fun refreshComments(postId: Long) {
        loadPost(postId)
    }

    fun toggleLike(postId: Long) {
        // 1. 捕获操作前的旧状态（用于传参和回滚）
        val oldLiked = _uiState.value.isLiked
        val oldCount = _uiState.value.likeCount

        // 2. 乐观更新 UI
        _uiState.update {
            it.copy(
                isLiked = !oldLiked,
                likeCount = if (!oldLiked) oldCount + 1 else oldCount - 1
            )
        }

        // 3. 发起网络请求
        viewModelScope.launch {
            // [Fix] 传入 oldLiked 和 oldCount，Repository 会根据它们计算新状态并广播
            val result = repository.toggleLike(postId, oldLiked, oldCount)

            if (result.isFailure) {
                // 失败回滚
                _uiState.update { it.copy(isLiked = oldLiked, likeCount = oldCount) }
            }
            // 成功无需操作，Repository 已经广播了事件，但详情页其实不需要监听这个事件来更新自己，
            // 因为我们在上面第 2 步已经更新了 UI。
            // (注：如果想做得更完美，详情页也可以监听 EventBus 来防止数据不一致，但目前这样足够了)
        }
    }

    fun toggleBookmark(postId: Long) {
        val oldBookmarked = _uiState.value.isBookmarked

        // 乐观更新
        _uiState.update { it.copy(isBookmarked = !oldBookmarked) }

        viewModelScope.launch {
            // [Fix] 传入 oldBookmarked
            val result = repository.toggleBookmark(postId, oldBookmarked)

            if (result.isFailure) {
                // 失败回滚
                _uiState.update { it.copy(isBookmarked = oldBookmarked) }
            }
        }
    }

    private fun groupComments(flatList: List<CommentDto>): List<CommentGroup> {
        val roots = flatList.filter { it.rootParentId == null || it.rootParentId == 0L }
        val replies = flatList.filter { it.rootParentId != null && it.rootParentId != 0L }
        return roots.map { root ->
            val childReplies = replies.filter { it.rootParentId == root.id }.sortedBy { it.createdAt }
            CommentGroup(root, childReplies)
        }
    }

    fun sendComment(postId: Long, content: String, parentId: Long? = null) {
        viewModelScope.launch {
            val result = repository.addComment(postId, content, parentId)
            result.onSuccess { newComment ->
                rawComments = listOf(newComment) + rawComments
                val newGroups = groupComments(rawComments)
                _uiState.update { it.copy(commentGroups = newGroups) }
            }
        }
    }
}