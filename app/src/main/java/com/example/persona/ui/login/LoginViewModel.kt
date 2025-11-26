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

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    var username by mutableStateOf("")
    var password by mutableStateOf("")

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    var loginSuccess by mutableStateOf(false)

    fun onLoginClick() {
        if (username.isBlank() || password.isBlank()) {
            uiState = LoginUiState.Error("用户名或密码不能为空")
            return
        }

        viewModelScope.launch {
            uiState = LoginUiState.Loading
            try {
                val request = LoginRequest(username, password)
                val response = authService.login(request)
                val body = response.body() // ✅ 获取 Body

                if (response.isSuccessful && body != null && body.code == 200 && body.data != null) {
                    val map = body.data
                    val token = map["token"].toString()
                    val userId = map["userId"].toString()

                    userPrefs.saveAuthData(token, userId)

                    uiState = LoginUiState.Success
                    loginSuccess = true
                } else {
                    val msg = body?.message ?: "登录失败 (${response.code()})"
                    uiState = LoginUiState.Error(msg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiState = LoginUiState.Error(e.message ?: "网络连接错误")
            }
        }
    }

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