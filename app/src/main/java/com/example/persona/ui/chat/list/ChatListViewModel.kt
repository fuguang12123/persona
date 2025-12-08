package com.example.persona.ui.chat.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.ConversationView
import com.example.persona.data.model.Persona
import com.example.persona.data.repository.ChatRepository
import com.example.persona.data.repository.PersonaRepository
import com.example.persona.data.repository.PostRepository
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
    private val postRepository: PostRepository,
    private val personaRepository: PersonaRepository, // [New]
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount = _unreadCount.asStateFlow()

    // [New] 关注列表数据
    private val _followedPersonas = MutableStateFlow<List<Persona>>(emptyList())
    val followedPersonas = _followedPersonas.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationView>> = userPrefs.userId
        .flatMapLatest { userIdStr ->
            val userId = userIdStr?.toLongOrNull() ?: 0L
            if (userId != 0L) {
                viewModelScope.launch {
                    chatRepository.syncConversationList()
                    refreshUnreadCount()
                }
                chatDao.getConversations(userId)
            } else {
                flowOf(emptyList())
            }
        }
        .map { list -> list.distinctBy { it.personaId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
//刷新未读通知
    fun refreshUnreadCount() {
        viewModelScope.launch { _unreadCount.value = postRepository.getUnreadCount() }
    }

    // [New] 加载关注列表
    fun loadFollowedPersonas() {
        viewModelScope.launch {
            _followedPersonas.value = personaRepository.getFollowedPersonas()
        }
    }
}