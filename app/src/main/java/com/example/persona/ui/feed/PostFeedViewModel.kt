package com.example.persona.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.remote.PostDto
import com.example.persona.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostFeedViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow<List<PostDto>>(emptyList())
    val feedState = _feedState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // [New] 0=All, 1=Followed
    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    init {
        observePostEvents()
        refresh()
    }

    private fun observePostEvents() {
        viewModelScope.launch {
            repository.postInteractEvents.collect { event ->
                _feedState.update { list ->
                    list.map {
                        if (it.id == event.postId) it.copy(
                            isLiked = event.isLiked ?: it.isLiked,
                            likes = event.likesCount ?: it.likes,
                            isBookmarked = event.isBookmarked ?: it.isBookmarked
                        ) else it
                    }
                }
            }
        }
    }

    fun switchTab(index: Int) {
        _currentTab.value = index
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val type = if (_currentTab.value == 1) "followed" else "all"
            val result = repository.getFeedPosts(type)
            result.onSuccess { posts -> _feedState.value = posts }
            _isRefreshing.value = false
        }
    }

    fun toggleLike(post: PostDto) {
        viewModelScope.launch { repository.toggleLike(post.id, post.isLiked, post.likes) }
    }

    fun toggleBookmark(post: PostDto) {
        viewModelScope.launch { repository.toggleBookmark(post.id, post.isBookmarked) }
    }
}