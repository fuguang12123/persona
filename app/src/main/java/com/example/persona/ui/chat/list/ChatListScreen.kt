package com.example.persona.ui.chat.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.persona.data.local.dao.ConversationView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = hiltViewModel(),
    onChatClick: (Long) -> Unit,
    onNotificationClick: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()

    // [New] 每次进入该页面时，刷新未读数
    // 这样当你从通知页(已读)返回时，红点会立刻消失
    LaunchedEffect(Unit) {
        viewModel.refreshUnreadCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "消息 (${conversations.size})",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNotificationClick) {
                        // [New] 带红点的图标
                        if (unreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        // 如果数字太大显示 99+
                                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications"
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无聊天记录\n快去\"智能体\"页面找人聊天吧",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(conversations, key = { it.personaId }) { item ->
                    ConversationItem(item = item, onClick = { onChatClick(item.personaId) })
                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ... ConversationItem 保持不变 ...
@Composable
fun ConversationItem(
    item: ConversationView,
    onClick: () -> Unit
) {
    val finalAvatarUrl = remember(item.avatarUrl, item.name) {
        if (item.avatarUrl.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${item.name}"
        } else {
            item.avatarUrl.replace("/svg", "/png")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(finalAvatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.LightGray),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Default.Person),
            error = rememberVectorPainter(Icons.Default.Person)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = parseAndFormatTime(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.lastMessage ?: "暂无消息",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun parseAndFormatTime(timeStr: String?): String {
    if (timeStr.isNullOrEmpty()) return ""

    try {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = parser.parse(timeStr) ?: return ""
        val timestamp = date.time
        return formatTimeFromMillis(timestamp)
    } catch (e: Exception) {
        e.printStackTrace()
        return timeStr
    }
}

fun formatTimeFromMillis(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val date = Date(timestamp)
    val diff = now - timestamp

    val calendar = Calendar.getInstance()
    calendar.time = date
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val year = calendar.get(Calendar.YEAR)

    val nowCalendar = Calendar.getInstance()
    val nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH)
    val nowYear = nowCalendar.get(Calendar.YEAR)

    return when {
        diff < 24 * 60 * 60 * 1000 && dayOfMonth == nowDay -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
        diff < 48 * 60 * 60 * 1000 -> {
            "昨天"
        }
        year == nowYear -> {
            SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        }
        else -> {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }
    }
}