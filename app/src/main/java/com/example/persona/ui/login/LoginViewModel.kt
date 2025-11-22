package com.example.persona.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
// import com.example.persona.data.remote.AuthService // 如果没写好后端接口，先注释掉
// import com.example.persona.data.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    // private val authService: AuthService, // 暂时注释掉，避免报错，等你后端好了再解开
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    var username by mutableStateOf("")
    var password by mutableStateOf("")

    // 用于控制 Loading 和 Error 显示
    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    // ✅ 新增：用于通知 UI 跳转 (兼容自动登录逻辑)
    var loginSuccess by mutableStateOf(false)

    init {
        // ❌ 方案1: 自动登录 (暂时禁用)
        // checkAutoLogin()
    }

    private fun checkAutoLogin() {
        viewModelScope.launch {
            val token = userPrefs.authToken.first()
            if (!token.isNullOrBlank()) {
                loginSuccess = true // 直接触发跳转
            }
        }
    }

    // ✅ 修改：不再需要传入 onSuccess 回调，改用状态控制
    fun onLoginClick() {
        if (username.isBlank() || password.isBlank()) {
            uiState = LoginUiState.Error("用户名或密码不能为空")
            return
        }

        viewModelScope.launch {
            uiState = LoginUiState.Loading
            try {
                // ⚠️ 假登录逻辑 (Backdoor)
                if (username == "admin") {
                    delay(1000) // 模拟网络延迟
                    // 存入假 Token 和假 UserId (对应数据库里的数据)
                    userPrefs.saveAuthData("fake_admin_token", "1")

                    uiState = LoginUiState.Success
                    loginSuccess = true
                    return@launch
                }

                /* --- 真实 API 逻辑 (等你后端 JWT 写好了取消注释) ---
                val response = authService.login(LoginRequest(username, password))
                // 注意：配合新的 BaseResponse，这里逻辑可能需要微调
                if (response.isSuccess()) {
                    userPrefs.saveAuthData(response.data!!.token, response.data.userId.toString())
                    uiState = LoginUiState.Success
                    loginSuccess = true
                } else {
                    uiState = LoginUiState.Error(response.message)
                }
                */

                // 如果不是 admin 且没开真实 API
                uiState = LoginUiState.Error("目前仅支持 'admin' 账号进行测试")

            } catch (e: Exception) {
                uiState = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }

    // ✅ 新增：离线体验模式
    fun onOfflineGuestClick() {
        viewModelScope.launch {
            uiState = LoginUiState.Loading
            delay(500)
            // 写入假数据，强行进入
            userPrefs.saveAuthData("offline_guest_token", "2")

            uiState = LoginUiState.Success
            loginSuccess = true
        }
    }

    // 重置状态
    fun onNavigated() {
        loginSuccess = false
        uiState = LoginUiState.Idle
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val msg: String) : LoginUiState()
}