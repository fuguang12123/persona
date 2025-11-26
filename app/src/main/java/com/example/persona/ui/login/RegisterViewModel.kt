package com.example.persona.ui.login

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64 // ✅ 明确导入 Android Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.RegisterRequest // ✅ 导入正确的 Request
import com.example.persona.data.remote.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var captchaCodeInput by mutableStateOf("")
    var captchaUuid by mutableStateOf("")
    var captchaBitmap by mutableStateOf<Bitmap?>(null)

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
    var registerSuccess by mutableStateOf(false)

    init {
        refreshCaptcha()
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            try {
                // ✅ 修正 Response 处理逻辑
                val response = authService.getCaptcha()
                val body = response.body()

                if (response.isSuccessful && body != null && body.code == 200 && body.data != null) {
                    val dto = body.data
                    captchaUuid = dto.uuid

                    val base64Str = dto.image.substringAfter(",")
                    // ✅ 使用 Android Base64
                    val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
                    captchaBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onRegisterClick() {
        if (username.isBlank() || password.isBlank()) {
            uiState = LoginUiState.Error("请填写完整")
            return
        }
        if (password != confirmPassword) {
            uiState = LoginUiState.Error("两次密码不一致")
            return
        }
        if (captchaCodeInput.isBlank()) {
            uiState = LoginUiState.Error("请输入验证码")
            return
        }

        viewModelScope.launch {
            uiState = LoginUiState.Loading
            try {
                val request = RegisterRequest(
                    username, password, confirmPassword,
                    captchaUuid, captchaCodeInput
                )
                val response = authService.register(request)
                val body = response.body() // ✅ 获取 Body

                if (response.isSuccessful && body != null && body.code == 200 && body.data != null) {
                    val map = body.data
                    val token = map["token"].toString()
                    val userId = map["userId"].toString()

                    userPrefs.saveAuthData(token, userId)
                    uiState = LoginUiState.Success
                    registerSuccess = true
                } else {
                    val errorMsg = body?.message ?: response.message()
                    uiState = LoginUiState.Error(errorMsg ?: "注册失败")
                    refreshCaptcha()
                    captchaCodeInput = ""
                }
            } catch (e: Exception) {
                uiState = LoginUiState.Error(e.message ?: "网络错误")
                refreshCaptcha()
            }
        }
    }
}