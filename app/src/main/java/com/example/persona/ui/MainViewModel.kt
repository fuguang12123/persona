package com.example.persona.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.Screen
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.remote.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主视图模型
 * 负责应用启动时的路由判断和静默Token续期
 *
 * 核心逻辑:
 * 1. 应用启动时检查本地是否有Token
 * 2. 有Token -> 直接进入主页(无缝体验),然后后台续期Token
 * 3. 无Token -> 进入登录页
 * 4. Token续期失败不影响用户体验,仅记录日志
 *
 * @param authService 认证服务,用于Token续期
 * @param userPrefs 用户偏好设置仓库,用于读取和保存Token
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    /**
     * 启动目的地状态流(内部可变)
     * null: 正在检查Token,暂不显示界面(避免闪屏)
     * Screen.Login.route: 跳转到登录页
     * Screen.ChatList.route: 跳转到主页
     */
    private val _startDestination = MutableStateFlow<String?>(null)

    /**
     * 启动目的地状态流(对外只读)
     * 应用启动时监听此流,获取初始路由
     */
    val startDestination = _startDestination.asStateFlow()

    init {
        // 初始化时执行路由判断
        determineStartDestination()
    }

    /**
     * 确定启动目的地
     * 根据本地Token状态决定应用的初始路由
     *
     * 设计理念:
     * - 快速启动: 有Token立即进入主页,不等待网络验证
     * - 静默续期: 进入主页后,后台自动续期Token,用户无感知
     * - 容错设计: 续期失败不影响用户使用,AuthInterceptor会处理401
     */
    private fun determineStartDestination() {
        viewModelScope.launch {
            // 1. 读取本地存储的Token(DataStore)
            val token = userPrefs.authToken.first()

            if (!token.isNullOrBlank()) {
                // 2. 有Token -> 默认认为已登录,直接进主页(无缝体验)
                //    优点: 启动速度快,用户无需等待网络请求
                //    安全性: AuthInterceptor会拦截所有请求,自动处理Token失效
                _startDestination.value = Screen.ChatList.route

                // 3. 后台静默续期Token(不阻塞UI)
                //    目的: 延长登录有效期,避免用户频繁登录
                //    注意: 即使续期失败,用户也能正常使用(直到Token真正过期)
                refreshTokenSilent()
            } else {
                // 4. 无Token -> 进入登录页
                _startDestination.value = Screen.Login.route
            }
        }
    }

    /**
     * 静默刷新Token
     * 在后台调用Token续期API,更新本地Token
     *
     * 续期策略:
     * - 成功: 更新本地Token和UserId,延长登录有效期
     * - 失败: 仅记录日志,不影响用户当前操作
     * - 401: AuthInterceptor会自动清除Token并跳转到登录页
     * - 网络错误: 忽略,允许用户离线使用(直到Token过期)
     */
    private suspend fun refreshTokenSilent() {
        try {
            Log.d("MainViewModel", "Silent refreshing token...")
            val response = authService.refreshToken()

            if (response.isSuccessful && response.body()?.code == 200) {
                // ✅ 续期成功: 解析新Token并保存到本地
                val map = response.body()?.data
                val newToken = map?.get("token")?.toString()

                if (!newToken.isNullOrBlank()) {
                    Log.d("MainViewModel", "Token refreshed successfully.")
                    val userId = map?.get("userId")?.toString() ?: ""
                    // 保存新Token到DataStore
                    userPrefs.saveAuthData(newToken, userId)
                }
            } else {
                // ⚠️ 续期失败: 记录日志
                // 如果是401,AuthInterceptor会自动处理踢下线,这里无需额外操作
                Log.w("MainViewModel", "Token refresh failed: ${response.code()}")
            }
        } catch (e: Exception) {
            // ❌ 网络错误: 仅记录日志,不影响用户体验
            // 设计理念: 允许用户在网络不稳定时继续使用应用
            // 后果: 用户在Token过期前仍可正常使用,过期后会被AuthInterceptor拦截
            Log.e("MainViewModel", "Network error during token refresh: ${e.message}")
        }
    }
}
