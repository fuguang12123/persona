package com.example.persona.ui.login
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.remote.AuthService
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authService: AuthService, // 实际项目中建议中间加一层 Repository
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    var username by mutableStateOf("")
    var password by mutableStateOf("")

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    fun onLoginClick(onLoginSuccess: () -> Unit) {
        viewModelScope.launch {
            uiState = LoginUiState.Loading
            try {
                // 注意：这里是直接调用，建议处理 HTTP 异常
                val response = authService.login(LoginRequest(username, password))
                val body = response.body()

                if (response.isSuccessful && body?.code == 200 && body.data != null) {
                    // 1. 保存 Token 到 DataStore
                    userPrefs.saveAuthData(body.data.token, body.data.userId)
                    // 2. 更新状态
                    uiState = LoginUiState.Success
                    // 3. 导航
                    onLoginSuccess()
                } else {
                    uiState = LoginUiState.Error(body?.message ?: "登录失败")
                }
            } catch (e: Exception) {
                uiState = LoginUiState.Error(e.message ?: "网络错误")
            }
        }
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val msg: String) : LoginUiState()
}
