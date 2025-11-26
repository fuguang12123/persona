package com.example.persona.ui.chat.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.ConversationView
import com.example.persona.data.repository.ChatRepository
import com.example.persona.data.repository.PostRepository // [New] 引入 PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val chatRepository: ChatRepository,
    private val postRepository: PostRepository, // [New] 注入以获取未读数
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    // [New] 未读通知数量 State
    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount: StateFlow<Long> = _unreadCount.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationView>> = userPrefs.userId
        .flatMapLatest { userIdStr ->
            val userId = userIdStr?.toLongOrNull() ?: 0L
            if (userId != 0L) {
                viewModelScope.launch {
                    chatRepository.syncConversationList()
                    // [New] 每次同步会话时，顺便同步一下未读红点
                    refreshUnreadCount()
                }
                chatDao.getConversations(userId)
            } else {
                flowOf(emptyList())
            }
        }
        .map { list -> list.distinctBy { it.personaId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // [New] 刷新未读数量的方法，供 UI 调用
    fun refreshUnreadCount() {
        viewModelScope.launch {
            val count = postRepository.getUnreadCount()
            _unreadCount.value = count
        }
    }
}