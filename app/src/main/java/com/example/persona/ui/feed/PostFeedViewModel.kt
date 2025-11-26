package com.example.persona.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.remote.PostDto
import com.example.persona.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostFeedViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow<List<PostDto>>(emptyList())
    val feedState: StateFlow<List<PostDto>> = _feedState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        observePostEvents()
        refresh()
    }

    private fun observePostEvents() {
        viewModelScope.launch {
            // 如果这里报错，请检查 PostRepository 是否定义了 postInteractEvents
            repository.postInteractEvents.collect { event ->
                updatePostInList(event.postId) { oldPost ->
                    oldPost.copy(
                        isLiked = event.isLiked ?: oldPost.isLiked,
                        likes = event.likesCount ?: oldPost.likes,
                        isBookmarked = event.isBookmarked ?: oldPost.isBookmarked
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.getFeedPosts().onSuccess { posts ->
                _feedState.value = posts
            }
            _isRefreshing.value = false
        }
    }

    fun toggleLike(post: PostDto) {
        updatePostInList(post.id) { it.copy(
            isLiked = !it.isLiked,
            likes = if (it.isLiked) it.likes - 1 else it.likes + 1
        )}
        viewModelScope.launch {
            val result = repository.toggleLike(post.id, post.isLiked, post.likes)
            if (result.isFailure) {
                updatePostInList(post.id) { it.copy(isLiked = !it.isLiked, likes = if (it.isLiked) it.likes - 1 else it.likes + 1)}
            }
        }
    }

    fun toggleBookmark(post: PostDto) {
        updatePostInList(post.id) { it.copy(isBookmarked = !it.isBookmarked)}
        viewModelScope.launch {
            val result = repository.toggleBookmark(post.id, post.isBookmarked)
            if (result.isFailure) {
                updatePostInList(post.id) { it.copy(isBookmarked = !it.isBookmarked)}
            }
        }
    }

    private fun updatePostInList(postId: Long, update: (PostDto) -> PostDto) {
        _feedState.update { list ->
            list.map { if (it.id == postId) update(it) else it }
        }
    }
}