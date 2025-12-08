package com.example.persona.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.ChangePasswordRequest
import com.example.persona.data.remote.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置视图模型
 * 处理修改密码和退出登录逻辑
 *
 * @param authService 认证服务,用于调用修改密码API
 * @param userPrefs 用户偏好设置仓库,用于清除认证信息
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    /** 原密码输入 */
    var oldPassword by mutableStateOf("")
    /** 新密码输入 */
    var newPassword by mutableStateOf("")

    /** 是否正在加载 */
    var isLoading by mutableStateOf(false)
    /** 错误信息 */
    var errorMsg by mutableStateOf<String?>(null)
    /** 成功信息 */
    var successMsg by mutableStateOf<String?>(null)
    /** 是否已退出登录 */
    var isLoggedOut by mutableStateOf(false)

    /**
     * 修改密码
     * 验证输入后调用API修改密码
     */
    fun changePassword() {
        // 验证输入是否为空
        if (oldPassword.isBlank() || newPassword.isBlank()) {
            errorMsg = "密码不能为空"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            successMsg = null
            try {
                // 构造修改密码请求
                val request = ChangePasswordRequest(oldPassword, newPassword)
                val response = authService.changePassword(request)

                if (response.isSuccessful && response.body()?.code == 200) {
                    // 修改成功,清空输入并显示成功消息
                    successMsg = "密码修改成功"
                    oldPassword = ""
                    newPassword = ""
                } else {
                    // 修改失败,显示错误消息
                    errorMsg = response.body()?.message ?: "修改失败"
                }
            } catch (e: Exception) {
                // 网络错误
                errorMsg = "网络错误: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 退出登录
     * 清除本地认证信息并触发导航
     */
    fun logout() {
        viewModelScope.launch {
            userPrefs.clearAuth()
            isLoggedOut = true
        }
    }
}
