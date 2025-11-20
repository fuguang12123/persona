package com.example.persona.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.persona.data.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    personaId: Long,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val listState = rememberLazyListState()

    LaunchedEffect(personaId) {
        viewModel.initChat(personaId)
    }

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { viewModel.personaName?.let { Text(it) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                onSend = { text -> viewModel.sendMessage(text) },
                enabled = !viewModel.isSending
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp), // 增加气泡间距
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(viewModel.messages) { msg ->
                ChatBubble(
                    msg = msg,
                    personaAvatarUrl = viewModel.personaAvatarUrl
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, personaAvatarUrl: String) {
    val isUser = msg.role == "user"

    // 使用 Row 实现左右布局
    Row(
        modifier = Modifier.fillMaxWidth(),
        // 如果是用户，内容靠右；如果是 AI，内容靠左
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top // 头像和气泡顶部对齐
    ) {
        // 🤖 左侧：AI 头像 (只在非用户时显示)
        if (!isUser) {
            ChatAvatar(url = personaAvatarUrl)
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 💬 中间：气泡
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                // 根据发送者，调整下角的圆角，做出"小尾巴"的感觉
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 260.dp) // 限制气泡最大宽度
        ) {
            Text(
                text = msg.content ?: "",
                modifier = Modifier.padding(12.dp),
                color = if (isUser) Color.White else Color.Black,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // 👤 右侧：用户头像 (只在用户时显示)
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户的头像暂时写死或者用 DiceBear 生成一个固定的
            ChatAvatar(url = "https://api.dicebear.com/7.x/avataaars/png?seed=User123")
        }
    }
}

@Composable
fun ChatAvatar(url: String) {
    // 处理 SVG 转 PNG (以防万一)
    val finalUrl = url.replace("/svg", "/png")

    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(finalUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Avatar",
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Default.Person),
            error = rememberVectorPainter(Icons.Default.Person)
        )
    }
}

// ChatInput 保持不变
@Composable
fun ChatInput(onSend: (String) -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...") },
            maxLines = 3,
            shape = RoundedCornerShape(24.dp) // 稍微圆润一点的输入框
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            },
            enabled = enabled
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}