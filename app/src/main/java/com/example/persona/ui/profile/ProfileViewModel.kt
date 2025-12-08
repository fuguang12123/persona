package com.example.persona.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.Persona
import com.example.persona.data.model.UserDto // ✅ 导入
import com.example.persona.data.remote.AuthService
import com.example.persona.data.remote.PostDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * 个人中心UI状态数据类
 *
 * @param isLoading 是否正在加载数据
 * @param user 当前用户信息
 * @param myPersonas 我的智能体列表
 * @param myPosts 我的动态列表
 * @param myLikes 我的点赞列表
 * @param myBookmarks 我的收藏列表
 * @param activeTab 当前激活的标签页索引 (0:动态 1:点赞 2:收藏 3:智能体)
 */
data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: UserDto? = null,
    val myPersonas: List<Persona> = emptyList(),
    val myPosts: List<PostDto> = emptyList(),
    val myLikes: List<PostDto> = emptyList(),
    val myBookmarks: List<PostDto> = emptyList(),
    val activeTab: Int = 0
)

/**
 * 个人中心视图模型
 * 负责加载用户资料和各个标签页的内容
 *
 * @param authService 认证服务,用于调用用户相关API
 * @param userPrefs 用户偏好设置仓库
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    /** UI状态流(内部可变) */
    private val _uiState = MutableStateFlow(ProfileUiState())
    /** UI状态流(对外只读) */
    val uiState = _uiState.asStateFlow()

    init {
        // 初始化时加载用户资料
        loadProfile()
    }

    /**
     * 加载用户资料
     * 获取用户信息并加载默认标签页(动态)的内容
     */
    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 获取用户资料
                val response = authService.getMyProfile()
                val body = response.body()
                if (response.isSuccessful && body != null && body.code == 200) {
                    _uiState.update { it.copy(user = body.data) }
                }

                // 加载第一个标签页内容
                loadTabContent(0)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 切换标签页
     * 更新激活的标签页并加载对应内容
     *
     * @param index 标签页索引 (0:动态 1:点赞 2:收藏 3:智能体)
     */
    fun switchTab(index: Int) {
        _uiState.update { it.copy(activeTab = index) }
        loadTabContent(index)
    }

    /**
     * 加载标签页内容
     * 根据标签页索引调用不同的API获取数据
     *
     * @param index 标签页索引
     */
    private fun loadTabContent(index: Int) {
        viewModelScope.launch {
            try {
                when (index) {
                    0 -> { // 我的动态
                        val res = authService.getMyPosts()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myPosts = res.body()?.data ?: emptyList()) }
                        }
                    }
                    1 -> { // 我的点赞
                        val res = authService.getMyLikes()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myLikes = res.body()?.data ?: emptyList()) }
                        }
                    }
                    2 -> { // 我的收藏
                        val res = authService.getMyBookmarks()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myBookmarks = res.body()?.data ?: emptyList()) }
                        }
                    }
                    3 -> { // 我的智能体
                        val res = authService.getMyPersonas()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myPersonas = res.body()?.data ?: emptyList()) }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
