package com.example.persona.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.persona.data.remote.NotificationDto
import com.example.persona.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 通知列表界面
 * 显示用户收到的所有通知(点赞、评论、回复等)
 *
 * @param viewModel 通知视图模型,使用Hilt注入
 * @param onBackClick 返回按钮点击回调
 * @param onPostClick 点击通知跳转到对应帖子的回调,传入帖子ID
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onPostClick: (Long) -> Unit
) {
    // 收集通知列表状态
    val notifications by viewModel.notifications.collectAsState()
    // 收集加载状态
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息通知") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        // 加载中且列表为空时显示加载指示器
        if (isLoading && notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        // 加载完成但列表为空时显示空状态提示
        else if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无新消息",
                        color = Color.Gray
                    )
                }
            }
        }
        // 有通知时显示列表
        else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(notifications) { item ->
                    // 渲染每条通知
                    NotificationItem(
                        notification = item,
                        onClick = { onPostClick(item.postId) }
                    )
                    // 分隔线
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color.LightGray.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * 通知列表项组件
 * 显示单条通知的详细信息
 *
 * @param notification 通知数据对象
 * @param onClick 点击通知的回调
 */
@Composable
fun NotificationItem(
    notification: NotificationDto,
    onClick: () -> Unit
) {
    // 根据通知类型决定显示的图标、颜色和文字
    // type 1: 点赞 | type 2: 评论 | type 3: 回复
    val (icon, iconColor, actionText) = when (notification.type) {
        1 -> Triple(Icons.Default.Favorite, Color(0xFFFF5252), "赞了你的动态")
        2 -> Triple(Icons.AutoMirrored.Filled.Message, Color(0xFF2196F3), "评论了你的动态")
        3 -> Triple(Icons.AutoMirrored.Filled.Reply, Color(0xFF4CAF50), "回复了你的评论")
        else -> Triple(Icons.Default.Notifications, Color.Gray, "有一条新通知")
    }

    // 将时间戳转换为相对时间显示(如"5分钟前")
    val timeAgoStr = remember(notification.createdAt) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            val dateStr = sdf.format(Date(notification.createdAt))
            DateUtils.formatTimeAgo(dateStr)
        } catch (e: Exception) {
            "刚刚"
        }
    }

    // 统一头像逻辑:如果没有头像则使用 DiceBear 生成
    val avatarUrl = remember(notification.senderAvatar, notification.senderName) {
        if (notification.senderAvatar.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${notification.senderName}"
        } else {
            notification.senderAvatar.replace("/svg", "/png")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 发送者头像
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.LightGray),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 通知内容区域
        Column(modifier = Modifier.weight(1f)) {
            // 发送者名称
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.senderName ?: "未知用户",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 动作类型提示(带图标)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 时间显示
            Text(
                text = timeAgoStr,
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray
            )
        }
    }
}
