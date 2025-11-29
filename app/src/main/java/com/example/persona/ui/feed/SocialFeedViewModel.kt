package com.example.persona.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialFeedViewModel @Inject constructor(
    private val repository: PersonaRepository
) : ViewModel() {

    // 列表数据源
    var feedList by mutableStateOf<List<Persona>>(emptyList())
    var isLoading by mutableStateOf(false)

    // 分页相关状态
    var currentPage by mutableStateOf(1)
    var isEndReached by mutableStateOf(false)
    private val pageSize = 20

    // 0=All (分页), 1=Recommend (不分页)
    var currentTab by mutableStateOf(0)

    init {
        // 只有 "全部" 列表需要监听数据库流
        // 当 currentTab == 0 时，Flow 的数据才会更新 feedList
        viewModelScope.launch {
            repository.getFeedStream().collectLatest {
                if (currentTab == 0) {
                    feedList = it
                }
            }
        }
        refresh()
    }

    fun switchTab(index: Int) {
        if (currentTab == index) return
        currentTab = index
        refresh()
    }

    // 刷新：重置页码，重新加载
    fun refresh() {
        currentPage = 1
        isEndReached = false
        loadData(isRefresh = true)
    }

    // 加载下一页
    fun loadNextPage() {
        if (isLoading || isEndReached || currentTab == 1) return
        loadData(isRefresh = false)
    }

    private fun loadData(isRefresh: Boolean) {
        viewModelScope.launch {
            isLoading = true

            // [新增] 只有在切换到推荐页时（或刷新推荐页时），为了体验清空旧数据
            // 这样配合 UI 层的 isLoading 就能显示全屏加载动画，而不是显示上一页残留的数据
            if (currentTab == 1) {
                feedList = emptyList()
            }

            try {
                if (currentTab == 1) {
                    // 推荐列表：直接获取不分页
                    val list = repository.getRecommendList()
                    feedList = list
                    isEndReached = true // 推荐列表一次性加载完
                } else {
                    // 广场列表：分页加载
                    val pageToLoad = if (isRefresh) 1 else currentPage + 1

                    val hasMore = repository.fetchFeed(pageToLoad, pageSize, "all")

                    if (hasMore) {
                        currentPage = pageToLoad
                        isEndReached = false
                    } else {
                        isEndReached = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
}