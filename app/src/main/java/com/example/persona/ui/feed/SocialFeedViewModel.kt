package com.example.persona.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialFeedViewModel @Inject constructor(
    private val repository: PersonaRepository // ✅ 注入 Repository
) : ViewModel() {

    var feedList by mutableStateOf<List<Persona>>(emptyList())
    var isLoading by mutableStateOf(false)

    init {
        // 1. 订阅数据库 (离线数据秒开)
        viewModelScope.launch {
            repository.getFeedStream().collect { list ->
                feedList = list
            }
        }

        // 2. 触发网络刷新
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            isLoading = true
            // 只需调用刷新，不需要处理返回值
            // 数据更新会通过上面的 collect 自动送达
            repository.refreshFeed()
            isLoading = false
        }
    }
}