package com.example.persona.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.remote.NotificationDto
import com.example.persona.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<NotificationDto>>(emptyList())
    val notifications: StateFlow<List<NotificationDto>> = _notifications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true

            // 1. 获取通知列表 (此时可能包含未读)
            val result = repository.getNotifications()
            if (result.isSuccess) {
                _notifications.value = result.getOrDefault(emptyList())
            }

            _isLoading.value = false

            // 2. [New] 获取完列表后，默默将它们标记为已读
            // 这样用户在当前页面看到的是刚才的状态，但下次进来就是已读了
            repository.markNotificationsAsRead()
        }
    }
}