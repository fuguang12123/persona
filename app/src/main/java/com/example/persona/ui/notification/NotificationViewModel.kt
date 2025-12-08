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

/**
 * 通知视图模型
 * 负责管理通知列表的加载和状态
 *
 * @param repository 帖子仓库,用于获取通知数据和标记已读
 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: PostRepository
) : ViewModel() {

    /** 通知列表状态流(内部可变) */
    private val _notifications = MutableStateFlow<List<NotificationDto>>(emptyList())

    /** 通知列表状态流(对外只读) */
    val notifications: StateFlow<List<NotificationDto>> = _notifications

    /** 加载状态标志(内部可变) */
    private val _isLoading = MutableStateFlow(false)

    /** 加载状态标志(对外只读) */
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        // 初始化时自动加载通知列表
        loadNotifications()
    }

    /**
     * 加载通知列表
     * 获取通知后自动标记为已读,实现"已读优先"的体验:
     * 1. 首先获取通知列表(可能包含未读消息)
     * 2. 更新UI显示
     * 3. 在后台将所有通知标记为已读
     * 这样用户看到的是完整列表,但下次进入时不会再显示为未读
     */
    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true

            // 1. 获取通知列表 (此时可能包含未读)
            val result = repository.getNotifications()
            if (result.isSuccess) {
                _notifications.value = result.getOrDefault(emptyList())
            }

            _isLoading.value = false

            // 2. 获取完列表后,默默将它们标记为已读
            // 这样用户在当前页面看到的是刚才的状态,但下次进来就是已读了
            repository.markNotificationsAsRead()
        }
    }
}
