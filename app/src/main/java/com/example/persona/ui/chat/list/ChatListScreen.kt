package com.example.persona.ui.chat.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.persona.data.local.dao.ConversationView
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = hiltViewModel(),
    onChatClick: (Long) -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "消息 (${conversations.size})",
                        fontWeight = FontWeight.Bold
                    )
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

@Composable
fun ConversationItem(
    item: ConversationView,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        AsyncImage(
            model = item.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.LightGray)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 内容区
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
                // 使用新的解析函数处理 String 类型的时间
                Text(
                    text = parseAndFormatTime(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 解析数据库中的时间字符串并格式化
 * 输入示例: "2025-11-19 12:46:49"
 */
fun parseAndFormatTime(timeStr: String?): String {
    if (timeStr.isNullOrEmpty()) return ""

    try {
        // 1. 解析 SQL 日期格式
        // 注意：这里假设数据库存的是本地时间，如果是 UTC 需要设置 timeZone
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = parser.parse(timeStr) ?: return ""
        val timestamp = date.time

        // 2. 使用之前的逻辑格式化为 "HH:mm" 或 "昨天"
        return formatTimeFromMillis(timestamp)
    } catch (e: Exception) {
        e.printStackTrace()
        return timeStr // 解析失败则直接显示原字符串
    }
}

// 毫秒级时间戳格式化逻辑 (复用之前的)
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
        // 当天：显示 HH:mm
        diff < 24 * 60 * 60 * 1000 && dayOfMonth == nowDay -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
        // 昨天
        diff < 48 * 60 * 60 * 1000 -> {
            "昨天"
        }
        // 今年其他时间
        year == nowYear -> {
            SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        }
        // 往年
        else -> {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }
    }
}