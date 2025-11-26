package com.example.persona.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.model.Persona
import com.example.persona.data.model.UserDto // ✅ 导入
import com.example.persona.data.remote.AuthService
import com.example.persona.data.remote.PostDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: UserDto? = null,

    val myPersonas: List<Persona> = emptyList(),
    val myPosts: List<PostDto> = emptyList(),
    val myLikes: List<PostDto> = emptyList(),
    val myBookmarks: List<PostDto> = emptyList(),

    val activeTab: Int = 0
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authService: AuthService,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // ✅ 修正 Response 调用
                val response = authService.getMyProfile()
                val body = response.body()
                if (response.isSuccessful && body != null && body.code == 200) {
                    _uiState.update { it.copy(user = body.data) }
                }

                loadTabContent(0)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun switchTab(index: Int) {
        _uiState.update { it.copy(activeTab = index) }
        loadTabContent(index)
    }

    private fun loadTabContent(index: Int) {
        viewModelScope.launch {
            try {
                when (index) {
                    0 -> { // My Posts
                        val res = authService.getMyPosts()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myPosts = res.body()?.data ?: emptyList()) }
                        }
                    }
                    1 -> { // Likes
                        val res = authService.getMyLikes()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myLikes = res.body()?.data ?: emptyList()) }
                        }
                    }
                    2 -> { // Bookmarks
                        val res = authService.getMyBookmarks()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myBookmarks = res.body()?.data ?: emptyList()) }
                        }
                    }
                    3 -> { // Personas
                        val res = authService.getMyPersonas()
                        if (res.isSuccessful && res.body()?.code == 200) {
                            _uiState.update { it.copy(myPersonas = res.body()?.data ?: emptyList()) }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}