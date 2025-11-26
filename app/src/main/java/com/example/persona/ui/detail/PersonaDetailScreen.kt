package com.example.persona.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonaDetailScreen(
    personaId: Long,
    onBackClick: () -> Unit,
    onChatClick: (Long) -> Unit,       // 回调：去聊天
    onCreatePostClick: (Long) -> Unit, // 回调：去发帖 (带 ID)
    onEditClick: (Long) -> Unit,       // 回调：去编辑 (仅号主可用)
    viewModel: PersonaDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(personaId) {
        viewModel.loadPersona(personaId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val persona = uiState.persona
    val context = LocalContext.current

    val avatarUrl = remember(persona?.avatarUrl) {
        val url = persona?.avatarUrl
        if (url.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${persona?.name ?: "unknown"}"
        } else {
            url.replace("/svg", "/png")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (uiState.isOwner && !uiState.isLoading) {
                        IconButton(
                            onClick = { onEditClick(personaId) },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { _ ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error!!, color = Color.Red)
            }
        } else if (persona != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                // --- A. 背景大图层 ---
                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )

                // 渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // --- B. 滚动内容层 ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 留白，让出头部大图区域
                    Spacer(modifier = Modifier.height(220.dp))

                    // [Fix] 使用 Box 包裹卡片和头像，避免 Surface 裁切头像
                    Box(modifier = Modifier.fillMaxWidth()) {

                        // 1. 白色圆角卡片 (作为背景)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 50.dp), // 给头像的上半部分留出空间
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 60.dp, bottom = 24.dp), // top=60dp: 50dp头像下半部 + 10dp间距
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 2. 信息展示 (名字 & 创建人)
                                Text(
                                    text = persona.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Created by ${uiState.creatorName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 3. 标签流
                                if (uiState.tags.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        uiState.tags.forEach { tag ->
                                            SuggestionChip(
                                                onClick = { },
                                                label = { Text(tag) },
                                                enabled = false,
                                                colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                                                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                border = null
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                // --- 4. 按钮区域 ---
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        onClick = { onChatClick(personaId) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("开始聊天")
                                    }

                                    FilledTonalButton(
                                        onClick = { onCreatePostClick(personaId) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("发布动态")
                                    }
                                }

                                // 5. 编辑按钮 (仅号主)
                                if (uiState.isOwner) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { onEditClick(personaId) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("编辑资料")
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(24.dp))

                                // 6. 描述
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "关于智能体",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = persona.description ?: "暂无描述",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5
                                    )
                                }
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }

                        // [Fix] 悬浮头像 (移出 Surface，放在 Box 上层)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter) // 居中顶部对齐
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholder = rememberVectorPainter(Icons.Default.Person),
                                error = rememberVectorPainter(Icons.Default.Person)
                            )
                        }
                    }
                }
            }
        }
    }
}