package com.example.persona.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.UpdateProfileRequest
import com.example.persona.data.remote.AuthService
import com.example.persona.utils.UriUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import javax.inject.Inject

data class EditProfileUiState(
    val nickname: String = "",
    val avatarUrl: String = "",
    val backgroundImageUrl: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authService: AuthService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        viewModelScope.launch {
            try {
                val res = authService.getMyProfile()
                if (res.isSuccessful && res.body()?.code == 200) {
                    val user = res.body()?.data
                    _uiState.update {
                        it.copy(
                            nickname = user?.nickname ?: user?.username ?: "",
                            avatarUrl = user?.avatarUrl ?: "",
                            backgroundImageUrl = user?.backgroundImageUrl ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onNicknameChange(v: String) {
        _uiState.update { it.copy(nickname = v) }
    }

    fun uploadImage(uri: Uri, isAvatar: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val file = UriUtils.uriToFile(context, uri)
                if (file != null) {
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    val res = authService.uploadImage(body)
                    if (res.isSuccessful && res.body()?.code == 200) {
                        val url = res.body()?.data ?: ""
                        _uiState.update {
                            if (isAvatar) it.copy(avatarUrl = url) else it.copy(backgroundImageUrl = url)
                        }
                    } else {
                        _uiState.update { it.copy(error = "上传失败: ${res.message()}") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "上传出错: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val req = UpdateProfileRequest(
                    nickname = _uiState.value.nickname,
                    avatarUrl = _uiState.value.avatarUrl,
                    backgroundImageUrl = _uiState.value.backgroundImageUrl
                )
                val res = authService.updateProfile(req)
                if (res.isSuccessful && res.body()?.code == 200) {
                    _uiState.update { it.copy(isSaved = true) }
                } else {
                    _uiState.update { it.copy(error = res.body()?.message ?: "保存失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "网络错误: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}