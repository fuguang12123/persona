package com.example.persona.ui.create

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

// @Composable: 标记这是一个 UI 构建函数
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    // 依赖注入获取 ViewModel (业务逻辑控制器)
    viewModel: CreatePostViewModel = hiltViewModel(),
    // 回调函数：点击返回按钮时触发
    onBackClick: () -> Unit,
    // 回调函数：发布成功后触发
    onPostSuccess: () -> Unit
) {
    // 1. 订阅状态：实时监听 ViewModel 中的 uiState，一旦变化，界面自动刷新
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current // 获取安卓上下文，用于弹 Toast

    // 2. 本地 UI 状态：这两个变量只影响界面显示，不需要存到 ViewModel 里
    // previewImageUrl: 记录当前正在全屏预览哪张图，null 表示没有预览
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    // isSearchVisible: 控制顶部搜索框是否展开
    var isSearchVisible by remember { mutableStateOf(false) }

    // 3. 注册图片选择器 (系统相册)
    // contract = PickMultipleVisualMedia(9): 允许用户一次选多张图，最多 9 张
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris: List<Uri> ->
        // 用户选完图后的回调：如果有选图，就传给 ViewModel 处理
        if (uris.isNotEmpty()) {
            viewModel.uploadLocalImages(uris)
        }
    }

    // 4. 监听业务结果 (副作用)
    // 监听发布是否成功
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            Toast.makeText(context, "发布成功", Toast.LENGTH_SHORT).show()
            onPostSuccess() // 触发外部跳转
        }
    }
    // 监听是否有错误发生
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError() // 弹完提示后，清除错误状态，避免旋转屏幕重复弹
        }
    }

    // 5. 全屏图片预览弹窗 (Dialog)
    // 只有当 previewImageUrl 不为空时，才渲染这个弹窗
    if (previewImageUrl != null) {
        Dialog(
            onDismissRequest = { previewImageUrl = null }, // 点击外部关闭
            properties = DialogProperties(usePlatformDefaultWidth = false) // 全屏模式
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black) // 黑色背景
                    .clickable { previewImageUrl = null }, // 点击图片也关闭
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = previewImageUrl,
                    contentDescription = "Full Image",
                    contentScale = ContentScale.Fit, // 保持比例完整显示
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 6. 页面主体结构 (Scaffold)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发布动态") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // 右上角的 "发布" 按钮
                    Button(
                        onClick = { viewModel.createPost() },
                        // 按钮可用性检查：
                        // 1. 不在加载中
                        // 2. 有内容 OR 有图片
                        // 3. 必须选了一个发布身份
                        enabled = !uiState.isLoading &&
                                (uiState.content.isNotBlank() || uiState.imageUrls.isNotEmpty()) &&
                                uiState.selectedPersonaId != null,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (uiState.isLoading) {
                            // 加载中显示转圈
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            // 正常显示文字和图标
                            Text("发布")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )
        }
    ) { padding ->
        // 主内容区域，支持垂直滚动
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // 避开顶部栏
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ============================================
            // 区域 1：身份选择 (横向滚动列表)
            // ============================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "发布身份",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )

                // 搜索按钮：点击切换搜索框的显示/隐藏
                IconButton(
                    onClick = {
                        isSearchVisible = !isSearchVisible
                        // 如果关闭搜索，清空搜索词，恢复显示全部列表
                        if (!isSearchVisible) viewModel.onSearchQueryChange("")
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search Persona",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 搜索框的展开动画
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandHorizontally() + fadeIn(), // 展开 + 淡入
                exit = shrinkHorizontally() + fadeOut()  // 收缩 + 淡出
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) }, // 实时过滤
                    placeholder = { Text("搜索智能体名称...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(56.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 智能体列表渲染
            if (uiState.personas.isNotEmpty()) {
                // LazyRow: 横向滚动的懒加载列表 (类似 RecyclerView)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp), // 每一项间隔 16dp
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                ) {
                    items(uiState.personas) { persona ->
                        // 判断当前项是否被选中
                        val isSelected = persona.id == uiState.selectedPersonaId

                        // 头像 URL 处理逻辑 (解决 SVG 显示问题，或生成随机头像)
                        val displayAvatarUrl = remember(persona.avatar, persona.name) {
                            if (!persona.avatar.isNullOrBlank()) {
                                if (persona.avatar.contains(".svg")) {
                                    persona.avatar.replace("/svg", "/png")
                                } else {
                                    persona.avatar
                                }
                            } else {
                                // DiceBear API: 根据名字生成唯一头像
                                "https://api.dicebear.com/7.x/avataaars/png?seed=${persona.name}"
                            }
                        }

                        // 单个智能体 Item
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { viewModel.selectPersona(persona.id) } // 点击选中
                        ) {
                            // 头像容器
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    // 选中时给头像加一个 Primary 颜色的边框
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .background(Color.LightGray.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                // 加载网络图片
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(displayAvatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = persona.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    placeholder = rememberVectorPainter(Icons.Default.Person),
                                    error = rememberVectorPainter(Icons.Default.Person)
                                )

                                // 选中时的打钩遮罩 (增强视觉反馈)
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)), // 半透明黑
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 名字
                            Text(
                                text = persona.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, // 名字太长只显示一行
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black
                            )
                        }
                    }
                }
            } else {
                // 如果搜索结果为空
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("没有找到匹配的智能体", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============================================
            // 区域 2：文本输入框 + AI 润色按钮
            // ============================================
            Box {
                OutlinedTextField(
                    value = uiState.content,
                    onValueChange = viewModel::onContentChange, // 绑定输入事件
                    placeholder = { Text("此刻的想法... (写完点✨润色试试)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp), // 最小高度
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, // 去掉边框，样式更干净
                        unfocusedBorderColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                // 悬浮在输入框右下角的 "润色" 按钮
                IconButton(
                    onClick = { viewModel.magicEdit() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    // 只有没在润色且有内容时才能点
                    enabled = !uiState.isMagicEditing && uiState.content.isNotBlank()
                ) {
                    if (uiState.isMagicEditing) {
                        // 正在润色时显示加载动画
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        // 魔法棒图标
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = "Magic Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============================================
            // 区域 3：图片预览列表
            // ============================================
            if (uiState.imageUrls.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                ) {
                    itemsIndexed(uiState.imageUrls) { index, url ->
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray)
                                .clickable { previewImageUrl = url } // 点击查看大图
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // 右上角的删除按钮
                            IconButton(
                                onClick = { viewModel.removeImage(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ============================================
            // 区域 4：底部操作栏 (AI配图 / 相册)
            // ============================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 按钮 A: 调用 AI 生成图片
                SuggestionChip(
                    onClick = { viewModel.generateAiImage() },
                    label = { Text("AI 配图") },
                    icon = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                    enabled = !uiState.isLoading && uiState.imageUrls.size < 9, // 最多9张
                    modifier = Modifier.height(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 按钮 B: 打开系统相册
                SuggestionChip(
                    onClick = {
                        // 启动相册选择器
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    label = { Text("相册 (${uiState.imageUrls.size}/9)") },
                    icon = { Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.secondary) },
                    enabled = !uiState.isLoading && uiState.imageUrls.size < 9,
                    modifier = Modifier.height(48.dp)
                )

                // 辅助提示：如果正在上传或生成，显示文字提示
                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("处理中...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(50.dp)) // 底部留白，防遮挡
        }
    }
}