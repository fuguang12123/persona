package com.example.persona.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
    val playingUrl by viewModel.audioPlayer.currentPlayingUrl.collectAsState()

    // 控制记忆弹窗显示
    var showMemoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(personaId) { viewModel.initChat(personaId) }

    // 当有新消息时自动滚动到底部
    LaunchedEffect(viewModel.messages.size, viewModel.isSending) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 记忆弹窗
    if (showMemoryDialog) {
        val memoryList by viewModel.memories.collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Face, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("共生记忆库")
                }
            },
            text = {
                if (memoryList.isEmpty()) {
                    Text("暂时还没有提取到关于你的记忆...", fontStyle = FontStyle.Italic, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(memoryList) { memory ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(memory.content, style = MaterialTheme.typography.bodyMedium)
                                }
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = Color.LightGray.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMemoryDialog = false }) { Text("关闭") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        viewModel.personaName?.let { Text(it) }
                        // 状态指示：端侧模式为绿色
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val color = if (viewModel.isPrivateMode) Color(0xFF4CAF50) else Color.Gray
                            Icon(
                                if (viewModel.isPrivateMode) Icons.Default.Lock else Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = color
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (viewModel.isPrivateMode) "端侧私密模式" else "云端模式",
                                style = MaterialTheme.typography.labelSmall,
                                color = color
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    // 记忆查看按钮 (仅私密模式显示)
                    if (viewModel.isPrivateMode) {
                        IconButton(onClick = { showMemoryDialog = true }) {
                            Icon(Icons.Default.Face, "Memory", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Switch(
                        checked = viewModel.isPrivateMode,
                        onCheckedChange = { viewModel.togglePrivateMode() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50))
                    )
                    IconButton(onClick = { onPersonaDetailClick(personaId) }) { Icon(Icons.Default.Info, "Detail") }
                }
            )
        },
        bottomBar = {
            ChatInputArea(
                onSendText = { text -> viewModel.sendMessage(text) },
                onSendImageGen = { text -> viewModel.sendImageGenRequest(text) },
                onStartRecording = { viewModel.startRecording() },
                onStopRecording = { viewModel.stopRecording() },
                onCancelRecording = { viewModel.cancelRecording() },
                isRecording = viewModel.isRecording,
                isSending = viewModel.isSending,
                isPrivateMode = viewModel.isPrivateMode
            )
        }
    ) { padding ->

        // [Fix] 核心修复：私密模式的排序逻辑修正
        val displayMessages = if (viewModel.isPrivateMode) {
            // 私密模式使用负数时间戳ID (例如: -1732xxxx)。
            // 数值越小(-2000)代表时间越新，数值越大(-1000)代表时间越旧。
            // LazyColumn(reverseLayout=true) 需要 Index 0 为最新消息。
            // 因此我们需要把“最小”的数排在最前面，即使用升序 sortedBy。
            viewModel.messages.sortedBy { it.id }
        } else {
            // 云端模式直接使用 ViewModel 中的顺序 (Repo层已按时间降序排列)
            viewModel.messages
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // [Fix] 恢复云端模式下的加载动画
            // 仅在非私密模式（云端）且正在发送、且最新消息是用户发送（AI还没回复）时显示
            val showCloudLoading = viewModel.isSending &&
                    !viewModel.isPrivateMode &&
                    (displayMessages.isEmpty() || displayMessages.firstOrNull()?.role == "user")

            if (showCloudLoading) {
                item {
                    ChatBubble(
                        msg = ChatMessage(role = "assistant", status = 1, content = ""),
                        personaAvatarUrl = viewModel.personaAvatarUrl,
                        personaName = viewModel.personaName,
                        onAvatarClick = { },
                        isPlaying = false,
                        onPlayAudio = { }
                    )
                }
            }

            items(items = displayMessages, key = { it.id }) { msg ->
                ChatBubble(
                    msg = msg,
                    personaAvatarUrl = viewModel.personaAvatarUrl,
                    personaName = viewModel.personaName,
                    onAvatarClick = { onPersonaDetailClick(personaId) },
                    isPlaying = playingUrl == (msg.localFilePath ?: msg.mediaUrl),
                    onPlayAudio = { path -> viewModel.playAudio(path) }
                )
            }
        }
    }
}

@Composable
fun ChatInputArea(
    onSendText: (String) -> Unit,
    onSendImageGen: (String) -> Unit,
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    isRecording: Boolean,
    isSending: Boolean,
    isPrivateMode: Boolean
) {
    var text by remember { mutableStateOf("") }
    var isVoiceMode by remember { mutableStateOf(false) }
    var isImageMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        // 私密模式下隐藏生图入口
        if (!isVoiceMode && !isPrivateMode) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isImageMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    border = if (!isImageMode) androidx.compose.foundation.BorderStroke(1.dp, Color.Gray) else null,
                    modifier = Modifier.height(32.dp).clickable { isImageMode = !isImageMode }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, "Image Gen", Modifier.size(16.dp), tint = if (isImageMode) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isImageMode) "生图模式已开启" else "AI绘图", style = MaterialTheme.typography.labelMedium, color = if (isImageMode) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 私密模式下隐藏语音切换，用 Spacer 占位
            if (!isPrivateMode) {
                IconButton(onClick = { isVoiceMode = !isVoiceMode }) { Icon(if (isVoiceMode) Icons.Default.Keyboard else Icons.Default.KeyboardVoice, "Switch") }
            } else {
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.width(4.dp))

            if (isVoiceMode && !isPrivateMode) {
                Box(Modifier.weight(1f)) {
                    VoiceInputButton(onStartRecording, onStopRecording, onCancelRecording, isRecording)
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (isPrivateMode) "🔒 私密对话中 (记忆共生)" else if (isImageMode) "描述你想要生成的画面..." else "Type a message...")
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    if (text.isNotBlank()) {
                        if (isImageMode && !isPrivateMode) {
                            onSendImageGen(text); isImageMode = false
                        } else {
                            onSendText(text)
                        };
                        text = ""
                    }
                }, enabled = !isSending) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}

// 底部辅助组件
@Composable
fun ChatBubble(
    msg: ChatMessage,
    personaAvatarUrl: String,
    personaName: String?,
    onAvatarClick: () -> Unit,
    isPlaying: Boolean,
    onPlayAudio: (String) -> Unit
) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(modifier = Modifier.clickable { onAvatarClick() }) {
                ChatAvatar(url = personaAvatarUrl, name = personaName ?: "AI")
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
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            ChatMessageContent(
                msg = msg,
                isUser = isUser,
                isPlaying = isPlaying,
                onPlayAudio = onPlayAudio
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            ChatAvatar(url = "", name = "User")
        }
    }
}

@Composable
fun ChatAvatar(url: String, name: String) {
    val finalUrl = remember(url, name) {
        if (url.isBlank()) "https://api.dicebear.com/7.x/avataaars/png?seed=$name" else url.replace("/svg", "/png")
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
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Default.Person),
            error = rememberVectorPainter(Icons.Default.Person)
        )
    }
}