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
    val hasChanges: Boolean = false, // 新增：标记是否有未保存的修改
    val error: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authService: AuthService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState = _uiState.asStateFlow()

    // 用于存储初始状态，用来对比是否发生了修改
    private var initialNickname = ""
    private var initialAvatarUrl = ""
    private var initialBackgroundImageUrl = ""
    private var isDataLoaded = false

    init {
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        viewModelScope.launch {
            try {
                val res = authService.getMyProfile()
                if (res.isSuccessful && res.body()?.code == 200) {
                    val user = res.body()?.data

                    // 保存初始值
                    initialNickname = user?.nickname ?: user?.username ?: ""
                    initialAvatarUrl = user?.avatarUrl ?: ""
                    initialBackgroundImageUrl = user?.backgroundImageUrl ?: ""
                    isDataLoaded = true

                    _uiState.update {
                        it.copy(
                            nickname = initialNickname,
                            avatarUrl = initialAvatarUrl,
                            backgroundImageUrl = initialBackgroundImageUrl,
                            hasChanges = false
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 检查当前状态是否与初始状态不同
    private fun checkChanges(currentState: EditProfileUiState): Boolean {
        if (!isDataLoaded) return false
        return currentState.nickname != initialNickname ||
                currentState.avatarUrl != initialAvatarUrl ||
                currentState.backgroundImageUrl != initialBackgroundImageUrl
    }

    fun onNicknameChange(v: String) {
        _uiState.update {
            val newState = it.copy(nickname = v)
            newState.copy(hasChanges = checkChanges(newState))
        }
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
                            val newState = if (isAvatar) it.copy(avatarUrl = url) else it.copy(backgroundImageUrl = url)
                            newState.copy(hasChanges = checkChanges(newState))
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
                    // 保存成功后，更新初始值，这样 hasChanges 就会变为 false
                    initialNickname = _uiState.value.nickname
                    initialAvatarUrl = _uiState.value.avatarUrl
                    initialBackgroundImageUrl = _uiState.value.backgroundImageUrl

                    _uiState.update { it.copy(isSaved = true, hasChanges = false) }
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