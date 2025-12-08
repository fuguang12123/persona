package com.example.persona.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.remote.CommentDto
import com.example.persona.data.remote.PostDto
import com.example.persona.data.repository.PersonaRepository
import com.example.persona.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentGroup(val root: CommentDto, val replies: List<CommentDto> = emptyList())

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
    val authorAvatar: String? = null,
    val isAuthorFollowed: Boolean = false
)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val personaRepository: PersonaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var rawComments: List<CommentDto> = emptyList()

    fun loadPost(postId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = postRepository.getPostDetail(postId)

            result.onSuccess { detail ->
                rawComments = detail.comments
                val groups = groupComments(rawComments)

                // [New] 获取作者关注状态
                val personaId = detail.post.personaId.toLongOrNull() ?: 0L
                val isFollowed = if (personaId > 0) personaRepository.getFollowStatus(personaId) else false

                _uiState.update { it.copy(
                    isLoading = false,
                    post = detail.post,
                    isLiked = detail.isLiked,
                    likeCount = detail.post.likes,
                    isBookmarked = detail.isBookmarked,
                    commentGroups = groups,
                    authorName = detail.authorName,
                    authorAvatar = detail.authorAvatar,
                    isCommentsLoading = false,
                    isAuthorFollowed = isFollowed
                )}
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message, isCommentsLoading = false) }
            }
        }
    }

    // [New] 动态详情页关注作者
    fun toggleFollowAuthor() {
        val post = _uiState.value.post ?: return
        val pid = post.personaId.toLongOrNull() ?: return

        val oldStatus = _uiState.value.isAuthorFollowed
        _uiState.update { it.copy(isAuthorFollowed = !oldStatus) } // 乐观更新

        viewModelScope.launch {
            if (!personaRepository.toggleFollow(pid)) {
                _uiState.update { it.copy(isAuthorFollowed = oldStatus) } // 失败回滚
            }
        }
    }

    fun refreshComments(postId: Long) { loadPost(postId) }

    fun toggleLike(postId: Long) {
        val oldLiked = _uiState.value.isLiked
        val oldCount = _uiState.value.likeCount
        _uiState.update { it.copy(isLiked = !oldLiked, likeCount = if (!oldLiked) oldCount + 1 else oldCount - 1) }
        viewModelScope.launch {
            val result = postRepository.toggleLike(postId, oldLiked, oldCount)
            if (result.isFailure) _uiState.update { it.copy(isLiked = oldLiked, likeCount = oldCount) }
        }
    }

    fun toggleBookmark(postId: Long) {
        val old = _uiState.value.isBookmarked
        _uiState.update { it.copy(isBookmarked = !old) }
        viewModelScope.launch {
            val result = postRepository.toggleBookmark(postId, old)
            if (result.isFailure) _uiState.update { it.copy(isBookmarked = old) }
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

    /**
     * Posts a comment to the current post.
     *
     * @param postId The ID of the post to which the comment is being added.
     * @param content The text content of the comment.
     * @param parentId The optional ID of a parent comment if this is a reply.
     */
    fun sendComment(postId: Long, content: String, parentId: Long? = null) {
        viewModelScope.launch {
            val result = postRepository.addComment(postId, content, parentId)
            result.onSuccess { newComment ->
                rawComments = listOf(newComment) + rawComments
                val newGroups = groupComments(rawComments)
                _uiState.update { it.copy(commentGroups = newGroups) }
            }
        }
    }
}