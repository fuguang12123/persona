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
import kotlin.code



/**
 * 编辑资料UI状态数据类
 *
 * @param nickname 昵称
 * @param avatarUrl 头像URL
 * @param backgroundImageUrl 背景图URL
 * @param isLoading 是否正在上传或保存
 * @param isSaved 是否已保存成功
 * @param hasChanges 是否有未保存的修改
 * @param error 错误信息
 */
data class EditProfileUiState(
    val nickname: String = "",
    val avatarUrl: String = "",
    val backgroundImageUrl: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val hasChanges: Boolean = false,
    val error: String? = null
)

/**
 * 编辑资料视图模型
 * 负责加载用户资料、上传图片、保存修改并跟踪修改状态
 *
 * @param authService 认证服务,用于调用用户资料相关API
 * @param context 应用上下文,用于文件操作
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authService: AuthService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** UI状态流(内部可变) */
    private val _uiState = MutableStateFlow(EditProfileUiState())
    /** UI状态流(对外只读) */
    val uiState = _uiState.asStateFlow()

    /** 初始昵称,用于对比是否修改 */
    private var initialNickname = ""
    /** 初始头像URL,用于对比是否修改 */
    private var initialAvatarUrl = ""
    /** 初始背景图URL,用于对比是否修改 */
    private var initialBackgroundImageUrl = ""
    /** 数据是否已加载,避免加载前误判修改状态 */
    private var isDataLoaded = false

    init {
        // 初始化时加载当前用户资料
        loadCurrentProfile()
    }

    /**
     * 加载当前用户资料
     * 获取用户信息并保存初始值,用于后续对比修改
     */
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

                    // 更新UI状态
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

    /**
     * 检查当前状态是否与初始状态不同
     * 用于判断是否有未保存的修改
     *
     * @param currentState 当前UI状态
     * @return true表示有修改,false表示无修改
     */
    private fun checkChanges(currentState: EditProfileUiState): Boolean {
        if (!isDataLoaded) return false
        return currentState.nickname != initialNickname ||
                currentState.avatarUrl != initialAvatarUrl ||
                currentState.backgroundImageUrl != initialBackgroundImageUrl
    }

    /**
     * 处理昵称输入变化
     * 更新昵称并检查修改状态
     *
     * @param v 新的昵称值
     */
    fun onNicknameChange(v: String) {
        _uiState.update {
            val newState = it.copy(nickname = v)
            newState.copy(hasChanges = checkChanges(newState))
        }
    }

    /**
     * 上传图片
     * 将选择的图片上传到服务器,并更新对应的URL
     *
     * @param uri 图片的本地URI
     * @param isAvatar true表示头像,false表示背景图
     */
    fun uploadImage(uri: Uri, isAvatar: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 将URI转换为文件
                val file = UriUtils.uriToFile(context, uri)
                if (file != null) {
                    // 构造多部分请求体
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    // 调用上传API
                    val res = authService.uploadImage(body)
                    if (res.isSuccessful && res.body()?.code == 200) {
                        val url = res.body()?.data ?: ""
                        // 更新对应的URL并检查修改状态
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

    /**
     * 保存用户资料
     * 将当前的昵称、头像和背景图提交到服务器
     * 保存成功后更新初始值,使修改状态重置为false
     */
    fun saveProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 构造更新请求
                val req = UpdateProfileRequest(
                    nickname = _uiState.value.nickname,
                    avatarUrl = _uiState.value.avatarUrl,
                    backgroundImageUrl = _uiState.value.backgroundImageUrl
                )
                val res = authService.updateProfile(req)
                if (res.isSuccessful && res.body()?.code == 200) {
                    // 保存成功后,更新初始值,使修改状态重置
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
