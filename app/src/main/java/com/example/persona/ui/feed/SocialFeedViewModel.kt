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

/**
 * 智能体广场 ViewModel
 *
 * @description 社交广场的状态管理与分页加载控制器:订阅本地 Flow(广场列表),管理分页页码与加载状态,
 * 实现"全部/推荐"双 Tab 的切换与缓存复用。通过 Repository 将远端数据写入 Room,UI 仅观察 SSOT 流,
 * 保证一致与性能。对应《最终作业.md》社交广场浏览与互动路径,并体现从 Mock 到真实服务的可插拔性。
 *
 * @property repository 智能体数据仓库,负责网络请求与本地缓存
 *
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see PersonaRepository 智能体数据仓库
 * @关联功能 REQ-B3 社交广场;REQ-C3 架构演进
 */
@HiltViewModel
class SocialFeedViewModel @Inject constructor(
    private val repository: PersonaRepository
) : ViewModel() {

    // 列表数据源 (UI 直接观察此状态)
    var feedList by mutableStateOf<List<Persona>>(emptyList())

    // 加载状态标志 (控制加载动画显示)
    var isLoading by mutableStateOf(false)

    // 分页相关状态
    var currentPage by mutableStateOf(1) // 当前页码
    var isEndReached by mutableStateOf(false) // 是否到达列表末尾
    private val pageSize = 20 // 每页数据量

    // 当前 Tab 索引 (0=全部, 1=推荐)
    var currentTab by mutableStateOf(0)

    // 任务句柄,用于取消过期的请求
    private var loadJob: Job? = null

    // 推荐数据的内存缓存 (避免重复请求慢接口)
    private var cachedRecommendList: List<Persona>? = null

    init {
        // 订阅数据库流 (仅"全部"Tab 需要)
        viewModelScope.launch {
            repository.getFeedStream().collectLatest {
                // 只有当前停留在 Tab 0 时,数据库的变动才刷新 UI
                if (currentTab == 0) {
                    feedList = it
                }
            }
        }
        refresh() // 初始化加载数据
    }

    /**
     * 切换 Tab 页
     *
     * @param index 目标 Tab 索引 (0=全部, 1=推荐)
     * @description 切换 Tab 时,优先使用缓存数据 (推荐页),避免重复请求。
     * 如果无缓存或切换到全部页,则触发标准刷新流程。
     */
    fun switchTab(index: Int) {
        if (currentTab == index) return // 重复点击不处理
        currentTab = index

        if (index == 1 && cachedRecommendList != null) {
            // 场景:切到"推荐"页,且之前加载过 (缓存不为空)
            // 动作:直接显示缓存数据,不发网络请求,不转圈
            feedList = cachedRecommendList!!
            isEndReached = true
            isLoading = false
        } else {
            // 场景:切到"广场",或者第一次切到"推荐" (没缓存)
            // 动作:走标准刷新流程
            refresh()
        }
    }

    /**
     * 刷新数据
     *
     * @description 重置页码并重新加载数据,适用于下拉刷新或 Tab 切换。
     * 强制重新请求会更新缓存。
     */
    fun refresh() {
        currentPage = 1
        isEndReached = false
        loadData(isRefresh = true)
    }

    /**
     * 加载下一页数据
     *
     * @description 滚动到底部时触发,仅"全部"Tab 支持分页加载。
     * 推荐页为一次性加载,不支持翻页。
     */
    fun loadNextPage() {
        if (isLoading || isEndReached || currentTab == 1) return
        loadData(isRefresh = false)
    }

    /**
     * 加载数据核心逻辑
     *
     * @param isRefresh 是否为刷新操作 (刷新时重置页码)
     * @description 根据当前 Tab 类型决定加载逻辑:
     * - 推荐页:请求推荐接口,结果存入缓存
     * - 全部页:分页请求,写入数据库,UI 通过 Flow 自动更新
     */
    private fun loadData(isRefresh: Boolean) {
        // 取消上一次未完成的请求 (防止并发冲突)
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            isLoading = true

            // 推荐页请求时清空列表显示 Loading
            if (currentTab == 1) {
                feedList = emptyList()
            }

            try {
                if (currentTab == 1) {
                    // 推荐列表:请求慢接口
                    val list = repository.getRecommendList()

                    // 仅当仍停留在推荐页时更新 UI
                    if (currentTab == 1) {
                        feedList = list
                        cachedRecommendList = list // 存入缓存
                        isEndReached = true
                    }
                } else {
                    // 广场列表:分页加载
                    val pageToLoad = if (isRefresh) 1 else currentPage + 1

                    // 请求成功后写入 DB,触发 Flow 更新 feedList
                    val hasMore = repository.fetchFeed(pageToLoad, pageSize, "all")

                    // 仅当仍停留在全部页时更新分页状态
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
                e.printStackTrace() // 错误处理 (可优化为 Toast 提示)
            } finally {
                isLoading = false
            }
        }
    }
}
