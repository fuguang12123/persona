package com.example.persona.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Info
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
    onPersonaDetailClick: (Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val listState = rememberLazyListState()

    LaunchedEffect(personaId) {
        viewModel.initChat(personaId)
    }

    // [New] 优化：自动回滚到最新消息
    // 监听消息数量变化 (发送或接收新消息时 size 会增加)
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            // 因为启用了 reverseLayout = true，列表底部是 Index 0
            // 所以这里滚动到 0 即可实现“回滚到最下方”
            listState.animateScrollToItem(0)
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
                },
                actions = {
                    IconButton(onClick = { onPersonaDetailClick(personaId) }) {
                        Icon(Icons.Default.Info, contentDescription = "Detail")
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
            // [Fix] 保持倒序布局，这对聊天应用至关重要
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(
                items = viewModel.messages,
                key = { it.id }
            ) { msg ->
                ChatBubble(
                    msg = msg,
                    personaAvatarUrl = viewModel.personaAvatarUrl,
                    personaName = viewModel.personaName,
                    onAvatarClick = { onPersonaDetailClick(personaId) }
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    msg: ChatMessage,
    personaAvatarUrl: String,
    personaName: String?,
    onAvatarClick: () -> Unit
) {
    val isUser = msg.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // 🤖 左侧：AI 头像
        if (!isUser) {
            Box(modifier = Modifier.clickable { onAvatarClick() }) {
                ChatAvatar(
                    url = personaAvatarUrl,
                    name = personaName ?: "AI"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Text(
                text = msg.content ?: "",
                modifier = Modifier.padding(12.dp),
                color = if (isUser) Color.White else Color.Black,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // 👤 右侧：用户头像
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            ChatAvatar(
                url = "",
                name = "User"
            )
        }
    }
}

@Composable
fun ChatAvatar(url: String, name: String) {
    val finalUrl = remember(url, name) {
        if (url.isBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=$name"
        } else {
            url.replace("/svg", "/png")
        }
    }

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
            shape = RoundedCornerShape(24.dp)
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