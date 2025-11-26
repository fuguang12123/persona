package com.example.persona.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.ChangePasswordRequest // ✅ Import
import com.example.persona.data.remote.AuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    var oldPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")

    var isLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    var successMsg by mutableStateOf<String?>(null)
    var isLoggedOut by mutableStateOf(false)

    fun changePassword() {
        if (oldPassword.isBlank() || newPassword.isBlank()) {
            errorMsg = "密码不能为空"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            successMsg = null
            try {
                // ✅ 修正 Response 调用
                val request = ChangePasswordRequest(oldPassword, newPassword)
                val response = authService.changePassword(request)

                if (response.isSuccessful && response.body()?.code == 200) {
                    successMsg = "密码修改成功"
                    oldPassword = ""
                    newPassword = ""
                } else {
                    errorMsg = response.body()?.message ?: "修改失败"
                }
            } catch (e: Exception) {
                errorMsg = "网络错误: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPrefs.clearAuth()
            isLoggedOut = true
        }
    }
}