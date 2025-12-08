package com.example.persona.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.LoginRequest
import com.example.persona.data.remote.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录视图模型
 * 处理登录逻辑和UI状态管理
 *
 * @param authService 认证服务,用于调用登录API
 * @param userPrefs 用户偏好设置仓库,用于保存认证信息
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    /** 用户名输入 */
    var username by mutableStateOf("")

    /** 密码输入 */
    var password by mutableStateOf("")

    /** UI状态,控制加载/错误/成功显示 */
    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    /** 登录成功标志,用于触发导航 */
    var loginSuccess by mutableStateOf(false)

    /**
     * 处理登录按钮点击事件
     * 验证输入后调用登录API,并保存认证信息
     */
    fun onLoginClick() {
        // 验证输入是否为空
        if (username.isBlank() || password.isBlank()) {
            uiState = LoginUiState.Error("用户名或密码不能为空")
            return
        }

        viewModelScope.launch {
            uiState = LoginUiState.Loading
            try {
                // 构造登录请求
                val request = LoginRequest(username, password)
                val response = authService.login(request)
                val body = response.body()

                // 检查响应是否成功
                if (response.isSuccessful && body != null && body.code == 200 && body.data != null) {
                    val map = body.data
                    val token = map["token"].toString()
                    val userId = map["userId"].toString()

                    // 保存认证信息到本地
                    userPrefs.saveAuthData(token, userId)

                    uiState = LoginUiState.Success
                    loginSuccess = true
                } else {
                    // 处理登录失败
                    val msg = body?.message ?: "登录失败 (${response.code()})"
                    uiState = LoginUiState.Error(msg)
                }
            } catch (e: Exception) {
                // 处理网络错误
                e.printStackTrace()
                uiState = LoginUiState.Error(e.message ?: "网络连接错误")
            }
        }
    }

    /**
     * 导航完成后重置状态
     * 防止重复导航
     */
    fun onNavigated() {
        loginSuccess = false
        uiState = LoginUiState.Idle
    }
}

/**
 * 登录UI状态密封类
 * 用于表示不同的UI状态
 */
sealed class LoginUiState {
    /** 空闲状态 */
    object Idle : LoginUiState()

    /** 加载中状态 */
    object Loading : LoginUiState()

    /** 成功状态 */
    object Success : LoginUiState()

    /** 错误状态
     * @param msg 错误消息
     */
    data class Error(val msg: String) : LoginUiState()
}
