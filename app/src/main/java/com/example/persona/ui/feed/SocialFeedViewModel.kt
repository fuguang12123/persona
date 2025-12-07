package com.example.persona.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

    // 任务句柄，用于取消过期的请求
    private var loadJob: Job? = null

    // 🔥 [新增] 推荐数据的内存缓存
    // 专门用来存上次请求到的推荐智能体，防止切回来又要重新等
    private var cachedRecommendList: List<Persona>? = null

    init {
        // 只有 "全部" 列表需要监听数据库流
        viewModelScope.launch {
            repository.getFeedStream().collectLatest {
                // 只有当前停留在 Tab 0 时，数据库的变动才刷新 UI
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

        // 🔥 [逻辑优化] 切换逻辑升级
        if (index == 1 && cachedRecommendList != null) {
            // 场景：切到“推荐”页，且之前加载过（缓存不为空）
            // 动作：直接显示缓存数据，不发网络请求，不转圈！
            feedList = cachedRecommendList!!
            isEndReached = true
            isLoading = false
            // 此时 loadJob?.cancel() 就不需要了，因为我们根本没发起新请求
        } else {
            // 场景：切到“广场”，或者第一次切到“推荐”（没缓存）
            // 动作：走标准刷新流程（会触发 loadJob.cancel 和网络请求）
            refresh()
        }
    }

    // 刷新：重置页码，重新加载
    // (注意：下拉刷新时调用这个，会强制重新请求，更新缓存)
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
        // 每次请求前，先取消上一次可能的慢请求
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            isLoading = true

            // 只有当没有缓存可用，或者强制刷新时，才清空列表显示 Loading
            // 如果是 Tab 0，总是要清空的或者依赖 Flow，这里保留原逻辑即可
            if (currentTab == 1) {
                feedList = emptyList()
            }

            try {
                if (currentTab == 1) {
                    // 推荐列表：请求慢接口
                    val list = repository.getRecommendList()

                    if (currentTab == 1) {
                        feedList = list
                        // 🔥 [新增] 请求成功后，存入缓存
                        cachedRecommendList = list
                        isEndReached = true
                    }
                } else {
                    // 广场列表：分页加载
                    val pageToLoad = if (isRefresh) 1 else currentPage + 1

                    // 这里请求成功后写入 DB，触发上面的 Flow 更新 feedList
                    val hasMore = repository.fetchFeed(pageToLoad, pageSize, "all")

                    if (currentTab == 0) {
                        if (hasMore) {
                            currentPage = pageToLoad
                            isEndReached = false
                        } else {
                            isEndReached = true
                        }
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