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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    personaId: Long, // 从路由传过来的 ID
    onBackClick: () -> Unit,
    onChatClick: (Long) -> Unit, // 跳转聊天
    onCreatePostClick: (Long) -> Unit, // 跳转发帖
    onEditClick: (Long) -> Unit, // 跳转编辑
    viewModel: PersonaDetailViewModel = hiltViewModel()
) {
    // 1. 初始化加载：一进页面就根据 ID 去拉取数据
    LaunchedEffect(personaId) {
        viewModel.loadPersona(personaId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val persona = uiState.persona
    val context = LocalContext.current

    // 2. 头像处理逻辑：统一处理 SVG 和空头像
    val avatarUrl = remember(persona?.avatarUrl, persona?.name) {
        val url = persona?.avatarUrl
        val name = persona?.name ?: "unknown"
        if (url.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=$name"
        } else {
            url.replace("/svg", "/png")
        }
    }

    Scaffold(
        topBar = {
            // 顶部栏是透明的，悬浮在图片上
            TopAppBar(
                title = { },
                navigationIcon = {
                    // 返回按钮加了个半透明黑底，防止背景图太亮看不清图标
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // 只有是作者本人 (isOwner) 且加载完了才显示 "编辑" 按钮
                    if (uiState.isOwner && !uiState.isLoading) {
                        IconButton(
                            onClick = { onEditClick(personaId) },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(Color.Black.copy(0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color.White
                            )
                        }
                    }
                },
                // 设置 TopBar 背景全透明
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { _ ->
        // 状态处理：加载中、错误、成功
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error!!, color = Color.Red)
            }
        } else if (persona != null) {
            // ============================================
            // 核心布局：使用 Box 叠加图层实现“头像悬浮”效果
            // ============================================
            Box(Modifier.fillMaxSize()) {

                // 图层 1 (最底层)：大背景图
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop, // 裁剪铺满
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp) // 占据顶部 300dp
                )

                // 图层 2：黑色渐变遮罩 (让文字看不清背景时有个过渡，虽然这里文字在卡片上，主要为了美观)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.7f))
                            )
                        )
                )

                // 图层 3：白色内容卡片 (可滚动区域)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 透明占位符，把卡片顶下去，露出上面的背景图
                    Spacer(Modifier.height(220.dp))

                    // 这里再套一个 Box 是为了方便定位圆形头像
                    Box(Modifier.fillMaxWidth()) {
                        // 白色圆角卡片
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 50.dp), // 给悬浮头像留出一半的空间
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 60.dp, bottom = 24.dp), // 顶部 padding 大一点，避开头像
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 名字
                                Text(
                                    text = persona.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                // 创建者信息
                                Text(
                                    text = "Created by ${uiState.creatorName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(16.dp))

                                // 标签流 (FlowRow)
                                if (uiState.tags.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        uiState.tags.forEach { tag ->
                                            SuggestionChip(
                                                onClick = { },
                                                label = { Text(tag) },
                                                enabled = false // 纯展示，不可点
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }

                                // --- 操作按钮区域 ---
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // 1. 开始聊天按钮
                                    Button(
                                        onClick = { onChatClick(personaId) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("开始聊天")
                                    }

                                    // 2. 关注按钮 (Toggle)
                                    FilledTonalButton(
                                        onClick = { viewModel.toggleFollow(personaId) },
                                        modifier = Modifier.weight(1f),
                                        // 如果已关注，颜色变灰一点
                                        colors = if (uiState.isFollowed) {
                                            ButtonDefaults.filledTonalButtonColors(
                                                containerColor = Color.Gray.copy(0.2f)
                                            )
                                        } else {
                                            ButtonDefaults.filledTonalButtonColors()
                                        }
                                    ) {
                                        // 图标随状态变化 (打钩 vs 加号)
                                        Icon(
                                            imageVector = if (uiState.isFollowed) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (uiState.isFollowed) "已关注" else "关注")
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // 3. 扮演发帖按钮
                                OutlinedButton(
                                    onClick = { onCreatePostClick(personaId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("发布动态")
                                }

                                Spacer(Modifier.height(24.dp))
                                HorizontalDivider(thickness = 0.5.dp) // 分割线
                                Spacer(Modifier.height(24.dp))

                                // 详细描述区域
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "关于智能体",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = persona.description ?: "暂无描述",
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5
                                    )
                                }
                                Spacer(Modifier.height(80.dp))
                            }
                        }

                        // 图层 4：悬浮的圆形头像
                        // 它的位置由 Alignment.TopCenter 决定，刚好压在背景图和白色卡片的交界线上
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape) // 白色描边
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
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