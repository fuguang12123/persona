package com.example.persona.ui.chat.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.ConversationView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val userPrefs: UserPreferencesRepository // [New] 注入 UserPrefs
) : ViewModel() {

    // 使用 flatMapLatest 监听 userId 的变化
    // 一旦 userId 变化（例如登录），就会重新执行 getConversations 查询
    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationView>> = userPrefs.userId
        .flatMapLatest { userIdStr ->
            val userId = userIdStr?.toLongOrNull() ?: 0L // 默认值处理
            if (userId != 0L) {
                chatDao.getConversations(userId) // [Fix] 传入 userId
            } else {
                flowOf(emptyList()) // 未登录或无效 ID 返回空列表
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}