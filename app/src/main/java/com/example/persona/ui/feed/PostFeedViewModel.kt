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

/**
 * 动态信息流 ViewModel
 *
 * @description 动态信息流的状态管理与交互调度:观察乐观更新事件(点赞/收藏),控制 Tab(全部/关注)
 * 与刷新节奏,统一从 Repository 拉取数据并更新 UI 状态。对应《最终作业.md》的社交广场浏览与互动,
 * 体现事件驱动与单向数据流。
 *
 * @property repository 动态数据仓库,负责网络请求与本地缓存
 *
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see PostRepository 动态数据仓库
 * @关联功能 REQ-B3 社交广场
 */
@HiltViewModel
class PostFeedViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    // 动态列表状态流 (UI 订阅此状态)
    private val _feedState = MutableStateFlow<List<PostDto>>(emptyList())
    val feedState = _feedState.asStateFlow()

    // 刷新状态标志 (控制加载动画显示)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // 当前 Tab 索引 (0=全部, 1=关注)
    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    init {
        // 初始化时订阅互动事件并加载数据
        observePostEvents()
        refresh()
    }

    /**
     * 监听动态互动事件 (点赞/收藏)
     *
     * @description 订阅 Repository 发出的乐观更新事件,当用户点赞或收藏时,
     * 立即更新本地状态,无需等待网络响应,提升用户体验。
     */
    private fun observePostEvents() {
        viewModelScope.launch {
            repository.postInteractEvents.collect { event ->
                // 根据事件 ID 更新对应动态的状态
                _feedState.update { list ->
                    list.map {
                        if (it.id == event.postId) it.copy(
                            isLiked = event.isLiked ?: it.isLiked, // 更新点赞状态
                            likes = event.likesCount ?: it.likes, // 更新点赞数
                            isBookmarked = event.isBookmarked ?: it.isBookmarked // 更新收藏状态
                        ) else it
                    }
                }
            }
        }
    }

    /**
     * 切换 Tab 页
     *
     * @param index 目标 Tab 索引 (0=全部, 1=关注)
     * @description 切换 Tab 时清空当前列表并重新加载对应类型的数据
     */
    fun switchTab(index: Int) {
        _currentTab.value = index
        refresh() // 切换后刷新数据
    }

    /**
     * 刷新动态列表
     *
     * @description 根据当前 Tab 类型 (全部/关注) 从服务器拉取最新数据,
     * 请求期间显示加载动画,成功后更新 UI 状态
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true // 开始刷新
            // 根据当前 Tab 决定请求类型
            val type = if (_currentTab.value == 1) "followed" else "all"
            val result = repository.getFeedPosts(type)
            result.onSuccess { posts -> _feedState.value = posts } // 更新列表
            _isRefreshing.value = false // 结束刷新
        }
    }

    /**
     * 点赞/取消点赞
     *
     * @param post 目标动态对象
     * @description 调用 Repository 执行点赞操作,乐观更新已在 observePostEvents 中处理
     */
    fun toggleLike(post: PostDto) {
        viewModelScope.launch {
            repository.toggleLike(post.id, post.isLiked, post.likes)
        }
    }

    /**
     * 收藏/取消收藏
     *
     * @param post 目标动态对象
     * @description 调用 Repository 执行收藏操作,乐观更新已在 observePostEvents 中处理
     */
    fun toggleBookmark(post: PostDto) {
        viewModelScope.launch {
            repository.toggleBookmark(post.id, post.isBookmarked)
        }
    }
}
