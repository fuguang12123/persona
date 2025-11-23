package com.example.persona.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.entity.PostEntity
import com.example.persona.data.repository.PostRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 专门用于 "动态广场" (Post Feed) 的 ViewModel
 * 与原有的 SocialFeedViewModel (智能体列表) 区分开
 */
@HiltViewModel
class PostFeedViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    // UI 状态：直接订阅 Repository 的 Flow (来自 Room)
    // 只要数据库变动，这里会自动更新
    val feedState: StateFlow<List<PostEntity>> = repository.getFeedStream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 下拉刷新状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        // 进入页面自动刷新一次
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 调用 Repository 从网络拉取新数据并存入 Room
                repository.refreshFeed(page = 1)
            } catch (e: Exception) {
                e.printStackTrace()
                // 实际项目中这里可以发送一个 UI Event 弹 Toast
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleLike(post: PostEntity) {
        viewModelScope.launch {
            repository.toggleLike(post)
        }
    }

    fun toggleBookmark(post: PostEntity) {
        viewModelScope.launch {
            // 调用 Repository 的收藏方法 (需确保 Repository 中已实现)
            repository.updateBookmarkStatus(post.id, !post.isBookmarked)
        }
    }
}