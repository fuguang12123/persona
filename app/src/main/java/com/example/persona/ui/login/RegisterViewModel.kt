package com.example.persona.ui.login

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.RegisterRequest
import com.example.persona.data.remote.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 注册视图模型
 * 处理用户注册逻辑,包括验证码获取和验证
 *
 * @param authService 认证服务,用于调用注册和验证码API
 * @param userPrefs 用户偏好设置仓库,用于保存认证信息
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    /** 用户名输入 */
    var username by mutableStateOf("")

    /** 密码输入 */
    var password by mutableStateOf("")

    /** 确认密码输入 */
    var confirmPassword by mutableStateOf("")

    /** 验证码输入 */
    var captchaCodeInput by mutableStateOf("")

    /** 验证码UUID,用于服务端验证 */
    var captchaUuid by mutableStateOf("")

    /** 验证码图片Bitmap */
    var captchaBitmap by mutableStateOf<Bitmap?>(null)

    /** UI状态 */
    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)

    /** 注册成功标志 */
    var registerSuccess by mutableStateOf(false)

    init {
        // 初始化时加载验证码
        refreshCaptcha()
    }

    /**
     * 刷新验证码
     * 从服务器获取新的验证码图片
     */
    fun refreshCaptcha() {
        viewModelScope.launch {
            try {
                val response = authService.getCaptcha()
                val body = response.body()

                if (response.isSuccessful && body != null && body.code == 200 && body.data != null) {
                    val dto = body.data
                    captchaUuid = dto.uuid

                    // 解析Base64编码的图片
                    val base64Str = dto.image.substringAfter(",")
                    val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
                    captchaBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 处理注册按钮点击
     * 验证输入并调用注册API
     */
    fun onRegisterClick() {
        // 验证输入完整性
        if (username.isBlank() || password.isBlank()) {
            uiState = LoginUiState.Error("请填写完整")
            return
        }

        // 验证密码一致性
        if (password != confirmPassword) {
            uiState = LoginUiState.Error("两次密码不一致")
            return
        }

        // 验证验证码输入
        if (captchaCodeInput.isBlank()) {
            uiState = LoginUiState.Error("请输入验证码")
            return
        }

        viewModelScope.launch {
            uiState = LoginUiState.Loading
            try {
                // 构造注册请求
                val request = RegisterRequest(
                    username, password, confirmPassword,
                    captchaUuid, captchaCodeInput
                )
                val response = authService.register(request)
                val body = response.body()

                if (response.isSuccessful && body != null && body.code == 200 && body.data != null) {
                    val map = body.data
                    val token = map["token"].toString()
                    val userId = map["userId"].toString()

                    // 注册成功后自动登录
                    userPrefs.saveAuthData(token, userId)
                    uiState = LoginUiState.Success
                    registerSuccess = true
                } else {
                    // 注册失败,刷新验证码
                    val errorMsg = body?.message ?: response.message()
                    uiState = LoginUiState.Error(errorMsg ?: "注册失败")
                    refreshCaptcha()
                    captchaCodeInput = ""
                }
            } catch (e: Exception) {
                // 网络错误,刷新验证码
                uiState = LoginUiState.Error(e.message ?: "网络错误")
                refreshCaptcha()
            }
        }
    }
}
