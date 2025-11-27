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

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    // 初始路由状态：null 表示正在检查，暂不显示界面
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        determineStartDestination()
    }

    private fun determineStartDestination() {
        viewModelScope.launch {
            // 1. 读取本地 Token
            val token = userPrefs.authToken.first()

            if (!token.isNullOrBlank()) {
                // 2. 有 Token -> 默认认为已登录，直接进主页 (无缝体验)
                _startDestination.value = Screen.ChatList.route

                // 3. 后台静默续期 (不阻塞 UI)
                refreshTokenSilent()
            } else {
                // 4. 无 Token -> 进登录页
                _startDestination.value = Screen.Login.route
            }
        }
    }

    private suspend fun refreshTokenSilent() {
        try {
            Log.d("MainViewModel", "Silent refreshing token...")
            val response = authService.refreshToken()

            if (response.isSuccessful && response.body()?.code == 200) {
                val map = response.body()?.data
                val newToken = map?.get("token")?.toString()
                if (!newToken.isNullOrBlank()) {
                    // ✅ 成功续期：更新本地 Token
                    Log.d("MainViewModel", "Token refreshed successfully.")
                    val userId = map?.get("userId")?.toString() ?: ""
                    userPrefs.saveAuthData(newToken, userId)
                }
            } else {
                Log.w("MainViewModel", "Token refresh failed: ${response.code()}")
                // 如果是 401，AuthInterceptor 会自动处理踢下线，这里无需操作
            }
        } catch (e: Exception) {
            // 网络错误：忽略。保持当前登录状态，允许离线使用。
            Log.e("MainViewModel", "Network error during token refresh: ${e.message}")
        }
    }
}